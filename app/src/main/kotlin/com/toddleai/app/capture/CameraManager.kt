package com.toddleai.app.capture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import android.util.Size
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private var boundPreviewView: PreviewView? = null

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (ImageProxy) -> Unit,
    ) {
        if (boundPreviewView === previewView && cameraProvider != null) {
            return
        }

        if (!hasCameraPermission()) {
            requestCameraPermissionIfPossible()
            showMessage("Camera permission is needed before recording can start.")
            return
        }

        stopCamera()

        analysisExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    boundPreviewView = previewView

                    val preview = Preview.Builder()
                        .applyThirtyFpsTarget()
                        .build()
                        .also { useCase ->
                            useCase.surfaceProvider = previewView.surfaceProvider
                        }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(Size(640, 480))
                        .applyThirtyFpsTarget()
                        .build()
                        .also { useCase ->
                            val executor = analysisExecutor ?: return@also
                            useCase.setAnalyzer(executor) { imageProxy ->
                                try {
                                    onFrame(imageProxy)
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Analysis callback failed", t)
                                    imageProxy.close()
                                }
                            }
                        }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "Unable to start camera", t)
                    showMessage("We couldn't start the camera. Please try again.")
                    stopCamera()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to unbind camera cleanly", t)
        } finally {
            cameraProvider = null
            boundPreviewView = null
            analysisExecutor?.shutdownNow()
            analysisExecutor = null
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermissionIfPossible() {
        val activity = context as? Activity ?: return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE,
        )
    }

    private fun showMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun Preview.Builder.applyThirtyFpsTarget(): Preview.Builder {
        Camera2Interop.Extender(this)
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(TARGET_FPS, TARGET_FPS),
            )
        return this
    }

    private fun ImageAnalysis.Builder.applyThirtyFpsTarget(): ImageAnalysis.Builder {
        Camera2Interop.Extender(this)
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(TARGET_FPS, TARGET_FPS),
            )
        return this
    }

    private companion object {
        const val TAG = "ToddleAICameraManager"
        const val CAMERA_PERMISSION_REQUEST_CODE = 7001
        const val TARGET_FPS = 30
    }
}
