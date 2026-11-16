package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.MediaItem
import com.example.ui.components.ErrorRetryBox
import com.example.ui.components.MediaPosterCard
import com.example.ui.theme.BroadcastCyan
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.SurfaceCard
import com.example.ui.viewmodel.CatalogViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun SearchScreen(
  viewModel: CatalogViewModel,
  onOpenStream: (MediaItem) -> Unit,
  onOpenById: (subjectId: String, title: String, detailPath: String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val query by viewModel.searchQuery.collectAsStateWithLifecycle()
  val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
  val resultsState by viewModel.searchResultsState.collectAsStateWithLifecycle()
  val keyboardController = LocalSoftwareKeyboardController.current

  Column(modifier = modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = query,
        onValueChange = { viewModel.onSearchQueryChanged(it) },
        placeholder = { Text(stringResource(R.string.search_hint)) },
        singleLine = true,
        leadingIcon = {
          Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = BroadcastCyan,
          )
        },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(
              onClick = { viewModel.onSearchQueryChanged("") },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(Icons.Filled.Clear, contentDescription = "Clear search")
            }
          }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
          KeyboardActions(
            onSearch = {
              keyboardController?.hide()
              viewModel.submitSearch()
            }
          ),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BroadcastCyan,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = SurfaceCard,
            unfocusedContainerColor = SurfaceCard,
          ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.weight(1f).testTag("search_input"),
      )

      Surface(
        color = ElectricCrimson,
        shape = MaterialTheme.shapes.medium,
        modifier =
          Modifier.size(54.dp)
            .clickable {
              keyboardController?.hide()
              viewModel.submitSearch()
            }
            .testTag("search_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }
    }

    if (suggestions.isNotEmpty()) {
      Surface(
        color = SurfaceCard,
        shape = MaterialTheme.shapes.medium,
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
      ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          items(suggestions, key = { "${it.subjectId}_${it.title}" }) { s ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable {
                    keyboardController?.hide()
                    onOpenById(s.subjectId, s.title, s.slug)
                  }
                  .padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Filled.PlayCircleOutline,
                  contentDescription = null,
                  tint = BroadcastCyan,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                  text = s.title,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
              Text(
                text = "HD",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCrimson,
              )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
          }
        }
      }
    }

    when (val state = resultsState) {
      UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = ElectricCrimson)
        }
      }
      is UiState.Error -> {
        ErrorRetryBox(message = state.message, onRetry = { viewModel.submitSearch() })
      }
      is UiState.Success -> {
        if (state.data.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
              )
              Text(
                text = stringResource(R.string.search_empty_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 136.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(state.data, key = { it.subjectId }) { item ->
              MediaPosterCard(item = item, onClick = { onOpenStream(item) })
            }
          }
        }
      }
    }
  }
}
