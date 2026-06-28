from __future__ import annotations

from typing import Any

import torch


def _normalize_output(value: Any):
    if isinstance(value, torch.Tensor):
        return value
    if isinstance(value, (list, tuple)):
        return tuple(_normalize_output(item) for item in value)
    if isinstance(value, dict):
        return tuple(_normalize_output(value[key]) for key in sorted(value))
    raise TypeError(f"Unsupported output type for export wrapper: {type(value)!r}")


class PoseDetectorExportWrapper(torch.nn.Module):
    def __init__(self, detector: torch.nn.Module):
        super().__init__()
        self.detector = detector

    def forward(self, *inputs):
        return _normalize_output(self.detector(*inputs))


class PoseLandmarkExportWrapper(torch.nn.Module):
    def __init__(self, landmark: torch.nn.Module):
        super().__init__()
        self.landmark = landmark

    def forward(self, *inputs):
        return _normalize_output(self.landmark(*inputs))
