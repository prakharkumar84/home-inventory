package com.prakharkumar.homeinventory.ui.recognition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.prakharkumar.homeinventory.AppContainer
import com.prakharkumar.homeinventory.ai.pipeline.RecognitionPipeline
import com.prakharkumar.homeinventory.domain.model.ConfidenceState
import com.prakharkumar.homeinventory.domain.model.InventoryItem
import com.prakharkumar.homeinventory.domain.model.ItemSuggestion
import com.prakharkumar.homeinventory.domain.model.StorageLocation
import com.prakharkumar.homeinventory.domain.repository.ItemRepository
import com.prakharkumar.homeinventory.domain.repository.LocationRepository
import com.prakharkumar.homeinventory.ui.components.ConfidenceChip
import com.prakharkumar.homeinventory.ui.components.ItemThumbnail
import com.prakharkumar.homeinventory.ui.components.LocationPickerDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ReviewUiState(
    val loading: Boolean = true,
    val stage: String = "Starting",
    val photoPath: String = "",
    val photoId: String? = null,
    val suggestions: List<ItemSuggestion> = emptyList(),
    val ocrText: String? = null,
    val message: String? = null,
    val locationId: String? = null,
    val breadcrumb: String = "",
    val locations: List<Pair<StorageLocation, Int>> = emptyList(),
    val duplicateWarning: String? = null,
    val saved: Boolean = false
)

