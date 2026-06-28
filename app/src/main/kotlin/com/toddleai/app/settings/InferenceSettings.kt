package com.toddleai.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class InferenceBackend {
    XNNPACK,
    QNN,
}

private val Context.inferencePreferencesDataStore by preferencesDataStore(name = "inference_settings")

class InferenceSettings(
    private val context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val backendPreference: StateFlow<InferenceBackend> = context.inferencePreferencesDataStore.data
        .map { preferences ->
            preferences[BACKEND_KEY]
                ?.let(::backendFromName)
                ?: InferenceBackend.XNNPACK
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = InferenceBackend.XNNPACK,
        )

    suspend fun setBackendPreference(backend: InferenceBackend) {
        context.inferencePreferencesDataStore.edit { preferences ->
            preferences[BACKEND_KEY] = backend.name
        }
    }

    private fun backendFromName(name: String): InferenceBackend {
        return InferenceBackend.entries.firstOrNull { it.name == name } ?: InferenceBackend.XNNPACK
    }

    private companion object {
        val BACKEND_KEY = stringPreferencesKey("preferred_inference_backend")
    }
}
