package com.winlator.cmod.shared.android

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeAccent
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativePanel
import com.winlator.cmod.shared.theme.WinNativeSurface
import com.winlator.cmod.shared.theme.WinNativeSurfaceAlt
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary
import com.winlator.cmod.shared.theme.WinNativeTheme
import java.io.File
import java.util.Locale

object DirectoryPickerDialog {
    private val FooterButtonHeight = 36.dp
    private val DialogHorizontalPadding = 18.dp
    private val DialogCutoutStartPadding = 14.dp
    private val CurrentPathHorizontalPadding = 10.dp
    private val FolderGridCardPadding = 6.dp
    private val BgDark = WinNativeBackground
    private val CardDark = WinNativeSurface
    private val CardBorder = WinNativeOutline
    private val CardSubtle = WinNativeSurfaceAlt
    private val IconBoxBg = Color(0xFF242434)
    private val Accent = WinNativeAccent
    private val TextPrimary = WinNativeTextPrimary
    private val TextSecondary = WinNativeTextSecondary

    private enum class SelectionMode {
        DIRECTORY,
        FILE,
    }

    private data class Entry(
        val label: String,
        val target: File,
        val isParent: Boolean = false,
        val isSelectableFile: Boolean = false,
    )

    @JvmStatic
    fun show(
        activity: Activity,
        initialPath: String? = null,
        title: String = activity.getString(R.string.common_ui_select_folder),
        onSelected: (String) -> Unit,
    ) {
        if (!ensureAllFilesAccess(activity)) return

        val roots = buildRootDirectories(activity)
        val initialDir = resolveInitialDirectory(initialPath, roots)

        val dialog =
            Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(true)
                setCanceledOnTouchOutside(true)
                window?.apply {
                    setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    setDimAmount(0.18f)
                }
            }

