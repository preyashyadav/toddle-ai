package com.toddleai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.toddleai.app.navigation.NavGraph
import com.toddleai.app.ui.theme.ToddleAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Start from a clean navigation state on each launch so stale capture/import flows
        // do not reopen on top of the welcome screen after reinstalls or task restores.
        super.onCreate(null)
        enableEdgeToEdge()
        setContent {
            ToddleAITheme {
                Surface {
                    NavGraph()
                }
            }
        }
    }
}
