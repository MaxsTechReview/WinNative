package com.winlator.cmod.app.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R

/** A single Steam launch option (appinfo `config.launch` entry). */
internal data class StoreLaunchOptionItem(
    // Relative path, '/'-separated.
    val executable: String,
    val arguments: String,
    val label: String,
)

// Palette — mirrors the Workshop window so the modal feels native.
private val LoBg = Color(0xFF12121B)
private val LoBorder = Color(0xFF2A2A3A)
private val LoAccent = Color(0xFF1A9FFF)
private val LoAccentGlow = Color(0xFF58A6FF)
private val LoTextPrimary = Color(0xFFF0F4FF)
private val LoTextSecondary = Color(0xFF93A6BC)
private val LoScrim = Color(0xFF000000)

/**
 * Steam launch-option picker — a Workshop-shaped modal window listing the
 * game's appinfo `config.launch` entries. Tapping a row persists it as the
 * game's default; the check mark moves to confirm and the window stays open.
 *
 * Stateless: data and callbacks are hoisted to the LaunchOptionsDialog wrapper.
 */
@Composable
internal fun StoreLaunchOptionsScreen(
    gameTitle: String,
    options: List<StoreLaunchOptionItem>,
    selectedOption: StoreLaunchOptionItem?,
    onSelect: (StoreLaunchOptionItem) -> Unit,
    onClose: () -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                // Dim the game-detail screen behind so the modal reads as foreground.
                .background(LoScrim.copy(alpha = 0.6f))
                .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center,
    ) {
        val dialogWidth = (maxWidth - 32.dp).coerceAtMost(560.dp)
        val dialogMaxHeight = (maxHeight - 48.dp).coerceIn(220.dp, 640.dp)
        Surface(
            modifier =
                Modifier
                    .widthIn(min = 320.dp, max = dialogWidth)
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight),
            shape = RoundedCornerShape(14.dp),
            color = LoBg,
            border = BorderStroke(1.dp, LoBorder),
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                LaunchOptionsHeader(
                    gameTitle = gameTitle,
                    optionCount = options.size,
                    onClose = onClose,
                )
                HorizontalDivider(color = LoBorder, thickness = 0.5.dp)
                LazyColumn(
                    // fill = false: the window wraps short lists instead of
                    // stretching to the max height.
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(options) { index, option ->
                        LaunchOptionPickerRow(
                            option = option,
                            selected = option == selectedOption,
                            onClick = { onSelect(option) },
                        )
                        if (index < options.lastIndex) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.06f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchOptionsHeader(
    gameTitle: String,
    optionCount: Int,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(LoAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.RocketLaunch,
                contentDescription = null,
                tint = LoAccentGlow,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(R.string.store_game_launch_options).uppercase(),
                color = LoTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
            )
            Text(
                gameTitle,
                style = MaterialTheme.typography.titleSmall,
                color = LoTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            modifier =
                Modifier.semantics {
                    contentDescription = "$optionCount launch options"
                },
            color = LoAccent.copy(alpha = 0.14f),
            shape = RoundedCornerShape(7.dp),
        ) {
            Text(
                optionCount.toString(),
                color = LoAccentGlow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Close",
                tint = LoTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LaunchOptionPickerRow(
    option: StoreLaunchOptionItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                option.label,
                color = if (selected) LoAccentGlow else LoTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(option.executable)
                    if (option.arguments.isNotBlank()) {
                        append("  ·  ")
                        append(option.arguments)
                    }
                },
                color = LoTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = LoAccentGlow,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