        val composeView =
            ComposeView(activity).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                (activity as? ComponentActivity)?.let {
                    setViewTreeLifecycleOwner(it)
                    setViewTreeSavedStateRegistryOwner(it)
                }
                setContent {
                    val defaultDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(defaultDensity.density, fontScale = 1f),
                    ) {
                        WinNativeTheme {
                            DirectoryPickerDialogContent(
                                title = title,
                                initialDir = initialDir,
                                roots = roots,
                                mode = SelectionMode.DIRECTORY,
                                allowedExtensions = emptySet(),
                                onDismiss = { dialog.dismiss() },
                                onSelect = { path ->
                                    onSelected(path)
                                    dialog.dismiss()
                                },
                            )
                        }
                    }
                }
            }

        dialog.setContentView(composeView)
        dialog.show()
        dialog.window?.apply {
            val dm = activity.resources.displayMetrics
            val screenWidthDp = dm.widthPixels / dm.density
            if (screenWidthDp < 600f) {
                setLayout((dm.widthPixels * 0.96f).toInt(), (dm.heightPixels * 0.94f).toInt())
            } else {
                setLayout((dm.widthPixels * 0.88f).toInt(), (dm.heightPixels * 0.92f).toInt())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val params = attributes
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.blurBehindRadius = 12
                attributes = params
            }
        }
    }

    fun showFile(
        activity: Activity,
        initialPath: String? = null,
        title: String = activity.getString(R.string.common_ui_open_file),
        allowedExtensions: Set<String> = emptySet(),
        onSelected: (String) -> Unit,
    ) {
        if (!ensureAllFilesAccess(activity)) return

        val roots = buildRootDirectories(activity)
        val initialDir = resolveInitialDirectory(initialPath, roots)
        val normalizedExtensions =
            allowedExtensions
                .map { it.trim().trimStart('.').lowercase(Locale.getDefault()) }
                .filter { it.isNotEmpty() }
                .toSet()

        val dialog =
            Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(true)
                setCanceledOnTouchOutside(true)
                window?.apply {
                    setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    setDimAmount(0.18f)
                }
            }

        val composeView =
            ComposeView(activity).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                (activity as? ComponentActivity)?.let {
                    setViewTreeLifecycleOwner(it)
                    setViewTreeSavedStateRegistryOwner(it)
                }
                setContent {
                    val defaultDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(defaultDensity.density, fontScale = 1f),
                    ) {
                        WinNativeTheme {
                            DirectoryPickerDialogContent(
                                title = title,
                                initialDir = initialDir,
                                roots = roots,
                                mode = SelectionMode.FILE,
                                allowedExtensions = normalizedExtensions,
                                onDismiss = { dialog.dismiss() },
                                onSelect = { path ->
                                    onSelected(path)
                                    dialog.dismiss()
                                },
                            )
                        }
                    }
                }
            }

        dialog.setContentView(composeView)
        dialog.show()
        dialog.window?.apply {
            val dm = activity.resources.displayMetrics
            val screenWidthDp = dm.widthPixels / dm.density
            if (screenWidthDp < 600f) {
                setLayout((dm.widthPixels * 0.96f).toInt(), (dm.heightPixels * 0.94f).toInt())
            } else {
                setLayout((dm.widthPixels * 0.88f).toInt(), (dm.heightPixels * 0.92f).toInt())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val params = attributes
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.blurBehindRadius = 12
                attributes = params
            }
        }
    }

    @Composable
    private fun DirectoryPickerDialogContent(
        title: String,
        initialDir: File,
        roots: List<File>,
        mode: SelectionMode,
        allowedExtensions: Set<String>,
        onDismiss: () -> Unit,
        onSelect: (String) -> Unit,
    ) {
        var currentDir by remember(initialDir.absolutePath) { mutableStateOf(initialDir) }
        var selectedFile by remember(currentDir.absolutePath) { mutableStateOf<File?>(null) }
        var rootsExpanded by remember { mutableStateOf(false) }
        val upLabel = activityString(R.string.saves_import_export_up_directory)
        val entries = remember(currentDir.absolutePath, upLabel, mode, allowedExtensions) {
            buildEntries(currentDir, upLabel, mode, allowedExtensions)
        }
        val folderCount = remember(entries) { entries.count { !it.isParent } }
        val selectableFileCount = remember(entries) { entries.count { it.isSelectableFile } }
        val folderOnlyCount = remember(entries) { entries.count { !it.isParent && !it.isSelectableFile } }
        val footerTitle =
            if (mode == SelectionMode.DIRECTORY) {
                activityString(R.string.common_ui_select_folder)
            } else {
                title
            }
        val footerSubtitle =
            if (mode == SelectionMode.DIRECTORY) {
                title
                    .takeUnless { it.equals(footerTitle, ignoreCase = true) }
                    ?: activityString(R.string.common_ui_browse_local_folders_directly)
            } else {
                selectedFile?.absolutePath ?: currentDir.absolutePath
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isLandscape = maxWidth > maxHeight
            val view = LocalView.current
            var hasLeftDisplayCutout by remember { mutableStateOf(false) }
            DisposableEffect(view) {
                fun updateLeftDisplayCutout(insets: android.view.WindowInsets?) {
                    hasLeftDisplayCutout =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                            (insets?.displayCutout?.safeInsetLeft ?: 0) > 0
                }

                updateLeftDisplayCutout(view.rootWindowInsets)
                view.setOnApplyWindowInsetsListener { _, insets ->
                    updateLeftDisplayCutout(insets)
                    insets
                }
                view.requestApplyInsets()
                onDispose {
                    view.setOnApplyWindowInsetsListener(null)
                }
            }
            val startPadding =
                if (hasLeftDisplayCutout) {
                    DialogCutoutStartPadding
                } else {
                    DialogHorizontalPadding
                }
            val folderListMinHeight =
                (maxHeight * if (isLandscape) 0.44f else 0.5f)
                    .coerceIn(240.dp, 420.dp)

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 360.dp),
                shape = RoundedCornerShape(16.dp),
                color = CardDark,
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                start = startPadding,
                                top = 14.dp,
                                end = DialogHorizontalPadding,
                                bottom = 14.dp,
                            ),
                ) {
                    Text(
                        text = activityString(R.string.common_ui_current_folder),
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = CurrentPathHorizontalPadding),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(4.dp))

                    CurrentPathCard(path = currentDir.absolutePath)

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        RootSelector(
                            roots = roots,
                            currentDir = currentDir,
                            expanded = rootsExpanded,
                            onExpandedChange = { rootsExpanded = it },
                            onRootSelected = {
                                currentDir = it
                                rootsExpanded = false
                            },
                            modifier = Modifier.widthIn(max = 172.dp),
                        )

                        Text(
                            text =
                                if (mode == SelectionMode.DIRECTORY) {
                                    activityPlural(R.plurals.common_ui_folder_count, folderCount)
                                } else {
                                    "$folderOnlyCount folders / $selectableFileCount files"
                                },
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = FolderGridCardPadding),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    androidx.compose.foundation.layout.Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .heightIn(min = folderListMinHeight)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardSubtle)
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = FolderGridCardPadding, vertical = FolderGridCardPadding),
                    ) {
                        if (entries.isEmpty()) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = activityString(R.string.common_ui_no_folders_available_here),
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                modifier = Modifier.fillMaxWidth(),
                                columns = GridCells.Adaptive(minSize = if (isLandscape) 150.dp else 140.dp),
                                contentPadding = PaddingValues(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(entries, key = { entry ->
                                    entry.target.absolutePath + entry.isParent + entry.isSelectableFile
                                }) { entry ->
                                    EntryTile(
                                        entry = entry,
                                        selected = selectedFile?.absolutePath == entry.target.absolutePath,
                                        onClick = {
                                            if (entry.isSelectableFile) {
                                                selectedFile = entry.target
                                            } else {
                                                currentDir = entry.target
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = CardBorder, thickness = 1.dp)
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FooterInfo(
                            title = footerTitle,
                            subtitle = footerSubtitle,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            FooterActionButton(
                                label = activityString(R.string.common_ui_cancel),
                                textColor = TextPrimary,
                                modifier = Modifier.height(FooterButtonHeight),
                                onClick = onDismiss,
                            )
                            FooterActionButton(
                                label = activityString(R.string.common_ui_ok),
                                textColor = Accent,
                                modifier = Modifier.height(FooterButtonHeight),
                                backgroundColor = Accent.copy(alpha = 0.12f),
                                borderColor = Accent.copy(alpha = 0.3f),
                                onClick = {
                                    val selectedPath =
                                        if (mode == SelectionMode.FILE) {
                                            selectedFile?.absolutePath ?: return@FooterActionButton
                                        } else {
                                            currentDir.absolutePath
                                        }
                                    onSelect(selectedPath)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FooterInfo(
        title: String,
        subtitle: String,
        modifier: Modifier = Modifier,
    ) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(IconBoxBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    private fun CurrentPathCard(path: String) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = CurrentPathHorizontalPadding, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = path,
                color = TextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun RootSelector(
        roots: List<File>,
        currentDir: File,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        onRootSelected: (File) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Box(modifier = modifier) {
            SecondaryActionChip(
                label = activityString(R.string.common_ui_storage_roots),
                icon = Icons.Outlined.Storage,
                trailing = Icons.Outlined.KeyboardArrowDown,
                onClick = { onExpandedChange(true) },
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                shape = RoundedCornerShape(10.dp),
                containerColor = Color(0xFF24243B),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.widthIn(max = 420.dp),
            ) {
                @Suppress("DEPRECATION")
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    Column(
                        modifier =
                            Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        roots.forEach { root ->
                            val selected = isSameOrDescendant(currentDir, root)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = root.absolutePath,
                                        color = if (selected) Accent else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = { onRootSelected(root) },
                                modifier =
                                    Modifier.background(
                                        if (selected) Accent.copy(alpha = 0.08f) else Color.Transparent,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EntryTile(
        entry: Entry,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            selected -> Accent.copy(alpha = 0.16f)
                            entry.isParent -> Accent.copy(alpha = 0.1f)
                            else -> CardDark
                        },
                    )
                    .border(
                        width = 1.dp,
                        color =
                            when {
                                selected -> Accent.copy(alpha = 0.45f)
                                entry.isParent -> Accent.copy(alpha = 0.24f)
                                else -> CardBorder
                            },
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ).padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector =
                        when {
                            entry.isParent -> Icons.Outlined.KeyboardArrowUp
                            entry.isSelectableFile -> Icons.Outlined.Description
                            else -> Icons.Outlined.Folder
                        },
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = entry.label,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    private fun SecondaryActionChip(
        label: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        Row(
            modifier =
                modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(WinNativePanel)
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = trailing,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }

    @Composable
    private fun FooterActionButton(
        label: String,
        textColor: Color,
        modifier: Modifier = Modifier,
        backgroundColor: Color = WinNativePanel,
        borderColor: Color = CardBorder,
        onClick: () -> Unit,
    ) {
        Box(
                modifier =
                    modifier
                    .widthIn(min = 74.dp)
                    .height(FooterButtonHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(backgroundColor)
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    private fun buildEntries(
        currentDir: File,
        upLabel: String,
        mode: SelectionMode,
        allowedExtensions: Set<String>,
    ): List<Entry> {
        val entries = mutableListOf<Entry>()

        currentDir.parentFile
            ?.takeIf { canBrowse(it) }
            ?.let {
                entries += Entry(label = upLabel, target = it, isParent = true)
            }

        val children =
            currentDir
                .listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory && canBrowse(it) }
                ?.sortedWith(compareBy<File>({ it.isHidden }, { it.name.lowercase(Locale.getDefault()) }))
                ?.toList()
                ?: emptyList()

        for (child in children) {
            entries += Entry(label = child.name.ifBlank { child.absolutePath }, target = child)
        }

        if (mode == SelectionMode.FILE) {
            val files =
                currentDir
                    .listFiles()
                    ?.asSequence()
                    ?.filter { it.isFile && canSelectFile(it, allowedExtensions) }
                    ?.sortedWith(compareBy<File>({ it.isHidden }, { it.name.lowercase(Locale.getDefault()) }))
                    ?.toList()
                    ?: emptyList()

            for (file in files) {
                entries += Entry(
                    label = file.name.ifBlank { file.absolutePath },
                    target = file,
                    isSelectableFile = true,
                )
            }
        }

        return entries
    }

    private fun resolveInitialDirectory(
        initialPath: String?,
        roots: List<File>,
    ): File {
        val requested =
            initialPath
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.let { if (it.isDirectory) it else it.parentFile }
                ?.takeIf { canBrowse(it) }
        if (requested != null) return requested

        return roots.firstOrNull() ?: Environment.getExternalStorageDirectory()
    }

    private fun buildRootDirectories(activity: Activity): List<File> {
        val roots = linkedMapOf<String, File>()
        val storageManager = activity.getSystemService(StorageManager::class.java)
        val externalFilesDirs =
            activity.getExternalFilesDirs(null)
                .filterNotNull()
                .filter { isReadableMountedState(Environment.getExternalStorageState(it)) }

        fun addRoot(dir: File?) {
            val normalized = dir?.absoluteFile ?: return
            if (!canBrowse(normalized)) return
            roots.putIfAbsent(normalized.absolutePath, normalized)
        }

        fun addResolvedRoot(
            root: File?,
            browseSeed: File? = null,
        ) {
            resolveBrowsableRoot(root, browseSeed)?.let(::addRoot)
        }

        addResolvedRoot(Environment.getExternalStorageDirectory())

        storageManager?.storageVolumes.orEmpty()
            .filter(::isReadableVolume)
            .forEach { volume ->
                val volumeExternalDirs =
                    externalFilesDirs.filter { dir ->
                        belongsToVolume(storageManager, dir, volume)
                    }

                val volumeCandidates =
                    buildList {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            add(volume.directory)
                        }
                        volume.uuid?.let { uuid ->
                            add(File("/storage/$uuid"))
                            add(File("/mnt/media_rw/$uuid"))
                        }
                        volumeExternalDirs.forEach { dir ->
                            add(resolveStorageRootFromExternalFilesDir(dir))
                        }
                    }

                var resolvedRoot: File? = null
                for (candidate in volumeCandidates) {
                    resolvedRoot =
                        volumeExternalDirs
                            .asSequence()
                            .mapNotNull { dir -> resolveBrowsableRoot(candidate, dir) }
                            .firstOrNull()
                            ?: resolveBrowsableRoot(candidate)
                    if (resolvedRoot != null) {
                        break
                    }
                }

                if (resolvedRoot == null) {
                    resolvedRoot =
                        volumeExternalDirs
                            .asSequence()
                            .mapNotNull(::resolveFallbackBrowsableRootFromExternalFilesDir)
                            .firstOrNull()
                }

                addRoot(resolvedRoot)
            }

        File("/storage").listFiles().orEmpty().forEach { child ->
            if (!child.isDirectory || child.name == "self") return@forEach
            if (child.name == "emulated") {
                addResolvedRoot(File(child, "0"))
            } else {
                addResolvedRoot(child)
            }
        }

        externalFilesDirs
            .mapNotNull(::resolveStorageRootFromExternalFilesDir)
            .forEach { root ->
                addResolvedRoot(root)
            }

        externalFilesDirs
            .mapNotNull(::resolveFallbackBrowsableRootFromExternalFilesDir)
            .forEach(::addRoot)

        return roots.values.toList()
    }

    private fun resolveStorageRootFromExternalFilesDir(dir: File): File? {
        val absolute = dir.absoluteFile
        val androidDir =
            generateSequence(absolute) { it.parentFile }
                .firstOrNull { it.name.equals("Android", ignoreCase = true) }
        if (androidDir?.parentFile != null) {
            return androidDir.parentFile
        }

        return generateSequence(absolute) { it.parentFile }
            .drop(4)
            .firstOrNull()
    }

    private fun resolveBrowsableRoot(
        root: File?,
        browseSeed: File? = null,
    ): File? {
        val normalizedRoot = root?.absoluteFile ?: return null
        if (canBrowse(normalizedRoot)) return normalizedRoot
        return highestBrowsablePathWithinRoot(normalizedRoot, browseSeed)
    }

    private fun resolveFallbackBrowsableRootFromExternalFilesDir(dir: File): File? {
        val absolute = dir.absoluteFile
        val resolvedRoot = resolveStorageRootFromExternalFilesDir(absolute)
        return when {
            resolvedRoot != null -> highestBrowsablePathWithinRoot(resolvedRoot, absolute)
            canBrowse(absolute) -> absolute
            else ->
                generateSequence(absolute.parentFile) { it.parentFile }
                    .firstOrNull(::canBrowse)
        }
    }

    private fun highestBrowsablePathWithinRoot(
        root: File,
        browseSeed: File?,
    ): File? {
        val normalizedRoot = root.absoluteFile
        val normalizedSeed = browseSeed?.absoluteFile ?: return null
        var current: File? = normalizedSeed
        var best: File? = null
        while (current != null && isSameOrDescendant(current, normalizedRoot)) {
            if (canBrowse(current)) {
                best = current
            }
            current = current.parentFile
        }
        return best
    }

    private fun belongsToVolume(
        storageManager: StorageManager,
        dir: File,
        volume: StorageVolume,
    ): Boolean {
        val dirVolume = storageManager.getStorageVolume(dir) ?: return false
        if (dirVolume.isPrimary != volume.isPrimary) return false

        val dirUuid = dirVolume.uuid
        val volumeUuid = volume.uuid
        return if (!dirUuid.isNullOrBlank() || !volumeUuid.isNullOrBlank()) {
            dirUuid == volumeUuid
        } else {
            true
        }
    }

    private fun isReadableVolume(volume: StorageVolume): Boolean = isReadableMountedState(volume.state)

    private fun isReadableMountedState(state: String?): Boolean =
        state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY

    private fun isSameOrDescendant(
        candidate: File,
        root: File,
    ): Boolean {
        val candidatePath = candidate.absolutePath.trimEnd('/')
        val rootPath = root.absolutePath.trimEnd('/')
        return candidatePath == rootPath || candidatePath.startsWith("$rootPath/")
    }

    private fun canBrowse(dir: File?): Boolean = dir != null && dir.exists() && dir.isDirectory && dir.canRead()

    private fun canSelectFile(
        file: File,
        allowedExtensions: Set<String>,
    ): Boolean {
        if (!file.canRead()) return false
        if (allowedExtensions.isEmpty()) return true
        return allowedExtensions.contains(file.extension.lowercase(Locale.getDefault()))
    }

    private fun ensureAllFilesAccess(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            return true
        }

        AppUtils.showToast(
            activity,
            activity.getString(R.string.common_ui_grant_all_files_access_browse),
            Toast.LENGTH_LONG,
        )

        val intent =
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
            } catch (_: Exception) {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
            }
        activity.startActivity(intent)
        return false
    }
    @Composable
    private fun activityString(resId: Int): String = androidx.compose.ui.res.stringResource(id = resId)

    @Composable
    private fun activityPlural(
        resId: Int,
        quantity: Int,
    ): String = androidx.compose.ui.res.pluralStringResource(id = resId, count = quantity, quantity)
}
