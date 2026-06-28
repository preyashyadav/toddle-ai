"""
MediaPipe-Pose (BlazePose) pre/post-processing for the Qualcomm AI Hub QNN .pte models.

Two-stage pipeline:
  1. pose_detector_qnn.pte   input (1,3,128,128) f32 RGB [0,1]
                             outputs box_coords_1 (1,512,12), box_coords_2 (1,384,12),
                                     box_scores_1 (1,512,1),  box_scores_2 (1,384,1)
  2. pose_landmark_qnn.pte   input (1,3,256,256) f32 RGB [0,1]
                             outputs scores (1,), landmarks (1,25,4)

These shapes were verified on-device (Samsung S25 Ultra, SM8750) with qnn_executor_runner.
The .pte already runs on the Hexagon NPU; this module only does the CPU-side glue.

Reference: github.com/quic/ai-hub-models  qai_hub_models/models/mediapipe_pose/
           github.com/zmurez/MediaPipePyTorch (BlazePose anchor/decode math)
"""
import math
import numpy as np
from PIL import Image, ImageDraw

# --- Verified model constants -------------------------------------------------
DET_SIZE = 128          # detector input H=W
LM_SIZE = 256           # landmark input H=W
NUM_ANCHORS = 896
NUM_COORDS = 12         # [cx,cy,w,h, kp0x,kp0y, kp1x,kp1y, kp2x,kp2y, kp3x,kp3y]
SCORE_CLIP = 100.0
MIN_DET_SCORE = 0.75    # detector box score threshold (post-sigmoid)
NMS_IOU = 0.3
MIN_LM_SCORE = 0.5      # landmark presence flag threshold
# Pose ROI is built from detector keypoints 2 & 3 (hip-center / scale point).
ROI_KP_CENTER, ROI_KP_SCALE = 2, 3
ROI_DSCALE = 1.5
ROI_ROT_OFFSET = math.pi / 2.0
NUM_LANDMARKS = 25

# 25-landmark skeleton (qai POSE_LANDMARK_CONNECTIONS).
CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 7), (0, 4), (4, 5), (5, 6), (6, 8), (9, 10),
    (11, 13), (13, 15), (15, 17), (17, 19), (19, 15), (15, 21),
    (12, 14), (14, 16), (16, 18), (18, 20), (20, 16), (16, 22),
    (11, 12), (12, 24), (24, 23), (23, 11),
]


# --- SSD anchors (MediaPipe SsdAnchorsCalculator, pose_detection 128x128) ------
def _calc_scale(mn, mx, i, n):
    return (mn + mx) * 0.5 if n == 1 else mn + (mx - mn) * i / (n - 1.0)


def generate_anchors():
    """Returns (896, 4) anchors as [x_center, y_center, w, h], normalized to [0,1]."""
    strides = [8, 16, 16, 16]
    min_scale, max_scale = 0.1484375, 0.75
    offset = 0.5
    n = len(strides)
    anchors = []
    layer = 0
    while layer < n:
        a_ratios, scales = [], []
        last = layer
        while last < n and strides[last] == strides[layer]:
            s = _calc_scale(min_scale, max_scale, last, n)
            a_ratios.append(1.0); scales.append(s)               # aspect_ratios = [1.0]
            s_next = 1.0 if last == n - 1 else _calc_scale(min_scale, max_scale, last + 1, n)
            a_ratios.append(1.0); scales.append(math.sqrt(s * s_next))  # interpolated_scale
            last += 1
        stride = strides[layer]
        fm = math.ceil(DET_SIZE / stride)                        # square feature map
        for y in range(fm):
            for x in range(fm):
                for _ in range(len(a_ratios)):                   # fixed_anchor_size -> w=h=1
                    anchors.append([(x + offset) / fm, (y + offset) / fm, 1.0, 1.0])
        layer = last
    arr = np.asarray(anchors, dtype=np.float32)
    assert arr.shape == (NUM_ANCHORS, 4), arr.shape
    return arr


# --- Image preprocessing (letterbox) ------------------------------------------
def letterbox(img: Image.Image, size: int):
    """Aspect-preserving resize into a centered square, zero-padded.
    Returns (square_rgb_uint8 HxWx3, scale, pad_x, pad_y) for inverse mapping."""
    w, h = img.size
    scale = size / max(w, h)
    nw, nh = round(w * scale), round(h * scale)
    resized = img.convert("RGB").resize((nw, nh), Image.BILINEAR)
    canvas = Image.new("RGB", (size, size), (0, 0, 0))
    px, py = (size - nw) // 2, (size - nh) // 2
    canvas.paste(resized, (px, py))
    return np.asarray(canvas, dtype=np.uint8), scale, px, py


def to_input_raw(square_uint8: np.ndarray, layout: str) -> bytes:
    """square_uint8 HxWx3 RGB -> float32 [0,1] bytes in NHWC or NCHW order."""
    arr = square_uint8.astype(np.float32) / 255.0          # HWC
    if layout == "nchw":
        arr = np.transpose(arr, (2, 0, 1))                 # CHW
    return np.ascontiguousarray(arr).tobytes()


# --- Detector decode ----------------------------------------------------------
def _sigmoid(x):
    x = np.clip(x.astype(np.float64), -SCORE_CLIP, SCORE_CLIP)
    return 1.0 / (1.0 + np.exp(-x))


