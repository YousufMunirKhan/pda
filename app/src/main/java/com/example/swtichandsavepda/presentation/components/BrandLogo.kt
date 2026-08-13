package com.example.swtichandsavepda.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.R

/**
 * The Switch&Save wordmark framed on a white plate.
 *
 * `ic_logo` is a black wordmark + coloured glyph on a transparent background, so
 * it only reads on a light surface. Both places that show it — the splash hero
 * and the sign-in card — sit on the dark/coloured brand gradients, where the
 * black text disappears. Framing it on white guarantees legibility in either
 * theme, so this is the single source of truth for rendering the logo.
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    logoHeight: Dp = 56.dp,
) {
    Box(
        modifier = modifier
            // Modifier shadow (not Surface elevation) so the plate stays a true
            // white and never picks up Material 3 tonal tinting.
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false,
            )
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(logoHeight),
        )
    }
}
