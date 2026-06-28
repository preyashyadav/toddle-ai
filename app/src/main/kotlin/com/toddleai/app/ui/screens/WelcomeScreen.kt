package com.toddleai.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.toddleai.app.ui.theme.SoftIvory
import com.toddleai.app.ui.theme.Terracotta
import com.toddleai.app.ui.theme.WarmSand

@Composable
fun WelcomeScreen(
    childName: String,
    childAgeMonthsInput: String,
    onboardingVisible: Boolean,
    onChildNameChange: (String) -> Unit,
    onChildAgeMonthsChange: (String) -> Unit,
    onRecordWalkingVideo: () -> Unit,
    onImportWalkingVideo: (Uri) -> Unit,
    onOpenSettings: () -> Unit,
    onDismissOnboarding: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val importVideoLauncher = rememberLauncherForActivityResult(
        contract = OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Temporary access is still enough for the current session on many providers.
            } catch (_: IllegalArgumentException) {
                // Some document providers do not allow persistable access.
            }
            onImportWalkingVideo(uri)
        }
    }

    Scaffold(
        containerColor = SoftIvory,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(SoftIvory, WarmSand)))
                .statusBarsPadding()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DirectionsWalk,
                        contentDescription = null,
                        tint = Terracotta,
                    )
                    Text(
                        text = "ToddleAI",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = WarmSand),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Private movement observations from home video",
                        style = MaterialTheme.typography.titleLarge,
                        color = Terracotta,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Record a short side-view walking clip. ToddleAI reviews step timing, cadence, and symmetry fully on-device.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Child Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = childName,
                        onValueChange = onChildNameChange,
                        label = { Text("Child's name (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = childAgeMonthsInput,
                        onValueChange = onChildAgeMonthsChange,
                        label = { Text("Child's age in years") },
                        supportingText = {
                            Text("Used to compare against typical walking for this age.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = childAgeMonthsInput.isBlank(),
                    )
                }
            }

            Button(
                onClick = onRecordWalkingVideo,
                enabled = childAgeMonthsInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Record Walking Video")
            }

            OutlinedButton(
                onClick = { importVideoLauncher.launch(arrayOf("video/*")) },
                enabled = childAgeMonthsInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import Test Video From Device")
            }

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Review Backend & Privacy Settings")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("No account needed", style = MaterialTheme.typography.bodyLarge)
                    Text("No internet required", style = MaterialTheme.typography.bodyLarge)
                    Text("All processing stays on this device", style = MaterialTheme.typography.bodyLarge)
                    Text("You can also import a saved side-view walking clip for testing", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (onboardingVisible) {
        AlertDialog(
            onDismissRequest = onDismissOnboarding,
            confirmButton = {
                Button(onClick = onDismissOnboarding) {
                    Text("Got it")
                }
            },
            title = { Text("Before you start") },
            text = {
                Text(
                    "ToddleAI will guide you through recording a short walking video. You'll need a well-lit room, about 3 meters of clear walking space, and your phone held steady at about knee height.",
                )
            },
        )
    }
}
