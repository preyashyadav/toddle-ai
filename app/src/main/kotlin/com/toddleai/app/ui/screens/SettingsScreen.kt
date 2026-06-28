package com.toddleai.app.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toddleai.app.settings.InferenceBackend
import com.toddleai.app.settings.InferenceSettings
import com.toddleai.app.ui.theme.SoftIvory
import com.toddleai.app.ui.theme.WarmSand
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.FileNotFoundException
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val inferenceSettings = remember(context) { InferenceSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    val backendPreference by inferenceSettings.backendPreference.collectAsState()
    val settingsState by produceState(
        initialValue = SettingsUiState.loading(),
        context,
    ) {
        value = loadSettingsUiState(context)
    }

    Scaffold(
        containerColor = SoftIvory,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(SoftIvory, WarmSand),
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HeaderCard(onBack = onBack)

            BackendSection(
                selectedBackend = backendPreference,
                backendStatus = settingsState.backendStatus,
                onSelectBackend = { backend ->
                    coroutineScope.launch {
                        inferenceSettings.setBackendPreference(backend)
                    }
                },
            )

            BenchmarkSection(
                benchmark = settingsState.benchmarkData,
            )

            ModelInfoSection(
                modelInfo = settingsState.modelInfo,
            )

            PrivacySection(
                hasInternetPermission = settingsState.hasInternetPermission,
            )

            AboutSection(
                appVersion = settingsState.appVersion,
            )
        }
    }
}

