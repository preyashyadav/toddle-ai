package com.example.npuchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("NPU Chat")
                    Text(
                        subtitle(vm),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            })
        },
        bottomBar = {
            if (vm.status == ModelStatus.Ready) {
                InputBar(
                    enabled = !vm.isGenerating,
                    generating = vm.isGenerating,
                    onSend = vm::send,
                    onStop = vm::stop,
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = vm.status) {
                is ModelStatus.Loading -> Centered { CircularProgressIndicator(); Text("Loading model on the NPU…") }
                is ModelStatus.MissingFiles -> SetupHelp(s.paths, vm::checkAndLoad)
                is ModelStatus.LoadError -> ErrorHelp(s.message, vm::checkAndLoad)
                is ModelStatus.Ready -> MessageList(vm)
            }
        }
    }
}

private fun subtitle(vm: ChatViewModel): String = when (vm.status) {
    is ModelStatus.Ready ->
        if (vm.isGenerating) "Generating on Hexagon NPU…"
        else vm.lastTokPerSec?.let { "Ready · %.1f tok/s".format(it) } ?: "Ready · Hexagon NPU"
    is ModelStatus.Loading -> "Loading…"
    is ModelStatus.MissingFiles -> "Model not found"
    is ModelStatus.LoadError -> "Load failed"
}

@Composable
private fun MessageList(vm: ChatViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size, vm.messages.lastOrNull()?.content) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(vm.messages, key = { it.id }) { msg -> Bubble(msg) }
    }
}

@Composable
private fun Bubble(msg: ChatMessage) {
    val isUser = msg.role == Role.USER
    val color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = color,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = msg.content.ifEmpty { "…" },
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBar(
    enabled: Boolean,
    generating: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                enabled = enabled,
                maxLines = 4,
            )
            FilledIconButton(
                onClick = {
                    if (generating) {
                        onStop()
                    } else if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                },
            ) {
                if (generating) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun SetupHelp(missing: List<String>, onRetry: () -> Unit) {
    Centered {
        Text("Model files not found", style = MaterialTheme.typography.titleMedium)
        Text(
            "Push the model to the device, then tap Retry:",
            textAlign = TextAlign.Center,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                """
                adb shell mkdir -p ${ModelConfig.BASE_DIR}
                adb push hybrid_llama_qnn.pte ${ModelConfig.BASE_DIR}/
                adb push tokenizer.json ${ModelConfig.BASE_DIR}/
                """.trimIndent(),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                fontSize = 12.sp,
            )
        }
        Text("Missing: ${missing.joinToString()}", fontSize = 12.sp)
        RetryButton(onRetry)
    }
}

@Composable
private fun ErrorHelp(message: String, onRetry: () -> Unit) {
    Centered {
        Text("Failed to load model", style = MaterialTheme.typography.titleMedium)
        Text(message, textAlign = TextAlign.Center)
        Text(
            "Check that executorch.aar was built with QNN and the Hexagon-v79 .so are in jniLibs.",
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
        )
        RetryButton(onRetry)
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    FilledIconButton(onClick = onRetry) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Retry")
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) { content() }
    }
}
