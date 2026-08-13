package com.example.swtichandsavepda.presentation.screens.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.presentation.components.BrandScaffold
import com.example.swtichandsavepda.presentation.components.FillColumn
import com.example.swtichandsavepda.presentation.components.IconWell
import com.example.swtichandsavepda.presentation.components.StatusCapsule
import com.example.swtichandsavepda.ui.theme.StatusDanger
import com.example.swtichandsavepda.ui.theme.WellBlue
import com.example.swtichandsavepda.ui.theme.WellBlueFg
import com.example.swtichandsavepda.ui.theme.WellGreen
import com.example.swtichandsavepda.ui.theme.WellGreenFg
import com.example.swtichandsavepda.ui.theme.WellOrange
import com.example.swtichandsavepda.ui.theme.WellOrangeFg
import com.example.swtichandsavepda.ui.theme.WellRose
import com.example.swtichandsavepda.ui.theme.WellRoseFg
import com.example.swtichandsavepda.ui.theme.WellTeal
import com.example.swtichandsavepda.ui.theme.WellTealFg
import com.example.swtichandsavepda.ui.theme.brandColors

@Composable
fun MenuScreen(
    onAdjustStock: () -> Unit,
    onUploadNewStock: () -> Unit,
    onAddLinesToPo: () -> Unit,
    onPurchaseReturn: () -> Unit,
    onScan: () -> Unit,
    onLogout: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    // Scale the whole screen up as it gets wider so text/icons/logo grow with
    // it instead of looking shrunk in stretched cards. 1.0x on a ~400dp phone,
    // up to 1.5x on large tablets. Bumping density scales every dp and sp
    // together; layout still fills the real pixels.
    val uiScale = (configuration.screenWidthDp / 400f).coerceIn(1f, 1.5f)

    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density * uiScale, baseDensity.fontScale),
    ) {
        BrandScaffold(
            title = "Main Menu",
            subtitle = "Main Warehouse",
            leading = { HeaderAvatar(initials = "MW") },
        ) {
        // Fills the screen on every size: the hero + four cards stretch to
        // cover the height (welcome + log out stay their natural size), full
        // width. Same filled design everywhere; scrolls only on a short screen.
        FillColumn(minHeightToFill = 780.dp, spacing = 14.dp) { fill ->
            val heroMod = (if (fill) Modifier.weight(1.3f) else Modifier).heightIn(min = 150.dp)
            val itemMod = if (fill) Modifier.weight(1f) else Modifier

            WelcomeSection()

            ScanHero(onClick = onScan, modifier = heroMod)

            MenuItem(
                label = "Adjust Stock",
                caption = "Correct counts after a stock check",
                icon = Icons.Filled.Tune,
                wellColor = WellOrange,
                glyphColor = WellOrangeFg,
                onClick = onAdjustStock,
                modifier = itemMod,
            )

            MenuItem(
                label = "Upload New Stock",
                caption = "Stock in goods received from a supplier",
                icon = Icons.Filled.Inventory2,
                wellColor = WellGreen,
                glyphColor = WellGreenFg,
                onClick = onUploadNewStock,
                modifier = itemMod,
            )

            MenuItem(
                label = "Purchase Orders",
                caption = "Create a draft PO or receive one",
                icon = Icons.Filled.ShoppingCart,
                wellColor = WellTeal,
                glyphColor = WellTealFg,
                onClick = onAddLinesToPo,
                modifier = itemMod,
            )

            MenuItem(
                label = "Purchase Returns",
                caption = "Return goods to a supplier",
                icon = Icons.AutoMirrored.Filled.AssignmentReturn,
                wellColor = WellRose,
                glyphColor = WellRoseFg,
                onClick = onPurchaseReturn,
                modifier = itemMod,
            )

            LogOutButton(onClick = onLogout)
        }
        }
    }
}

/** Initials chip on the header gradient. */
@Composable
private fun HeaderAvatar(initials: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun WelcomeSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(WellGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warehouse,
                contentDescription = null,
                tint = WellGreenFg,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "Welcome back!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.brandColors.textHeading,
            )
            Text(
                text = "Manage your inventory and stock with ease.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.brandColors.textSecondary,
            )
        }
    }
}

/** Soft-shadowed white rounded card — the mockup surface treatment. */
@Composable
private fun MenuCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 5.dp, shape = RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ScanHero(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.brandColors.heroGradient)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Scan barcode / QR",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "Scan an item, then choose what to do",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun MenuItem(
    label: String,
    caption: String,
    icon: ImageVector,
    wellColor: Color,
    glyphColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeBackground: Color = WellBlue,
    badgeForeground: Color = WellBlueFg,
) {
    MenuCard(onClick = onClick, modifier = modifier.heightIn(min = 96.dp)) {
        IconWell(icon = icon, tint = glyphColor, background = wellColor, size = 48.dp)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.brandColors.textHeading,
                )
                if (badge != null) {
                    StatusCapsule(
                        label = badge,
                        background = badgeBackground,
                        foreground = badgeForeground,
                    )
                }
            }
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.brandColors.textSecondary,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.brandColors.textTertiary,
        )
    }
}

/** Card-style Log Out — subtle bordered surface, centred red content. */
@Composable
private fun LogOutButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            tint = StatusDanger,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Log Out",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = StatusDanger,
        )
    }
}
