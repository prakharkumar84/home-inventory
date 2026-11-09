package com.prakharkumar.homeinventory.ui.itemdetails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.prakharkumar.homeinventory.AppContainer
import com.prakharkumar.homeinventory.domain.model.InventoryItem
import com.prakharkumar.homeinventory.domain.model.StorageLocation
import com.prakharkumar.homeinventory.domain.repository.ItemRepository
import com.prakharkumar.homeinventory.domain.repository.LocationRepository
import com.prakharkumar.homeinventory.ui.components.LocationPickerDialog
import com.prakharkumar.homeinventory.util.formatQuantity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

data class ItemDetailState(
    val item: InventoryItem? = null,
    val breadcrumb: String = "",
    val locations: List<Pair<StorageLocation, Int>> = emptyList(),
    val deleted: Boolean = false
)

class ItemDetailViewModel(
    private val itemId: String,
    private val itemRepository: ItemRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val breadcrumb = MutableStateFlow("")
    private val locations = MutableStateFlow<List<Pair<StorageLocation, Int>>>(emptyList())
    private val deleted = MutableStateFlow(false)

    val state = combine(
        itemRepository.observeItem(itemId),
        breadcrumb,
        locations,
        deleted
    ) { item, crumb, locs, isDeleted ->
        ItemDetailState(item, crumb, locs, isDeleted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailState())

    init {
        viewModelScope.launch {
            locations.value = locationRepository.flatList()
            itemRepository.observeItem(itemId).collect { item ->
                if (item != null) breadcrumb.value = locationRepository.breadcrumb(item.locationId)
            }
        }
    }

    fun save(name: String, category: String, description: String, quantity: Double, notes: String) {
        val current = state.value.item ?: return
        viewModelScope.launch {
            itemRepository.update(
                current.copy(
                    name = name,
                    category = category.ifBlank { null },
                    description = description.ifBlank { null },
                    quantity = quantity,
                    notes = notes.ifBlank { null }
                )
            )
        }
    }

    fun move(toLocationId: String) {
        viewModelScope.launch {
            itemRepository.move(itemId, toLocationId, "Moved by user")
            breadcrumb.value = locationRepository.breadcrumb(toLocationId)
        }
    }

    fun delete() {
        viewModelScope.launch {
            itemRepository.delete(itemId)
            deleted.value = true
        }
    }

    companion object {
        fun factory(container: AppContainer, itemId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ItemDetailViewModel(itemId, container.itemRepository, container.locationRepository) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    container: AppContainer,
    itemId: String,
    onBack: () -> Unit,
    onOpenLocation: (String) -> Unit,
    viewModel: ItemDetailViewModel = viewModel(
        key = "item-$itemId",
        factory = ItemDetailViewModel.factory(container, itemId)
    )
) {
    val state by viewModel.state.collectAsState()
    var showMove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    val item = state.item

    var name by remember(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var category by remember(item?.id) { mutableStateOf(item?.category.orEmpty()) }
    var description by remember(item?.id) { mutableStateOf(item?.description.orEmpty()) }
    var notes by remember(item?.id) { mutableStateOf(item?.notes.orEmpty()) }
    var quantity by remember(item?.id) {
        mutableStateOf(item?.quantity?.let { formatQuantity(it) } ?: "1")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.name ?: "Item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMove = true }) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = "Move")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        if (item == null) {
            Box(Modifier.padding(padding)) { CircularProgressIndicator(Modifier.padding(32.dp)) }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (item.photoPath != null && File(item.photoPath).exists()) {
                Surface(shape = RoundedCornerShape(16.dp)) {
                    AsyncImage(
                        model = File(item.photoPath),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }

            AssistChip(
                onClick = { onOpenLocation(item.locationId) },
                label = { Text(state.breadcrumb.ifBlank { "Location" }) }
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Qty") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    viewModel.save(name, category, description, quantity.toDoubleOrNull() ?: 1.0, notes)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save changes") }

            val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
            Text(
                "Added ${df.format(Date(item.createdAt))} · Updated ${df.format(Date(item.updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showMove) {
        LocationPickerDialog(
            locations = state.locations,
            selectedId = item?.locationId,
            title = "Move item to",
            onDismiss = { showMove = false },
            onSelect = {
                viewModel.move(it)
                showMove = false
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this item?") },
            text = { Text("The record will be removed. Its photo is deleted only if no other item uses it.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
