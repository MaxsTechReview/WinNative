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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeAccent
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeFontFamily
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativePanel
import com.winlator.cmod.shared.theme.WinNativeSurface
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary
import com.winlator.cmod.shared.theme.WinNativeTheme
import java.io.File
import java.util.Locale

object DirectoryPickerDialog {
    private const val ContentEnterMillis = 220
    private val ContentEnterEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val FooterButtonHeight = 36.dp
    private val DialogHorizontalPadding = 18.dp
    private val DialogCutoutStartPadding = 14.dp
    private val CurrentPathHorizontalPadding = 10.dp
    private val FolderGridCardPadding = 6.dp
    private val BgDark = WinNativeBackground
    private val CardDark = WinNativeSurface
    private val CardBorder = WinNativeOutline
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
        dimBackground: Boolean = true,
        dimAmount: Float = 0.30f,
        preserveBackdropBlur: Boolean = false,
        onSelected: (String) -> Unit,
    ) {
        showPicker(
            activity = activity,
            initialPath = initialPath,
            title = title,
            mode = SelectionMode.DIRECTORY,
            allowedExtensions = emptySet(),
            dimBackground = dimBackground,
            dimAmount = dimAmount,
            preserveBackdropBlur = preserveBackdropBlur,
            onSelected = onSelected,
        )
    }

    fun showFile(
        activity: Activity,
        initialPath: String? = null,
        title: String = activity.getString(R.string.common_ui_open_file),
        allowedExtensions: Set<String> = emptySet(),
        dimBackground: Boolean = true,
        dimAmount: Float = 0.30f,
        preserveBackdropBlur: Boolean = false,
        onSelected: (String) -> Unit,
    ) {
        showPicker(
            activity = activity,
            initialPath = initialPath,
            title = title,
            mode = SelectionMode.FILE,
            allowedExtensions = normalizeAllowedExtensions(allowedExtensions),
            dimBackground = dimBackground,
            dimAmount = dimAmount,
            preserveBackdropBlur = preserveBackdropBlur,
            onSelected = onSelected,
        )
    }

    private fun showPicker(
        activity: Activity,
        initialPath: String?,
        title: String,
        mode: SelectionMode,
        allowedExtensions: Set<String>,
        dimBackground: Boolean,
        dimAmount: Float,
        preserveBackdropBlur: Boolean,
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
                    setWindowAnimations(0)
                    if (dimBackground) {
                        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                        setDimAmount(dimAmount.coerceIn(0f, 1f))
                    } else {
                        clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                        setDimAmount(0f)
                    }
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
                            CompositionLocalProvider(
                                LocalTextStyle provides
                                    LocalTextStyle.current.merge(
                                        TextStyle(fontFamily = WinNativeFontFamily),
                                    ),
                            ) {
                                fun dismissPicker() {
                                    dialog.dismiss()
                                }

                                DirectoryPickerDialogContent(
                                    title = title,
                                    initialDir = initialDir,
                                    roots = roots,
                                    mode = mode,
                                    allowedExtensions = allowedExtensions,
                                    onDismiss = ::dismissPicker,
                                    onSelect = { path ->
                                        onSelected(path)
                                        dismissPicker()
                                    },
                                )
                            }
                        }
                    }
                }
            }

        dialog.setContentView(composeView)
        dialog.show()
        applyDialogWindowSizing(activity, dialog.window, preserveBackdropBlur)
    }

    private fun normalizeAllowedExtensions(allowedExtensions: Set<String>): Set<String> =
        allowedExtensions
            .map { it.trim().trimStart('.').lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun applyDialogWindowSizing(
        activity: Activity,
        window: Window?,
        preserveBackdropBlur: Boolean,
    ) {
        window?.apply {
            setWindowAnimations(0)
            val dm = activity.resources.displayMetrics
            val screenWidthDp = dm.widthPixels / dm.density
            val widthFraction = (0.82f + 84f / screenWidthDp).coerceIn(0.88f, 0.96f)
            val heightFraction = (0.90f + 24f / screenWidthDp).coerceIn(0.92f, 0.94f)

            setLayout((dm.widthPixels * widthFraction).toInt(), (dm.heightPixels * heightFraction).toInt())
            if (preserveBackdropBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val params = attributes
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.blurBehindRadius = 10
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
        val entryCountLabel =
            if (mode == SelectionMode.DIRECTORY) {
                activityPlural(R.plurals.common_ui_folder_count, folderCount)
            } else {
                "${activityPlural(R.plurals.common_ui_folder_count, folderOnlyCount)} / " +
                    activityPlural(R.plurals.common_ui_file_count, selectableFileCount)
        }
        var contentVisible by remember { mutableStateOf(false) }
        val contentAlpha by animateFloatAsState(
            targetValue = if (contentVisible) 1f else 0f,
            animationSpec =
                tween(
                    durationMillis = ContentEnterMillis,
                    easing = ContentEnterEasing,
                ),
            label = "directoryPickerContentFade",
        )
        val contentScale by animateFloatAsState(
            targetValue = if (contentVisible) 1f else 0.972f,
            animationSpec =
                tween(
                    durationMillis = ContentEnterMillis,
                    easing = ContentEnterEasing,
                ),
            label = "directoryPickerContentScale",
        )
        val density = LocalDensity.current
        val hiddenTranslationY = with(density) { 8.dp.toPx() }
        val contentTranslationY by animateFloatAsState(
            targetValue = if (contentVisible) 0f else hiddenTranslationY,
            animationSpec =
                tween(
                    durationMillis = ContentEnterMillis,
                    easing = ContentEnterEasing,
                ),
            label = "directoryPickerContentOffset",
        )
        LaunchedEffect(Unit) {
            contentVisible = true
        }

        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = contentAlpha
                        scaleX = contentScale
                        scaleY = contentScale
                        translationY = contentTranslationY
                    }
                    .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
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
                (maxHeight * 0.48f)
                    .coerceIn(240.dp, 420.dp)
            val entryCountMaxWidth = (maxWidth * 0.42f).coerceIn(128.dp, 220.dp)
            val folderGridMinSize = (maxWidth * 0.22f).coerceIn(140.dp, 150.dp)

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
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CurrentPathHorizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = activityString(R.string.common_ui_current_folder),
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = entryCountLabel,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.widthIn(max = entryCountMaxWidth),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    CurrentPathCard(path = currentDir.absolutePath)

                    Spacer(Modifier.height(8.dp))

                    androidx.compose.foundation.layout.Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .heightIn(min = folderListMinHeight)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgDark)
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
                                columns = GridCells.Adaptive(minSize = folderGridMinSize),
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

                    val rootSelector: @Composable (Modifier) -> Unit = { modifier ->
                        RootSelector(
                            roots = roots,
                            currentDir = currentDir,
                            expanded = rootsExpanded,
                            onExpandedChange = { rootsExpanded = it },
                            onRootSelected = {
                                currentDir = it
                                rootsExpanded = false
                            },
                            modifier = modifier,
                        )
                    }
                    val footerActions: @Composable () -> Unit = {
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
                        rootSelector(Modifier.widthIn(min = 158.dp, max = 182.dp))
                        footerActions()
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
        val chevronRotation by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = tween(durationMillis = 180),
            label = "storageRootChevronRotation",
        )

        Box(modifier = modifier) {
            SecondaryActionChip(
                label = activityString(R.string.common_ui_storage_roots),
                icon = Icons.Outlined.Storage,
                trailing = Icons.Outlined.KeyboardArrowDown,
                trailingRotationDegrees = chevronRotation,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onExpandedChange(true) },
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                offset = DpOffset(x = 0.dp, y = (-8).dp),
                shape = RoundedCornerShape(10.dp),
                containerColor = Color(0xFF24243B),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.widthIn(min = 220.dp, max = 420.dp),
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
        trailingRotationDegrees: Float = 0f,
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
                    modifier =
                        Modifier
                            .size(15.dp)
                            .rotate(trailingRotationDegrees),
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
                .orEmpty()
                .asSequence()
                .sortedWith(compareBy<File>({ it.isHidden }, { it.name.lowercase(Locale.ROOT) }))
                .toList()

        children
            .filter { it.isDirectory && canBrowse(it) }
            .forEach { child ->
                entries += Entry(label = entryLabel(child), target = child)
            }

        if (mode == SelectionMode.FILE) {
            children
                .filter { it.isFile && canSelectFile(it, allowedExtensions) }
                .forEach { file ->
                    entries += Entry(
                        label = entryLabel(file),
                        target = file,
                        isSelectableFile = true,
                    )
                }
        }

        return entries
    }

    private fun entryLabel(file: File): String = file.name.ifBlank { file.absolutePath }

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

    private fun buildRootDirectories(activity: Activity): List<File> =
        StoragePathUtils.buildBrowsableStorageRoots(activity)

    private fun isSameOrDescendant(
        candidate: File,
        root: File,
    ): Boolean {
        return StoragePathUtils.isSameOrDescendant(candidate, root)
    }

    private fun canBrowse(dir: File?): Boolean = StoragePathUtils.canBrowse(dir)

    private fun canSelectFile(
        file: File,
        allowedExtensions: Set<String>,
    ): Boolean {
        if (!file.canRead()) return false
        if (allowedExtensions.isEmpty()) return true
        return allowedExtensions.contains(file.extension.lowercase(Locale.ROOT))
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

        val appSpecificIntent =
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
        val appDetailsIntent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
        val allFilesIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

        if (!tryStartActivity(activity, appSpecificIntent) &&
            !tryStartActivity(activity, appDetailsIntent)
        ) {
            tryStartActivity(activity, allFilesIntent)
        }
        return false
    }

    private fun tryStartActivity(
        activity: Activity,
        intent: Intent,
    ): Boolean =
        try {
            activity.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }

    @Composable
    private fun activityString(resId: Int): String = androidx.compose.ui.res.stringResource(id = resId)

    @Composable
    private fun activityPlural(
        resId: Int,
        quantity: Int,
    ): String = androidx.compose.ui.res.pluralStringResource(id = resId, count = quantity, quantity)
}
