package com.prakharkumar.homeinventory.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prakharkumar.homeinventory.AppContainer
import com.prakharkumar.homeinventory.domain.model.InventoryItem
import com.prakharkumar.homeinventory.domain.model.LocationType
import com.prakharkumar.homeinventory.domain.model.StorageLocation
import com.prakharkumar.homeinventory.domain.repository.ItemRepository
import com.prakharkumar.homeinventory.domain.repository.LocationRepository
import com.prakharkumar.homeinventory.ui.components.EmptyState
import com.prakharkumar.homeinventory.ui.components.ItemThumbnail
import com.prakharkumar.homeinventory.ui.components.LocationTypeDropdown
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val itemCount: Int = 0,
    val locationCount: Int = 0,
    val recentItems: List<InventoryItem> = emptyList(),
    val topLocations: List<StorageLocation> = emptyList()
)

class HomeViewModel(
    private val locationRepository: LocationRepository,
    itemRepository: ItemRepository
) : ViewModel() {

    val state = combine(
        itemRepository.observeItemCount(),
        locationRepository.observeLocationCount(),
        itemRepository.observeRecent(8),
        locationRepository.observeChildren(null)
    ) { items, locations, recent, roots ->
        HomeUiState(items, locations, recent, roots)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun createTopLevelLocation(name: String, type: LocationType) {
        viewModelScope.launch {
            locationRepository.create(name, null, type, null)
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(container.locationRepository, container.itemRepository) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onSearch: () -> Unit,
    onBrowseLocations: () -> Unit,
    onScan: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenLocation: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
) {
    val state by viewModel.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home Inventory") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                text = { Text("Add items") }
            )
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
                Surface(
                    onClick = onSearch,
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Where did I put that thing?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Items", state.itemCount.toString(), Modifier.weight(1f))
                    StatCard("Locations", state.locationCount.toString(), Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New space")
                    }
                    Button(onClick = onBrowseLocations, modifier = Modifier.weight(1f)) {
                        Text("Browse locations")
                    }
                }
            }

            if (state.topLocations.isNotEmpty()) {
                item { SectionHeader("Your spaces") }
                items(state.topLocations, key = { it.id }) { location ->
                    ListItem(
                        headlineContent = { Text(location.name) },
                        supportingContent = { Text("${location.itemCount} items stored here") },
                        modifier = Modifier.clickable { onOpenLocation(location.id) }
                    )
                    HorizontalDivider()
                }
            }

            if (state.recentItems.isNotEmpty()) {
                item { SectionHeader("Recently added") }
                items(state.recentItems, key = { it.id }) { item ->
                    ListItem(
                        leadingContent = { ItemThumbnail(item.photoPath) },
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text(item.category ?: "Uncategorised") },
                        modifier = Modifier.clickable { onOpenItem(item.id) }
                    )
                    HorizontalDivider()
                }
            }

            if (state.itemCount == 0 && state.locationCount == 0) {
                item {
                    EmptyState(
                        title = "Nothing stored yet",
                        subtitle = "Create a space such as “My Home”, add rooms and drawers inside it, then photograph what you put away.",
                        action = {
                            Button(onClick = { showCreate = true }) { Text("Create my first space") }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showCreate) {
        CreateLocationDialog(
            title = "New space",
            defaultType = LocationType.HOME,
            onDismiss = { showCreate = false },
            onConfirm = { name, type ->
                viewModel.createTopLevelLocation(name, type)
                showCreate = false
            }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun CreateLocationDialog(
    title: String,
    defaultType: LocationType,
    onDismiss: () -> Unit,
    onConfirm: (String, LocationType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(defaultType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LocationTypeDropdown(selected = type, onSelect = { type = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), type) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