@Composable
private fun HeaderCard(onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = WarmSand),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Settings & Device Info",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Backend selection, model inventory, and privacy details",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BackendSection(
    selectedBackend: InferenceBackend,
    backendStatus: List<BackendStatus>,
    onSelectBackend: (InferenceBackend) -> Unit,
) {
    SectionCard(title = "Inference Backend") {
        backendStatus.forEach { status ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(
                    selected = selectedBackend == status.backend,
                    onClick = { onSelectBackend(status.backend) },
                    enabled = status.available,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        StatusChip(
                            text = if (status.available) "Available" else "Unavailable",
                            background = if (status.available) Color(0xFFD9EEDC) else Color(0xFFF2D4D4),
                            foreground = if (status.available) Color(0xFF275B2E) else Color(0xFF8A3131),
                        )
                    }
                    Text(
                        text = status.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            text = "QNN execution requires elevated device permissions. QNN benchmarks are measured via the development runner.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BenchmarkSection(
    benchmark: BenchmarkData?,
) {
    SectionCard(title = "NPU Benchmark Comparison") {
        if (benchmark == null) {
            Text(
                text = "Run the benchmark runner via adb to measure NPU performance.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Expected benchmark file: assets/qnn_benchmarks.json or app-local storage override.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        BenchmarkRow(header = true, model = "Model", cpu = "CPU (XNNPACK)", npu = "NPU (QNN)")
        BenchmarkRow(model = "Pose", cpu = benchmark.poseCpuMs ?: "—", npu = benchmark.poseQnnMs ?: "—")
        BenchmarkRow(model = "LLM (3B)", cpu = benchmark.llmCpuThroughput ?: "—", npu = benchmark.llmQnnThroughput ?: "—")

        benchmark.sourceLabel?.let { source ->
            Text(
                text = source,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelInfoSection(
    modelInfo: ModelInfoSummary,
) {
    SectionCard(title = "Model Information") {
        ModelLine(label = "Pose model", value = modelInfo.poseSummary)
        ModelLine(label = "LLM model", value = modelInfo.llmSummary)
        ModelLine(label = "Total on-device model storage", value = modelInfo.totalStorageLabel)
    }
}

@Composable
private fun PrivacySection(
    hasInternetPermission: Boolean,
) {
    SectionCard(title = "Privacy") {
        PrivacyLine(
            text = if (!hasInternetPermission) "No internet permission (verified in manifest)" else "Internet permission detected",
        )
        PrivacyLine(text = "All videos processed locally")
        PrivacyLine(text = "No accounts, no analytics, no telemetry")
        PrivacyLine(text = "Video data remains in app-local storage")
    }
}

@Composable
private fun AboutSection(
    appVersion: String,
) {
    SectionCard(title = "About") {
        Text("ToddleAI is a research prototype", style = MaterialTheme.typography.bodyMedium)
        Text("Not clinically validated", style = MaterialTheme.typography.bodyMedium)
        Text("Not intended for diagnosis or medical decision-making", style = MaterialTheme.typography.bodyMedium)
        Text("Version: $appVersion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Team: ExecuTorch Hackathon build team", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Hackathon: June 27-28, 2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Research citations: See project reference notes in the bundled documentation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    background: Color,
    foreground: Color,
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BenchmarkRow(
    model: String,
    cpu: String,
    npu: String,
    header: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableCell(text = model, cellWeight = 1.2f, header = header)
        TableCell(text = cpu, cellWeight = 1f, header = header)
        TableCell(text = npu, cellWeight = 1f, header = header)
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    cellWeight: Float,
    header: Boolean,
) {
    Text(
        text = text,
        modifier = Modifier.weight(cellWeight),
        style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        color = if (header) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModelLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private suspend fun loadSettingsUiState(context: Context): SettingsUiState {
    val benchmark = loadBenchmarkData(context)
    val modelFiles = discoverModelFiles(context)
    val hasQnnPoseModel = modelFiles.any { it.path.contains("pose", ignoreCase = true) && it.path.contains("qnn", ignoreCase = true) }
    val hasCpuPoseModel = modelFiles.any {
        it.path.contains("pose", ignoreCase = true) &&
            (it.path.contains("cpu", ignoreCase = true) || it.path.contains("xnn", ignoreCase = true))
    }
    return SettingsUiState(
        backendStatus = listOf(
            BackendStatus(
                backend = InferenceBackend.XNNPACK,
                label = "ExecuTorch XNNPACK (CPU)",
                available = hasCpuPoseModel,
                note = if (hasCpuPoseModel) {
                    "The in-app on-device reference path used by the current Android build."
                } else {
                    "No CPU/XNN pose asset is bundled yet."
                },
            ),
            BackendStatus(
                backend = InferenceBackend.QNN,
                label = "QNN (NPU)",
                available = hasQnnPoseModel,
                note = if (hasQnnPoseModel && benchmark != null) {
                    "QNN pose asset is bundled and benchmark data is available from the development runner."
                } else if (hasQnnPoseModel) {
                    "QNN pose asset is bundled. In-app support still depends on device/runtime compatibility."
                } else if (benchmark != null) {
                    "Benchmark data exists, but no bundled QNN pose asset was found."
                } else {
                    "No bundled QNN pose asset or benchmark file was found yet."
                },
            ),
        ),
        benchmarkData = benchmark,
        modelInfo = summarizeModels(modelFiles),
        hasInternetPermission = hasInternetPermission(context),
        appVersion = appVersion(context),
    )
}

private fun hasInternetPermission(context: Context): Boolean {
    return context.packageManager.checkPermission(
        android.Manifest.permission.INTERNET,
        context.packageName,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun appVersion(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
        "${packageInfo.versionName} ($versionCode)"
    } catch (_: Throwable) {
        "1.0"
    }
}

private fun discoverModelFiles(context: Context): List<ModelFileInfo> {
    return listAssetFiles(context, "")
        .filter { it.endsWith(".pte") || it.endsWith(".tflite") }
        .map { assetPath ->
            ModelFileInfo(
                path = assetPath,
                sizeBytes = assetSizeBytes(context, assetPath),
            )
        }
}

private fun listAssetFiles(context: Context, path: String): List<String> {
    val children = context.assets.list(path).orEmpty()
    if (children.isEmpty()) {
        return if (path.isBlank()) emptyList() else listOf(path)
    }

    return children.flatMap { child ->
        val nextPath = if (path.isBlank()) child else "$path/$child"
        listAssetFiles(context, nextPath)
    }
}

private fun assetSizeBytes(context: Context, assetPath: String): Long {
    return try {
        context.assets.openFd(assetPath).use { it.length }
    } catch (_: FileNotFoundException) {
        context.assets.open(assetPath).use { input -> input.available().toLong() }
    } catch (_: Throwable) {
        0L
    }
}

private fun summarizeModels(models: List<ModelFileInfo>): ModelInfoSummary {
    val pose = models.firstOrNull { it.path.contains("pose", ignoreCase = true) }
    val llm = models.firstOrNull {
        it.path.contains("llama", ignoreCase = true) ||
            it.path.contains("qwen", ignoreCase = true) ||
            it.path.contains("llm", ignoreCase = true)
    }
    val totalBytes = models.sumOf { it.sizeBytes }

    return ModelInfoSummary(
        poseSummary = pose?.let { "${it.path.substringAfterLast('/')} • ${formatSizeMb(it.sizeBytes)} • ${modelFormatLabel(it.path)}" }
            ?: "No pose model asset found yet",
        llmSummary = llm?.let { "${it.path.substringAfterLast('/')} • ${formatSizeMb(it.sizeBytes)} • ${modelFormatLabel(it.path)}" }
            ?: "No LLM model asset found yet",
        totalStorageLabel = formatSizeMb(totalBytes),
    )
}

private fun modelFormatLabel(assetPath: String): String {
    return when {
        assetPath.endsWith(".pte", ignoreCase = true) -> "ExecuTorch .pte"
        assetPath.endsWith(".tflite", ignoreCase = true) -> "TFLite fallback"
        else -> "Unknown format"
    }
}

private fun formatSizeMb(sizeBytes: Long): String {
    return String.format(Locale.US, "%.1f MB", sizeBytes / (1024f * 1024f))
}

private suspend fun loadBenchmarkData(context: Context): BenchmarkData? {
    val candidateReaders = listOf(
        { readTextIfExists(context, "qnn_benchmarks.json") },
        { readTextIfExists(context, "benchmarks/qnn_benchmarks.json") },
        { context.filesDir.resolve("qnn_benchmarks.json").takeIf { it.exists() }?.readText() },
    )

    val rawText = candidateReaders.firstNotNullOfOrNull { reader -> reader() } ?: return null
    return parseBenchmarkData(rawText)
}

private fun readTextIfExists(context: Context, assetPath: String): String? {
    return try {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    } catch (_: Throwable) {
        null
    }
}

private fun parseBenchmarkData(rawText: String): BenchmarkData? {
    return try {
        val json = JSONObject(rawText)
        val pose = json.optJSONObject("pose")
        val llm = json.optJSONObject("llm")

        BenchmarkData(
            poseCpuMs = pose?.optDouble("cpu_ms")?.takeIf { !it.isNaN() }?.let { "${format1(it.toFloat())} ms" },
            poseQnnMs = pose?.optDouble("qnn_ms")?.takeIf { !it.isNaN() }?.let { "${format1(it.toFloat())} ms" },
            llmCpuThroughput = llm?.optDouble("cpu_toks_per_sec")?.takeIf { !it.isNaN() }?.let { "${format1(it.toFloat())} tok/s" },
            llmQnnThroughput = llm?.optDouble("qnn_toks_per_sec")?.takeIf { !it.isNaN() }?.let { "${format1(it.toFloat())} tok/s" },
            sourceLabel = json.optString("source").takeIf { it.isNotBlank() },
        )
    } catch (_: Throwable) {
        null
    }
}

private fun format1(value: Float): String = String.format(Locale.US, "%.1f", value)

private data class SettingsUiState(
    val backendStatus: List<BackendStatus>,
    val benchmarkData: BenchmarkData?,
    val modelInfo: ModelInfoSummary,
    val hasInternetPermission: Boolean,
    val appVersion: String,
) {
    companion object {
        fun loading(): SettingsUiState {
            return SettingsUiState(
                backendStatus = emptyList(),
                benchmarkData = null,
                modelInfo = ModelInfoSummary(
                    poseSummary = "Checking bundled assets…",
                    llmSummary = "Checking bundled assets…",
                    totalStorageLabel = "0.0 MB",
                ),
                hasInternetPermission = false,
                appVersion = "Loading…",
            )
        }
    }
}

private data class BackendStatus(
    val backend: InferenceBackend,
    val label: String,
    val available: Boolean,
    val note: String,
)

private data class BenchmarkData(
    val poseCpuMs: String?,
    val poseQnnMs: String?,
    val llmCpuThroughput: String?,
    val llmQnnThroughput: String?,
    val sourceLabel: String?,
)

private data class ModelFileInfo(
    val path: String,
    val sizeBytes: Long,
)

private data class ModelInfoSummary(
    val poseSummary: String,
    val llmSummary: String,
    val totalStorageLabel: String,
)
