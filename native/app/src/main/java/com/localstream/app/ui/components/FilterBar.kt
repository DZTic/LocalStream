package com.localstream.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.domain.model.ResolutionFilter
import com.localstream.app.domain.model.SortBy
import com.localstream.app.domain.model.TmdbGenre
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc500
import com.localstream.app.ui.theme.Zinc900

/**
 * Barre de tri et de filtres de la bibliothèque (équivalent Compose de
 * `FilterBar.tsx`) : tri (A-Z, date, taille, durée), genre TMDB et qualité
 * en chips Material 3 avec menus déroulants.
 */
@Composable
fun LibraryFilterBar(
    sortBy: SortBy,
    filterGenre: Int?,
    filterResolution: ResolutionFilter,
    onSortBy: (SortBy) -> Unit,
    onFilterGenre: (Int?) -> Unit,
    onFilterResolution: (ResolutionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterMenuChip(
            label = "Trier",
            value = sortByLabel(sortBy),
            items = listOf(SortBy.ALPHA, SortBy.DATE, SortBy.SIZE, SortBy.DURATION),
            itemLabel = { sortByLabel(it) },
            onSelect = onSortBy,
        )
        FilterMenuChip(
            label = "Genre",
            value = filterGenre?.let { TmdbGenre.getGenreName(it) } ?: "Tous les genres",
            items = listOf<Int?>(null) + TmdbGenre.entries.map { it.id as Int? },
            itemLabel = { it?.let(TmdbGenre::getGenreName) ?: "Tous les genres" },
            onSelect = onFilterGenre,
        )
        FilterMenuChip(
            label = "Qualité",
            value = resolutionLabel(filterResolution),
            items = listOf(
                ResolutionFilter.ALL,
                ResolutionFilter.FOUR_K,
                ResolutionFilter.ONE_THOUSAND_EIGHTY_P,
                ResolutionFilter.SEVEN_HUNDRED_TWENTY_P,
                ResolutionFilter.SD,
            ),
            itemLabel = { resolutionLabel(it) },
            onSelect = onFilterResolution,
        )
    }
}

@Composable
private fun <T> FilterMenuChip(
    label: String,
    value: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = CircleShape,
        color = Zinc900.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, Zinc500.copy(alpha = 0.4f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "$label : ",
                color = Zinc500,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = value,
                color = White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = Zinc500,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                )
            }
        }
    }
}

private fun sortByLabel(sortBy: SortBy): String = when (sortBy) {
    SortBy.ALPHA -> "A-Z"
    SortBy.DATE -> "Date d'ajout"
    SortBy.SIZE -> "Taille"
    SortBy.DURATION -> "Durée"
}

private fun resolutionLabel(filter: ResolutionFilter): String = when (filter) {
    ResolutionFilter.ALL -> "Toutes"
    ResolutionFilter.FOUR_K -> "4K / 2160p"
    ResolutionFilter.TWO_K -> "2K / 1440p"
    ResolutionFilter.ONE_THOUSAND_EIGHTY_P -> "1080p"
    ResolutionFilter.SEVEN_HUNDRED_TWENTY_P -> "720p"
    ResolutionFilter.SD -> "SD"
}
