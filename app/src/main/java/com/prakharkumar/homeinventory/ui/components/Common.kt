package com.prakharkumar.homeinventory.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prakharkumar.homeinventory.domain.model.ConfidenceState
import com.prakharkumar.homeinventory.domain.model.LocationType
import com.prakharkumar.homeinventory.domain.model.StorageLocation
import java.io.File

/** Spec 5.7 - app-level confidence states, worded so nothing looks certain. */
@Composable
fun ConfidenceChip(state: ConfidenceState, modifier: Modifier = Modifier) {
    val (label, container, content) = when (state) {
        ConfidenceState.STRONG -> Triple(
            "Strong suggestion",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        ConfidenceState.POSSIBLE -> Triple(
            "Possible match",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        ConfidenceState.NEEDS_CONFIRMATION -> Triple(
            "Needs confirmation",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ItemThumbnail(path: String?, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (path != null && File(path).exists()) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Destination picker used by add, move and capture flows. */
@Composable
fun LocationPickerDialog(
    locations: List<Pair<StorageLocation, Int>>,
    selectedId: String?,
    title: String = "Choose a location",
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (locations.isEmpty()) {
                Text("No locations yet. Create one first, for example “Bedroom › Drawer 2”.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(locations, key = { it.first.id }) { (location, depth) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(location.id) }
                                .padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(location.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    location.type.label(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (location.id == selectedId) {
                                Icon(Icons.Default.Check, contentDescription = "Selected")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun LocationTypeDropdown(
    selected: LocationType,
    onSelect: (LocationType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }) { Text(selected.label()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LocationType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label()) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun LocationType.label(): String = when (this) {
    LocationType.HOME -> "Home / space"
    LocationType.ROOM -> "Room"
    LocationType.CUPBOARD -> "Cupboard"
    LocationType.DRAWER -> "Drawer"
    LocationType.BOX -> "Box"
    LocationType.SHELF -> "Shelf"
    LocationType.OTHER -> "Other"
}
