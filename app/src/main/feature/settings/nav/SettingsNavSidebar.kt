package com.winlator.cmod.feature.settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.shared.ui.widget.chasingBorder

// ─── Palette ────────────────────────────────────────────────────────

private val SidebarBgTop = Color(0xFF171E2E)
private val SidebarBgBot = Color(0xFF11161F)
private val SectionLabelClr = Color(0xFF3D4F65)
private val TextNormal = Color(0xFF7A8FA8)
private val TextSelected = Color(0xFFF0F4FF)
private val IconMuted = Color(0xFF4A7A8F)
private val SelectedBg = Color(0xFF131D2F)
private val DividerColor = Color(0xFF212C3F)

private val AccentSelected = Color(0xFF4FC3F7)

private val InterFamily = FontFamily(Font(R.font.inter_medium, FontWeight.Medium))

// ─── Navigation model ───────────────────────────────────────────────

enum class NavSection {
    ACCOUNTS,
    SYSTEM,
    TOOLS,
}

enum class SettingsNavItem(
    val menuId: Int,
    val icon: ImageVector,
    val titleRes: Int,
    val section: NavSection,
) {
    GOOGLE(R.id.main_menu_google, Icons.Outlined.AccountCircle, R.string.google_cloud_google, NavSection.ACCOUNTS),
    STORES(R.id.main_menu_stores, Icons.Outlined.ShoppingBag, R.string.stores_accounts_title, NavSection.ACCOUNTS),
    CONTAINERS(R.id.main_menu_containers, Icons.Outlined.ViewInAr, R.string.common_ui_containers, NavSection.SYSTEM),
    PRESETS(R.id.main_menu_settings, Icons.Outlined.Tune, R.string.container_presets_title, NavSection.SYSTEM),
    COMPONENTS(R.id.main_menu_contents, Icons.Outlined.Extension, R.string.settings_content_components, NavSection.SYSTEM),
    DRIVERS(R.id.main_menu_adrenotools_gpu_drivers, Icons.Outlined.Memory, R.string.settings_drivers_title, NavSection.SYSTEM),
    INPUT_CONTROLS(R.id.main_menu_input_controls, Icons.Outlined.SportsEsports, R.string.common_ui_input_controls, NavSection.SYSTEM),
    OTHER(R.id.main_menu_other, Icons.Outlined.Widgets, R.string.common_ui_other, NavSection.SYSTEM),
    DEBUG(R.id.main_menu_advanced, Icons.Outlined.BugReport, R.string.settings_debug_title, NavSection.TOOLS),
    ;

    companion object {
        fun fromMenuId(id: Int): SettingsNavItem? = entries.find { it.menuId == id }
    }
}

private val sectionedNavItems: Map<NavSection, List<SettingsNavItem>> =
    SettingsNavItem.entries.groupBy { it.section }

// ─── Main sidebar composable ────────────────────────────────────────

@Composable
fun SettingsNavSidebar(
    selectedItem: SettingsNavItem,
    onItemSelected: (SettingsNavItem) -> Unit,
    onBackPressed: () -> Unit,
    bordersPaused: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier =
                Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(SidebarBgTop, SidebarBgBot)))
                    .navigationBarsPadding(),
        ) {
            // Header
            SidebarHeader(onBackPressed)

            // Scrollable nav items
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 16.dp),
            ) {
                NavSection.entries.forEachIndexed { index, section ->
                    if (index > 0) Spacer(Modifier.height(4.dp))
                    SectionHeader(section.name)
                    sectionedNavItems[section]?.forEach { item ->
                        NavItemRow(
                            item = item,
                            isSelected = item == selectedItem,
                            borderPaused = bordersPaused,
                            onClick = { onItemSelected(item) },
                        )
                    }
                }
            }
        }

        // Right-edge divider
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(DividerColor),
        )
    }
}

// ─── Header ─────────────────────────────────────────────────────────

@Composable
private fun SidebarHeader(onBackPressed: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBackPressed,
                ).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 6.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.common_ui_back),
            tint = AccentSelected,
            modifier = Modifier.size(22.dp),
        )

        Text(
            text = stringResource(R.string.common_ui_settings).uppercase(),
            color = SectionLabelClr,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = InterFamily,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

// ─── Section header ─────────────────────────────────────────────────

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        color = SectionLabelClr,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = InterFamily,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
    )
}

// ─── Navigation item row ────────────────────────────────────────────

@Composable
private fun NavItemRow(
    item: SettingsNavItem,
    isSelected: Boolean,
    borderPaused: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) AccentSelected else IconMuted,
        animationSpec = tween(280),
        label = "iconTint",
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) TextSelected else TextNormal,
        animationSpec = tween(280),
        label = "textColor",
    )
    val bgAlpha by animateFloatAsState(
        targetValue =
            when {
                isSelected -> 1f
                isHovered || isFocused -> 0.5f
                else -> 0f
            },
        animationSpec = tween(200),
        label = "bgAlpha",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .background(
                    color = SelectedBg.copy(alpha = bgAlpha),
                    shape = RoundedCornerShape(8.dp),
                ).then(
                    if (isSelected) {
                        Modifier.chasingBorder(
                            paused = borderPaused,
                            animationDurationMs = 8200,
                            borderWidth = 1.5.dp,
                        )
                    } else {
                        Modifier
                    },
                ).then(
                    if (!isSelected && (isHovered || isFocused)) {
                        Modifier.staticBorder()
                    } else {
                        Modifier
                    },
                ).hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )

        Text(
            text = stringResource(item.titleRes),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = InterFamily,
            letterSpacing = 0.02.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

// ─── Static hover/focus border ──────────────────────────────────────

private fun Modifier.staticBorder(
    cornerRadius: Dp = 8.dp,
    borderWidth: Dp = 1.5.dp,
    color: Color = Color(0x5000D7F5),
): Modifier =
    this.drawWithContent {
        drawContent()
        val bw = borderWidth.toPx()
        val cr = cornerRadius.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(bw / 2, bw / 2),
            size = Size(size.width - bw, size.height - bw),
            cornerRadius = CornerRadius(cr, cr),
            style = Stroke(width = bw),
        )
    }