class ReviewViewModel(
    private val photoPath: String,
    initialLocationId: String?,
    private val pipeline: RecognitionPipeline,
    private val itemRepository: ItemRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState(photoPath = photoPath, locationId = initialLocationId))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(locations = locationRepository.flatList()) }
            initialLocationId?.let { id ->
                _state.update { it.copy(breadcrumb = locationRepository.breadcrumb(id)) }
            }
        }
        runRecognition()
    }

    fun runRecognition() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val outcome = pipeline.run(
                photoFile = File(photoPath),
                onStage = { stage -> _state.update { it.copy(stage = stage) } }
            )
            _state.update {
                it.copy(
                    loading = false,
                    photoId = outcome.photoId,
                    suggestions = outcome.suggestions,
                    ocrText = outcome.ocrText,
                    message = outcome.failureMessage
                )
            }
        }
    }

    fun toggle(id: String) = _state.update { s ->
        s.copy(suggestions = s.suggestions.map { if (it.id == id) it.copy(selected = !it.selected) else it })
    }

    fun edit(id: String, name: String? = null, category: String? = null, quantity: Double? = null, description: String? = null) =
        _state.update { s ->
            s.copy(
                suggestions = s.suggestions.map {
                    if (it.id != id) it else it.copy(
                        name = name ?: it.name,
                        category = category ?: it.category,
                        quantity = quantity ?: it.quantity,
                        description = description ?: it.description,
                        userEdited = true,
                        selected = true
                    )
                }
            )
        }

    fun remove(id: String) = _state.update { s ->
        s.copy(suggestions = s.suggestions.filterNot { it.id == id })
    }

    fun addManual() = _state.update { s ->
        s.copy(
            suggestions = s.suggestions + ItemSuggestion(
                id = UUID.randomUUID().toString(),
                name = "",
                confidence = ConfidenceState.NEEDS_CONFIRMATION,
                sourceModels = emptyList(),
                selected = true,
                userEdited = true
            )
        )
    }

    fun chooseLocation(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(locationId = id, breadcrumb = locationRepository.breadcrumb(id)) }
            checkDuplicates()
        }
    }

    /** Spec 13 - warn, never merge automatically. */
    private suspend fun checkDuplicates() {
        val s = _state.value
        val locationId = s.locationId ?: return
        val hits = s.suggestions
            .filter { it.selected && it.name.isNotBlank() }
            .flatMap { itemRepository.possibleDuplicates(locationId, it.name) }
            .distinctBy { it.id }
        _state.update {
            it.copy(
                duplicateWarning = if (hits.isEmpty()) null
                else "A similar item may already be here: " + hits.take(3).joinToString { h -> h.name }
            )
        }
    }

    fun save() {
        val s = _state.value
        val locationId = s.locationId ?: return
        val selected = s.suggestions.filter { it.selected && it.name.isNotBlank() }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val withOcr = selected.map { suggestion ->
                if (suggestion.description.isNullOrBlank() && !s.ocrText.isNullOrBlank() && selected.size == 1) {
                    suggestion.copy(description = s.ocrText.take(200))
                } else suggestion
            }
            itemRepository.saveConfirmed(withOcr, locationId, s.photoPath, s.photoId)
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        fun factory(container: AppContainer, photoPath: String, locationId: String?) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ReviewViewModel(
                    photoPath,
                    locationId,
                    container.recognitionPipeline,
                    container.itemRepository,
                    container.locationRepository
                ) as T
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    container: AppContainer,
    photoPath: String,
    initialLocationId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ReviewViewModel = viewModel(
        key = "review-$photoPath",
        factory = ReviewViewModel.factory(container, photoPath, initialLocationId)
    )
) {
    val state by viewModel.state.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val selectedCount = state.suggestions.count { it.selected && it.name.isNotBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review items") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.addManual() }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add row")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.padding(16.dp)) {
                    state.duplicateWarning?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { viewModel.save() },
                        enabled = selectedCount > 0 && state.locationId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            when {
                                state.locationId == null -> "Choose a location first"
                                selectedCount == 0 -> "Select at least one item"
                                else -> "Save $selectedCount item(s)"
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                    AsyncImage(
                        model = File(state.photoPath),
                        contentDescription = "Captured photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }

            item {
                OutlinedCard(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Save into", style = MaterialTheme.typography.labelMedium)
                        Text(
                            state.breadcrumb.ifBlank { "Tap to choose a location" },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            if (state.loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("${state.stage}…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.message?.let { message ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(message, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            if (!state.ocrText.isNullOrBlank()) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Text found on the item", style = MaterialTheme.typography.labelMedium)
                            Text(
                                state.ocrText!!.take(300),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "OCR can be wrong — edit it before relying on it.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(state.suggestions, key = { it.id }) { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    onToggle = { viewModel.toggle(suggestion.id) },
                    onNameChange = { viewModel.edit(suggestion.id, name = it) },
                    onCategoryChange = { viewModel.edit(suggestion.id, category = it) },
                    onQuantityChange = { viewModel.edit(suggestion.id, quantity = it) },
                    onRemove = { viewModel.remove(suggestion.id) }
                )
            }

            if (!state.loading && state.suggestions.isEmpty()) {
                item {
                    OutlinedButton(onClick = { viewModel.addManual() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add an item manually")
                    }
                }
            }
        }
    }

    if (showPicker) {
        LocationPickerDialog(
            locations = state.locations,
            selectedId = state.locationId,
            title = "Save items into",
            onDismiss = { showPicker = false },
            onSelect = {
                viewModel.chooseLocation(it)
                showPicker = false
            }
        )
    }
}

@Composable
private fun SuggestionCard(
    suggestion: ItemSuggestion,
    onToggle: () -> Unit,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onQuantityChange: (Double) -> Unit,
    onRemove: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = suggestion.selected, onCheckedChange = { onToggle() })
                ItemThumbnail(suggestion.cropPath, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ConfidenceChip(suggestion.confidence)
                    if (suggestion.sourceModels.isNotEmpty()) {
                        Text(
                            "from ${suggestion.sourceModels.joinToString()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove suggestion")
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = suggestion.name,
                onValueChange = onNameChange,
                label = { Text("Item name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = suggestion.category.orEmpty(),
                    onValueChange = onCategoryChange,
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = if (suggestion.quantity % 1.0 == 0.0) suggestion.quantity.toLong().toString()
                    else suggestion.quantity.toString(),
                    onValueChange = { onQuantityChange(it.toDoubleOrNull() ?: 1.0) },
                    label = { Text("Qty") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
