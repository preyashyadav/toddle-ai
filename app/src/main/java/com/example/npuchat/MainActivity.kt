package com.example.npuchat

import android.os.Bundle
import android.system.Os
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureHexagonDspPath()
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    val vm: ChatViewModel = viewModel()
                    ChatScreen(vm)
                }
            }
        }
    }

    /**
     * QNN's Hexagon (cDSP) loads the skel library (`libQnnHtpV79Skel.so`) over FastRPC and finds it
     * via ADSP_LIBRARY_PATH. We bundle the skel in jniLibs, so point that path at our extracted
     * native-lib dir (plus the standard system DSP dirs). Must run before the model is loaded,
     * otherwise QNN device creation fails with "Failed to load skel, error: 4000".
     *
     * NOTE: On stock Samsung S25 Ultra (SM8750) this still fails at FastRPC transport creation
     * ("loadRemoteSymbols failed with err 4000") because the untrusted_app SELinux domain is denied
     * access to the cDSP/adsprpc device nodes. Staging the skel to an app-private real-filesystem
     * path was tested and produced the identical error, confirming it is a FastRPC/SELinux gate, not
     * a path issue. The installed app cannot reach the NPU without root; use run_qwen_npu.sh (adb
     * shell domain) instead.
     */
    private fun configureHexagonDspPath() {
        val nativeDir = applicationInfo.nativeLibraryDir
        val path = "$nativeDir;/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp"
        try {
            Os.setenv("ADSP_LIBRARY_PATH", path, true)
            Log.i("MainActivity", "ADSP_LIBRARY_PATH=$path")
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to set ADSP_LIBRARY_PATH", e)
        }
    }
}
