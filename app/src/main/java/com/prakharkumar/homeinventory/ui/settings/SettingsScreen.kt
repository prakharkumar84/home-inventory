package com.prakharkumar.homeinventory.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prakharkumar.homeinventory.AppContainer
import com.prakharkumar.homeinventory.data.files.PhotoStorage
import com.prakharkumar.homeinventory.util.formatBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val photoStorage: PhotoStorage) : ViewModel() {

    private val _usage = MutableStateFlow(0L)
    val usage = _usage.asStateFlow()

    init {
        viewModelScope.launch { _usage.value = photoStorage.usageBytes() }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(container.photoStorage) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
) {
    val usage by viewModel.usage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection("Recognition models") {
                ListItem(
                    headlineContent = { Text("Image labelling") },
                    supportingContent = { Text("ML Kit, bundled, runs on device") },
                    trailingContent = { Text("Installed") }
                )
                ListItem(
                    headlineContent = { Text("Object detection") },
                    supportingContent = {
                        Text("ML Kit multi-object. This one ships unbundled, so Google Play services fetches it once; after that it runs on device.")
                    },
                    trailingContent = { Text("Play services") }
                )
                ListItem(
                    headlineContent = { Text("Text recognition (OCR)") },
                    supportingContent = { Text("ML Kit Latin script, bundled") },
                    trailingContent = { Text("Installed") }
                )
                ListItem(
                    headlineContent = { Text("Mobile detector upgrade") },
                    supportingContent = {
                        Text("Optional YOLO-family / image-text model slot. Not installed; verify licence and size before bundling.")
                    },
                    trailingContent = { Text("Planned") }
                )
            }

            SettingsSection("Storage") {
                ListItem(
                    headlineContent = { Text("Photos and crops") },
                    supportingContent = { Text("Stored in app-private storage only") },
                    trailingContent = { Text(formatBytes(usage)) }
                )
            }

            SettingsSection("Privacy") {
                Text(
                    "Photos are processed on this device. Nothing is uploaded, there is no account, " +
                        "no API key and no server. Labelling and OCR models are bundled in the app; the " +
                        "object detector is fetched once by Google Play services and then runs offline too. " +
                        "Device cloud backup is disabled for this app's data, so your inventory stays on " +
                        "this phone until you export it yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            SettingsSection("About") {
                ListItem(
                    headlineContent = { Text("Version") },
                    trailingContent = { Text("1.0") }
                )
                ListItem(
                    headlineContent = { Text("Offline status") },
                    supportingContent = { Text("No network permission is requested") }
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp))
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}
