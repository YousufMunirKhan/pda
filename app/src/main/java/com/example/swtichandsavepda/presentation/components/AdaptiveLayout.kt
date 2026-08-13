package com.example.swtichandsavepda.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A column that **fills the available height** by distributing its children,
 * and only falls back to scrolling when they genuinely cannot fit.
 *
 * The [content] lambda receives `fill`: when true, apply [Modifier.weight] to
 * the children that should grow (they will share the leftover height); when
 * false, the column scrolls and children keep their natural size. Growable
 * children should cap their growth with `heightIn(max = …)` so they stay tidy
 * on very large screens — the leftover then becomes balanced top/bottom space.
 *
 * This is the shared rule behind "same design on every size, no dead space":
 * add or remove items and everything re-distributes automatically.
 *
 * @param minHeightToFill the natural minimum the content needs (sum of the
 * children's minimum heights + spacing + padding). At or above this the column
 * fills; below it, it scrolls.
 */
@Composable
fun FillColumn(
    minHeightToFill: Dp,
    modifier: Modifier = Modifier,
    maxHeightToFill: Dp? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.(fill: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val available = maxHeight
        if (available >= minHeightToFill) {
            // Fill mode. If the screen is bigger than [maxHeightToFill], cap the
            // block at that height and centre it — growable children stop at a
            // sensible size and the slack becomes balanced top/bottom padding
            // ("fill with sensible scaling"). Otherwise the block fills fully.
            val capped = maxHeightToFill != null && available > maxHeightToFill
            val blockModifier = if (capped) {
                Modifier.fillMaxWidth().height(maxHeightToFill!!)
            } else {
                Modifier.fillMaxSize()
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = blockModifier.padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) { content(true) }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) { content(false) }
        }
    }
}

/**
 * A width-capped, horizontally-centred column that **centres its content
 * vertically when it fits and scrolls when it doesn't** — so the same design
 * renders identically on every screen size (full-width on a phone, the same
 * centred column on a tablet, scrolling on a short screen), never top-piled
 * with dead space and never stretched.
 */
@Composable
fun CenteredScrollColumn(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 640.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
    spacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // At least the viewport tall, so short content centres; taller
                // content grows past it and scrolls.
                .heightIn(min = viewportHeight)
                .padding(contentPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                content()
            }
        }
    }
}

/**
 * Form layout for the "bigger controls, centred, pinned action" style.
 *
 * The [content] scrolls and is capped to [maxContentWidth] and centred, so on a
 * tablet the form reads as a proper centred sheet rather than fields stretched
 * edge to edge. An optional [footer] (usually the primary button) is pinned to
 * the bottom, above the keyboard.
 */
@Composable
fun FormScaffold(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 560.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth(),
            ) {
                content()
            }
        }

        if (footer != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.widthIn(max = maxContentWidth).fillMaxWidth()) {
                    footer()
                }
            }
        }
    }
}
