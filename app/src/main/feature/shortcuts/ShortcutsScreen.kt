package com.winlator.cmod.feature.shortcuts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.shared.theme.WinNativeAccent
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativePanel
import com.winlator.cmod.shared.theme.WinNativeSurface
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary
import com.winlator.cmod.shared.theme.WinNativeTheme

interface ShortcutsActionListener {
    fun onRunShortcut(shortcut: Shortcut)

    fun onEditShortcut(shortcut: Shortcut)

    fun onAddToHomeScreen(shortcut: Shortcut)

    fun onRemoveShortcut(shortcut: Shortcut)

    fun onExportShortcut(shortcut: Shortcut)

    fun onCloneShortcut(shortcut: Shortcut)

    fun onShowProperties(shortcut: Shortcut)
}

fun setupShortcutsComposeView(
    composeView: ComposeView,
    shortcuts: List<Shortcut>,
    listener: ShortcutsActionListener,
) {
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        WinNativeTheme {
            ShortcutsScreen(shortcuts = shortcuts, listener = listener)
        }
    }
}

@Composable
private fun ShortcutsScreen(
    shortcuts: List<Shortcut>,
    listener: ShortcutsActionListener,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(WinNativeBackground)
                .padding(start = 16.dp, top = 16.dp, end = 26.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.common_ui_shortcuts).uppercase(),
            color = WinNativeTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
        if (shortcuts.isEmpty()) {
            Text(
                text = stringResource(R.string.common_ui_no_items_to_display),
                color = WinNativeTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = shortcuts,
                    key = { shortcut -> shortcut.file?.absolutePath ?: shortcut.name },
                ) { shortcut ->
                    ShortcutRow(shortcut = shortcut, listener = listener)
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    shortcut: Shortcut,
    listener: ShortcutsActionListener,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WinNativeSurface)
                .border(1.dp, WinNativeOutline, RoundedCornerShape(12.dp))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { listener.onRunShortcut(shortcut) },
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(WinNativePanel),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = shortcut.icon
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.icon_shortcut),
                        contentDescription = null,
                        tint = WinNativeAccent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.name,
                    color = WinNativeTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = shortcut.container.name,
                    color = WinNativeTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { menuExpanded = true },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = WinNativeTextPrimary,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = WinNativeSurface,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shortcuts_properties_custom_game_settings), color = WinNativeTextPrimary) },
                    onClick = {
                        menuExpanded = false
                        listener.onEditShortcut(shortcut)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shortcuts_list_add_to_home_screen), color = WinNativeTextPrimary) },
                    onClick = {
                        menuExpanded = false
                        listener.onAddToHomeScreen(shortcut)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_ui_remove), color = WinNativeTextPrimary) },
                    onClick = {
                        menuExpanded = false
                        listener.onRemoveShortcut(shortcut)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_ui_export), color = WinNativeTextPrimary) },
                    onClick = {
                        menuExpanded = false
                        listener.onExportShortcut(shortcut)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_ui_clone), color = WinNativeTextPrimary) },
                    onClick = {
                        menuExpanded = false
                        listener.onCloneShortcut(shortcut)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_ui_properties), color = WinNativeTextPrimary) },
                    onClick = {
                        menuExpanded = false
                        listener.onShowProperties(shortcut)
                    },
                )
            }
        }
    }
}
