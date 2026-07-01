package com.youtroc.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.youtroc.core.ui.component.TvVideoCard
import com.youtroc.core.ui.component.VideoCardUi
import com.youtroc.core.ui.theme.AlmostBlack
import com.youtroc.core.ui.theme.ElevatedSurface
import com.youtroc.core.ui.theme.OnDark
import com.youtroc.core.ui.theme.OnDarkMuted
import com.youtroc.core.ui.theme.YouTrocDimens
import com.youtroc.core.ui.theme.YouTrocRed

/**
 * Presentational search content: a query field that triggers the system
 * on-screen IME, above a results grid / status panel for [state].
 *
 * Submit-on-confirm ONLY (RF-SRCH-01): [onSubmit] fires from
 * `KeyboardActions(onSearch = …)`, never from `onValueChange` — no
 * per-keystroke calls to the domain.
 *
 * Every INTERACTIVE state binds a D-pad-reachable focus target — the
 * project's recurring focus-trap risk (real-catalog design-gate #4399,
 * reapplied here per design-gate #4408): [fieldFocusRequester] for
 * `Idle`/`Empty` (the field is ALWAYS composed, the top anchor — Empty
 * focuses the field to retype, a deliberate divergence from the spec's
 * "message anchor" wording, flagged for archive reconciliation — see tasks
 * #4409 item 12.1), [resultsFocusRequester] for `Results` (the grid
 * CONTAINER, which delegates to its first child via `.focusGroup()` — gate
 * MINOR-1, verify-review fix batch: a not-yet-placed lazy ITEM is a flaky
 * focus target, mirrors [com.youtroc.feature.catalog.HomeContent]'s
 * container-focus pattern) / `Offline`/`Error` (Retry). `Loading` is the
 * sole exception — transient, the field simply stays focused.
 *
 * Compose/IME/focus glue is integration-tested only (validated on device),
 * matching this project's convention (`HomeShell`/`HomeContent`/
 * `PlayerOverlay` carry no headless Compose test either).
 */
@Composable
fun SearchContent(
    state: SearchUiState,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit,
    onVideoClick: (VideoCardUi) -> Unit,
    fieldFocusRequester: FocusRequester,
    resultsFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state::class) {
        when (state) {
            SearchUiState.Idle, SearchUiState.Empty ->
                runCatching { fieldFocusRequester.requestFocus() }

            is SearchUiState.Results, SearchUiState.Offline, SearchUiState.Error ->
                runCatching { resultsFocusRequester.requestFocus() }

            SearchUiState.Loading -> Unit
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            onSubmit = onSubmit,
            focusRequester = fieldFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = YouTrocDimens.overscanHorizontal,
                    vertical = YouTrocDimens.overscanVertical,
                ),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = YouTrocDimens.overscanVertical),
        ) {
            when (state) {
                SearchUiState.Idle -> SearchMessage(text = "Buscá videos en YouTube")

                SearchUiState.Loading -> SearchMessage(text = "Cargando…")

                is SearchUiState.Results -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = YouTrocDimens.cardWidth),
                    horizontalArrangement = Arrangement.spacedBy(YouTrocDimens.cardSpacing),
                    verticalArrangement = Arrangement.spacedBy(YouTrocDimens.shelfSpacing),
                    contentPadding = PaddingValues(horizontal = YouTrocDimens.overscanHorizontal),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(resultsFocusRequester)
                        .focusGroup(),
                ) {
                    itemsIndexed(
                        items = state.videos,
                        key = { _, video -> video.id },
                        contentType = { _, _ -> "videoCard" },
                    ) { _, video ->
                        TvVideoCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                        )
                    }
                }

                // gate minor-3 (design-gate #4408): Empty focuses the FIELD,
                // not a message anchor — diverges from spec's "message
                // anchor" wording; see the LaunchedEffect table above.
                SearchUiState.Empty -> SearchMessage(text = "No encontramos resultados.")

                SearchUiState.Offline -> SearchMessage(
                    text = "Sin conexión. Revisá tu red.",
                    onRetry = onRetry,
                    retryFocusRequester = resultsFocusRequester,
                )

                SearchUiState.Error -> SearchMessage(
                    text = "Algo salió mal en la búsqueda.",
                    onRetry = onRetry,
                    retryFocusRequester = resultsFocusRequester,
                )
            }
        }
    }
}

/**
 * The query field. Focusing it and pressing D-pad OK opens the system
 * on-screen IME automatically (platform behavior on Google TV — no explicit
 * invocation). [KeyboardActions.onSearch] hides the IME and fires
 * [onSubmit] with the trimmed text; typing itself never calls [onSubmit].
 */
@Composable
private fun SearchField(
    onSubmit: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier
            .background(ElevatedSurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = OnDark),
            cursorBrush = SolidColor(YouTrocRed),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    onSubmit(text.trim())
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = "Buscá videos en YouTube",
                        color = OnDarkMuted,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                innerTextField()
            },
        )
    }
}

/**
 * Centered status message. When [onRetry] is set, renders a focusable Retry
 * action bound to [retryFocusRequester]. Local duplicate of
 * `:feature:catalog`'s `HomeMessage`/`RetryButton` (same rule-of-three
 * deferral already applied to [SearchMetaFormatter]).
 */
@Composable
private fun SearchMessage(
    text: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryFocusRequester: FocusRequester? = null,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = OnDarkMuted,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                RetryButton(
                    onClick = onRetry,
                    modifier = if (retryFocusRequester != null) {
                        Modifier.focusRequester(retryFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/** Focusable retry action — same tv-material Surface pattern as `RailItem`/`TvVideoCard`. */
@Composable
private fun RetryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ElevatedSurface,
            focusedContainerColor = OnDark,
            contentColor = OnDark,
            focusedContentColor = AlmostBlack,
        ),
        modifier = modifier,
    ) {
        Text(
            text = "Reintentar",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
