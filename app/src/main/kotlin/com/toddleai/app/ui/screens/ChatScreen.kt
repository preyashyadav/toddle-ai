package com.toddleai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toddleai.app.ToddleAISessionViewModel
import com.toddleai.app.ui.theme.SoftIvory
import com.toddleai.app.ui.theme.WarmSand

@Composable
fun ChatScreen(
    sessionViewModel: ToddleAISessionViewModel,
    onBack: () -> Unit,
) {
    val result by sessionViewModel.analysisResult.collectAsState()
    val selectedQuestion by sessionViewModel.assistantQuestion.collectAsState()
    val observations = result?.observations.orEmpty()
    val answer = buildAssistantAnswer(selectedQuestion, observations)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SoftIvory, WarmSand)))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "ToddleAI Assistant",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = selectedQuestion ?: "Choose a question below to review this recording.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = "This assistant is bounded to explaining the recording, suggesting recapture, and helping prepare a summary.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        listOf(
            "Explain this recording",
            "Should I record another video?",
            "Prepare a doctor summary",
        ).forEach { question ->
            Button(
                onClick = { sessionViewModel.setAssistantQuestion(question) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(question)
            }
        }
    }
}

private fun buildAssistantAnswer(
    question: String?,
    observations: List<com.toddleai.app.data.models.Observation>,
): String {
    val summary = observations.joinToString(" ") {
        if (it.context.isNotBlank()) "${it.measurement}. ${it.context}" else "${it.measurement}."
    }.ifBlank {
        "No analysis result is available yet. Record a walking clip first."
    }

    return when (question) {
        "Should I record another video?" ->
            "Record another video if the result mentioned low data, missing feet, or camera movement. A steady side view with at least five usable steps will usually produce a stronger summary. $summary"
        "Prepare a doctor summary" ->
            "ToddleAI observed temporal gait measures from a short home video. $summary This was processed entirely on-device and is intended as an observation from this recording rather than a diagnosis."
        else ->
            "Here is the clearest summary from this recording: $summary"
    }
}