def decode_detections(coords, scores, anchors):
    """coords (896,12) raw, scores (896,) raw logits, anchors (896,4).
    Returns list of dicts {score, box(xyxy), kps(4,2)} in [0,1] letterbox space."""
    s = _sigmoid(scores)
    keep = np.where(s >= MIN_DET_SCORE)[0]
    dets = []
    for i in keep:
        ax, ay, aw, ah = anchors[i]
        c = coords[i]
        cx = c[0] / DET_SIZE * aw + ax
        cy = c[1] / DET_SIZE * ah + ay
        w = c[2] / DET_SIZE * aw
        h = c[3] / DET_SIZE * ah
        box = np.array([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], np.float32)
        kps = np.empty((4, 2), np.float32)
        for k in range(4):
            kps[k, 0] = c[4 + 2 * k] / DET_SIZE * aw + ax
            kps[k, 1] = c[5 + 2 * k] / DET_SIZE * ah + ay
        dets.append({"score": float(s[i]), "box": box, "kps": kps})
    return dets


def _iou(a, b):
    ix1, iy1 = max(a[0], b[0]), max(a[1], b[1])
    ix2, iy2 = min(a[2], b[2]), min(a[3], b[3])
    iw, ih = max(0.0, ix2 - ix1), max(0.0, iy2 - iy1)
    inter = iw * ih
    ua = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / ua if ua > 0 else 0.0


def nms(dets):
    dets = sorted(dets, key=lambda d: d["score"], reverse=True)
    out = []
    while dets:
        best = dets.pop(0)
        out.append(best)
        dets = [d for d in dets if _iou(best["box"], d["box"]) < NMS_IOU]
    return out


# --- ROI (rotated crop) from a detection --------------------------------------
def letterbox_to_orig(pt, scale, px, py):
    return np.array([(pt[0] * DET_SIZE - px) / scale, (pt[1] * DET_SIZE - py) / scale], np.float32)


def roi_from_detection(det, scale, px, py):
    """Build the rotated square ROI (in original-image pixels) for the landmark stage.
    Returns (center, size, theta, corners(4,2): TL, BL, TR, BR)."""
    kc = letterbox_to_orig(det["kps"][ROI_KP_CENTER], scale, px, py)
    ks = letterbox_to_orig(det["kps"][ROI_KP_SCALE], scale, px, py)
    theta = math.atan2(kc[1] - ks[1], kc[0] - ks[0]) - ROI_ROT_OFFSET
    side = math.hypot(kc[0] - ks[0], kc[1] - ks[1]) * 2.0 * ROI_DSCALE
    half = side / 2.0
    ct, st = math.cos(theta), math.sin(theta)
    # unit square corners (TL, BL, TR, BR) before rotation
    units = [(-half, -half), (-half, half), (half, -half), (half, half)]
    corners = np.array([[kc[0] + ux * ct - uy * st, kc[1] + ux * st + uy * ct]
                        for ux, uy in units], np.float32)
    return kc, side, theta, corners


def affine_256_to_orig(corners):
    """Solve PIL AFFINE coeffs (a,b,c,d,e,f) mapping 256-ROI space -> original pixels,
    using corner order TL->(0,0), BL->(0,256), TR->(256,0)."""
    dst = corners[:3]                                   # original-image points
    src = np.array([[0, 0], [0, LM_SIZE], [LM_SIZE, 0]], np.float32)  # 256-space points
    A = np.array([[src[0, 0], src[0, 1], 1],
                  [src[1, 0], src[1, 1], 1],
                  [src[2, 0], src[2, 1], 1]], np.float32)
    ax = np.linalg.solve(A, dst[:, 0])                  # a,b,c
    ay = np.linalg.solve(A, dst[:, 1])                  # d,e,f
    return (ax[0], ax[1], ax[2], ay[0], ay[1], ay[2])


def crop_roi_256(img: Image.Image, coeffs):
    return img.convert("RGB").transform((LM_SIZE, LM_SIZE), Image.AFFINE, coeffs, Image.BILINEAR)


def landmarks_to_orig(landmarks, coeffs):
    """landmarks (25,4) with x,y in [0,1] of ROI -> (25,2) original pixels via affine."""
    a, b, c, d, e, f = coeffs
    out = np.empty((landmarks.shape[0], 2), np.float32)
    for i, lm in enumerate(landmarks):
        x256, y256 = lm[0] * LM_SIZE, lm[1] * LM_SIZE
        out[i, 0] = a * x256 + b * y256 + c
        out[i, 1] = d * x256 + e * y256 + f
    return out


# --- Drawing ------------------------------------------------------------------
def draw_pose(img: Image.Image, pts, roi_corners=None):
    out = img.convert("RGB").copy()
    dr = ImageDraw.Draw(out)
    if roi_corners is not None:
        tl, bl, tr, br = [tuple(p) for p in roi_corners]
        dr.line([tl, tr, br, bl, tl], fill=(255, 200, 0), width=2)
    for a, b in CONNECTIONS:
        if a < len(pts) and b < len(pts):
            dr.line([tuple(pts[a]), tuple(pts[b])], fill=(0, 230, 0), width=3)
    for x, y in pts:
        dr.ellipse([x - 4, y - 4, x + 4, y + 4], fill=(255, 0, 0))
    return out
