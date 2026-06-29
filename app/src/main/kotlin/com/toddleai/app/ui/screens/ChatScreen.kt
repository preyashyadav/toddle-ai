package com.toddleai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toddleai.app.ToddleAISessionViewModel
import com.toddleai.app.llm.ChatRole
import com.toddleai.app.llm.LlmStatus
import com.toddleai.app.ui.theme.SoftIvory
import com.toddleai.app.ui.theme.WarmSand

@Composable
fun ChatScreen(
    sessionViewModel: ToddleAISessionViewModel,
    onBack: () -> Unit,
) {
    val messages by sessionViewModel.chatMessages.collectAsState()
    val status by sessionViewModel.llmStatus.collectAsState()
    val generating by sessionViewModel.isGenerating.collectAsState()
    val pendingQuestion by sessionViewModel.assistantQuestion.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { sessionViewModel.primeChat() }

    // If the user arrived via "Ask ToddleAI" with a preset question, send it once the model is ready.
    LaunchedEffect(status, pendingQuestion) {
        val q = pendingQuestion
        if (status == LlmStatus.READY && !q.isNullOrBlank()) {
            sessionViewModel.clearAssistantQuestion()
            sessionViewModel.sendChatMessage(q)
        }
    }

    LaunchedEffect(messages, generating) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SoftIvory, WarmSand)))
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "ToddleAI Assistant",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        StatusLine(status)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(messages) { index, message ->
                val isStreamingThis = generating && index == messages.lastIndex &&
                    message.role == ChatRole.ASSISTANT
                MessageBubble(
                    text = if (isStreamingThis && message.text.isBlank()) "…" else message.text,
                    fromUser = message.role == ChatRole.USER,
                )
            }
        }

        if (status == LlmStatus.READY && !generating && messages.size <= 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Is this typical?", "How do I record a better clip?").forEach { prompt ->
                    SuggestionChip(
                        onClick = { sessionViewModel.sendChatMessage(prompt) },
                        label = { Text(prompt) },
                    )
                }
            }
        }

        InputBar(
            value = input,
            onValueChange = { input = it },
            enabled = status == LlmStatus.READY && !generating,
            onSend = {
                if (input.isNotBlank()) {
                    sessionViewModel.sendChatMessage(input)
                    input = ""
                }
            },
        )
    }
}

@Composable
private fun StatusLine(status: LlmStatus) {
    val text = when (status) {
        LlmStatus.LOADING -> "Loading the on-device assistant…"
        LlmStatus.MISSING -> "On-device assistant model isn't installed on this phone."
        LlmStatus.ERROR -> "The assistant couldn't start on this device."
        else -> null
    } ?: return

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (status == LlmStatus.LOADING) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    fromUser: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (fromUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (fromUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask about the results…") },
            enabled = enabled,
            maxLines = 4,
        )
        Box(contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
