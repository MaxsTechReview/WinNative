package com.winlator.cmod.feature.retro

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armsx2.runtime.MainActivityRuntime
import com.winlator.cmod.R
import com.winlator.cmod.shared.ui.nav.LocalPaneNav
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry
import com.winlator.cmod.shared.ui.nav.bindPaneNav
import com.winlator.cmod.shared.ui.nav.paneNavItem
import java.io.File
import kotlinx.coroutines.launch
import kr.co.iefriends.pcsx2.NativeApp

internal fun memcardDir(context: Context): File =
    File(MainActivityRuntime.assetCopyRoot(context), "memcards").apply { mkdirs() }

internal fun listMemcards(context: Context): List<File> =
    memcardDir(context).listFiles().orEmpty()
        .filter { it.isFile && it.extension.equals("ps2", true) }
        .sortedBy { it.name.lowercase() }

internal fun humanSize(bytes: Long): String =
    when {
        bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

internal fun queryName(context: Context, uri: android.net.Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

internal fun uniqueMemcard(dir: File, name: String): File {
    dir.mkdirs()
    val base = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    var target = File(dir, name)
    var i = 2
    while (target.exists()) target = File(dir, "$base-$i$ext").also { i++ }
    return target
}

@Composable
fun Ps2CheatsScreen(
    context: Context,
    onBack: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var crc by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<com.armsx2.PatchRepo.Entry>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var patchEntries by remember { mutableStateOf<List<com.armsx2.PatchRepo.Entry>>(emptyList()) }
    var selectedPatches by remember { mutableStateOf<Set<String>>(emptySet()) }
    var repoCheatNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var repoPatchNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dnasEntries by remember { mutableStateOf<List<Ps2DnasBypass.BypassEntry>>(emptyList()) }
    var dnasGlobalOn by remember { mutableStateOf(true) }
    var dnasDisabled by remember { mutableStateOf<Set<String>>(emptySet()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        data class LocalCheats(
            val s: String,
            val c: String?,
            val stagedC: List<com.armsx2.PatchRepo.Entry>,
            val stagedP: List<com.armsx2.PatchRepo.Entry>,
            val dnas: List<Ps2DnasBypass.BypassEntry>,
            val globalOn: Boolean,
            val disabled: Set<String>,
        )
        val local = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val s = runCatching { NativeApp.getGameSerial() }.getOrNull()?.takeIf { it.isNotBlank() }
            val c = runCatching { NativeApp.getGameCRC() }.getOrNull()?.takeIf { it.length == 8 }
            if (s == null) null
            else {
                val stagedC = Ps2CheatStaging.read(context, s, false)
                val stagedP = Ps2CheatStaging.read(context, s, true)
                val dnas = Ps2DnasBypass.bypassEntries(context, s).filter { it.auto }
                val globalOn = context.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE).getBoolean(Ps2DnasBypass.PREF, true)
                val disabled = Ps2DnasBypass.ensureSingleDnasEnabled(context, s, dnas.map { it.name }.toSet())
                LocalCheats(s, c, stagedC, stagedP, dnas, globalOn, disabled)
            }
        }
        if (local == null) {
            status = context.getString(R.string.retro_scr_no_game_serial)
            loading = false
        } else {
            serial = local.s
            crc = local.c.orEmpty()
            title = local.s
            entries = local.stagedC
            patchEntries = local.stagedP
            selected = local.stagedC.map { it.name }.toSet()
            selectedPatches = local.stagedP.map { it.name }.toSet()
            dnasEntries = local.dnas
            dnasGlobalOn = local.globalOn
            dnasDisabled = local.disabled
            loading = false
            val remote = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    if (local.c != null) com.armsx2.PatchRepo.fetchForGame(local.s, local.c)
                    else com.armsx2.PatchRepo.fetchForSerial(local.s)
                }.getOrNull()
            }
            if (remote != null) {
                title = remote.gametitle.takeIf { it.isNotBlank() } ?: local.s
                status = remote.error.orEmpty()
                val repoCheats = remote.entries.filter { it.source != "patches" }
                val repoPatches = remote.entries.filter { it.source == "patches" }
                repoCheatNames = repoCheats.map { it.name }.toSet()
                repoPatchNames = repoPatches.map { it.name }.toSet()
                entries = repoCheats + local.stagedC.filter { st -> repoCheats.none { it.name == st.name } }
                patchEntries = repoPatches + local.stagedP.filter { st -> repoPatches.none { it.name == st.name } }
            }
        }
    }

    fun persist() {
        if (serial.isBlank()) return
        Ps2CheatStaging.write(context, serial, false, serial, entries.filter { it.name in selected })
        Ps2CheatStaging.write(context, serial, true, serial, patchEntries.filter { it.name in selectedPatches })
    }

    fun apply() {
        persist()
        val s = serial
        val c = crc
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { Ps2CheatStaging.applyAll(context, s, c); true }.getOrDefault(false)
            }
            status = if (ok) {
                context.getString(R.string.retro_scr_applied_both, selected.size, selectedPatches.size + dnasEntries.count { dnasGlobalOn && it.name !in dnasDisabled })
            } else {
                context.getString(R.string.retro_scr_couldnt_apply_cheats)
            }
        }
    }

    fun toggleDnas(name: String, currentlyOn: Boolean) {
        if (serial.isBlank()) return
        if (currentlyOn) {
            dnasDisabled = dnasDisabled + name
        } else {
            if (!dnasGlobalOn) { Ps2DnasBypass.setEnabled(context, true); dnasGlobalOn = true }
            val all = dnasEntries.map { it.name }.toSet()
            dnasDisabled = all - name
        }
        Ps2DnasBypass.setDisabledNames(context, serial, dnasDisabled)
    }

    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newCodes by remember { mutableStateOf("") }
    var newIsPatch by remember { mutableStateOf(false) }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                }
                val imported = if (text != null) parsePnachFile(text, if (newIsPatch) "patches" else "custom") else emptyList()
                if (imported.isNotEmpty()) {
                    if (newIsPatch) {
                        patchEntries = patchEntries.filterNot { e -> imported.any { it.name == e.name } } + imported
                        selectedPatches = selectedPatches + imported.map { it.name }
                    } else {
                        entries = entries.filterNot { e -> imported.any { it.name == e.name } } + imported
                        selected = selected + imported.map { it.name }
                    }
                    persist()
                    status = ""
                    showAdd = false
                } else {
                    status = context.getString(R.string.retro_scr_cheat_invalid)
                }
            }
        }
    }

    Ps2WindowedScaffold(title = stringResource(R.string.retro_scr_cheats), onBack = onBack, header = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val openAdd = { newName = ""; newCodes = ""; newIsPatch = false; showAdd = true }
            OutlinedButton(
                onClick = openAdd,
                modifier = Modifier.paneNavItem(onActivate = openAdd),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.width(18.dp).height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.retro_scr_add))
            }
            if (!loading && (entries.isNotEmpty() || patchEntries.isNotEmpty() || serial.isNotBlank())) {
                OutlinedButton(onClick = { apply() }, modifier = Modifier.paneNavItem(onActivate = { apply() })) {
                    Text(stringResource(R.string.retro_scr_apply))
                }
            }
        }
    }) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            entries.isEmpty() && patchEntries.isEmpty() && dnasEntries.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(status.ifBlank { stringResource(R.string.retro_scr_no_cheats_or_patches) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Column(Modifier.fillMaxSize()) {
                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (dnasEntries.isNotEmpty()) {
                        item(key = "hdr_dnas") { Ps2SectionHeader(stringResource(R.string.retro_gs_dnas_group)) }
                        items(dnasEntries, key = { "dnas_" + it.name }) { e ->
                            val on = dnasGlobalOn && e.name !in dnasDisabled
                            Ps2CheatRow(
                                com.armsx2.PatchRepo.Entry(e.name, stringResource(R.string.retro_gs_dnas_entry_desc), e.body, "dnas"),
                                on, isPatch = true, onToggle = { toggleDnas(e.name, on) },
                            )
                        }
                    }
                    if (entries.isNotEmpty()) {
                        item(key = "hdr_cheats") { Ps2SectionHeader(stringResource(R.string.retro_scr_cheats_section)) }
                        items(entries, key = { "cheat_" + it.name }) { entry ->
                            val checked = entry.name in selected
                            val toggle = {
                                selected = if (entry.name in selected) selected - entry.name else selected + entry.name
                                persist()
                            }
                            val isCustom = entry.name !in repoCheatNames
                            Ps2CheatRow(
                                entry, checked, isPatch = false, onToggle = toggle,
                                onDelete = if (isCustom) {
                                    {
                                        entries = entries.filterNot { it.name == entry.name }
                                        selected = selected - entry.name
                                        persist()
                                    }
                                } else null,
                            )
                        }
                    }
                    if (patchEntries.isNotEmpty()) {
                        item(key = "hdr_patches") { Ps2SectionHeader(stringResource(R.string.retro_scr_patches_section)) }
                        items(patchEntries, key = { "patch_" + it.name }) { entry ->
                            val checked = entry.name in selectedPatches
                            val toggle = {
                                selectedPatches = if (entry.name in selectedPatches) selectedPatches - entry.name else selectedPatches + entry.name
                                persist()
                            }
                            val isCustom = entry.name !in repoPatchNames
                            Ps2CheatRow(
                                entry, checked, isPatch = true, onToggle = toggle,
                                onDelete = if (isCustom) {
                                    {
                                        patchEntries = patchEntries.filterNot { it.name == entry.name }
                                        selectedPatches = selectedPatches - entry.name
                                        persist()
                                    }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.retro_scr_add_cheat)) },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        val selCheat = { newIsPatch = false }
                        val selPatch = { newIsPatch = true }
                        OutlinedButton(
                            onClick = selCheat,
                            modifier = Modifier.weight(1f).paneNavItem(onActivate = selCheat),
                            colors = if (!newIsPatch)
                                androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                        ) { Text(stringResource(R.string.retro_scr_target_cheat)) }
                        OutlinedButton(
                            onClick = selPatch,
                            modifier = Modifier.weight(1f).paneNavItem(onActivate = selPatch),
                            colors = if (newIsPatch)
                                androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                        ) { Text(stringResource(R.string.retro_scr_target_patch)) }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.retro_scr_cheat_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(
                        value = newCodes,
                        onValueChange = { newCodes = it },
                        label = { Text(stringResource(R.string.retro_scr_cheat_codes)) },
                        placeholder = { Text("2021A268 00000000") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().paneNavItem(onActivate = { importLauncher.launch(arrayOf("*/*")) }),
                    ) { Text(stringResource(R.string.retro_scr_import_from_file)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val entry = buildCustomPnachEntry(context, newName, newCodes, if (newIsPatch) "patches" else "custom")
                    if (entry != null) {
                        if (newIsPatch) {
                            patchEntries = patchEntries.filterNot { it.name == entry.name } + entry
                            selectedPatches = selectedPatches + entry.name
                        } else {
                            entries = entries.filterNot { it.name == entry.name } + entry
                            selected = selected + entry.name
                        }
                        persist()
                        status = ""
                        showAdd = false
                    } else {
                        status = context.getString(R.string.retro_scr_cheat_invalid)
                    }
                }) { Text(stringResource(R.string.retro_scr_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.retro_scr_cancel)) }
            },
        )
    }
}

internal fun buildCustomPnachEntry(context: Context, name: String, codes: String, source: String): com.armsx2.PatchRepo.Entry? {
    val lines =
        codes.lineSequence().mapNotNull { raw ->
            val t = raw.trim()
            when {
                t.isEmpty() || t.startsWith("//") -> null
                t.startsWith("patch=") -> t
                else -> {
                    val parts = t.split(Regex("\\s+"))
                    if (parts.size == 2 && parts[0].length == 8 && parts[1].length == 8 &&
                        parts[0].all { it.isDigit() || it.uppercaseChar() in 'A'..'F' } &&
                        parts[1].all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
                    ) {
                        "patch=1,EE,${parts[0].uppercase()},extended,${parts[1].uppercase()}"
                    } else {
                        null
                    }
                }
            }
        }.toList()
    if (name.isBlank() || lines.isEmpty()) return null
    val body = buildString {
        append("[").append(name.trim()).append("]\n")
        lines.forEach { append(it).append("\n") }
    }
    val desc = context.getString(if (source == "patches") R.string.retro_scr_custom_patch else R.string.retro_scr_custom_cheat)
    return com.armsx2.PatchRepo.Entry(name.trim(), desc, body, source)
}

internal fun parsePnachFile(text: String, source: String): List<com.armsx2.PatchRepo.Entry> =
    runCatching {
        com.armsx2.PatchRepo.parseInstalled(text, if (source == "patches") "patches" else "cheats").second
            .map { com.armsx2.PatchRepo.Entry(it.name, it.description, it.body, source) }
    }.getOrDefault(emptyList())

@Composable
internal fun Ps2SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
internal fun Ps2CheatRow(
    entry: com.armsx2.PatchRepo.Entry,
    checked: Boolean,
    isPatch: Boolean,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().paneNavItem(cornerRadius = 12.dp, onActivate = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (checked) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isPatch) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ) {
                            Text(
                                stringResource(R.string.retro_scr_patch_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        entry.name,
                        fontWeight = FontWeight.SemiBold,
                        color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (entry.description.isNotBlank()) {
                    Text(
                        entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (checked) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (onDelete != null) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.paneNavItem(onActivate = onDelete)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.retro_scr_delete),
                        tint = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun Ps2AchievementsScreen(
    context: Context,
    romPath: String,
    launchGameName: String,
    onBack: () -> Unit,
) {
    val gameName =
        remember(romPath, launchGameName) {
            launchGameName.takeIf { it.isNotBlank() }
                ?: romPath.takeIf { it.isNotBlank() }?.let { java.io.File(it).nameWithoutExtension }
                ?: context.getString(R.string.retro_ps2_tab_menu)
        }
    RetroAchievementsScreen(
        systemId = RetroSystems.PS2.id,
        gameName = gameName,
        romPath = romPath,
        inSession = true,
        onClose = onBack,
        useNativePs2 = true,
        floatingOverGame = true,
    )
}

@Composable
fun Ps2WindowedScaffold(
    title: String,
    onBack: () -> Unit,
    header: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val registry = remember { PaneNavRegistry() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val restore = activity?.window?.bindPaneNav(registry, onDismiss = onBack)
        onDispose { restore?.invoke() }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPaneNav provides registry) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.paneNavItem(onActivate = onBack)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.retro_scr_back), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
                        Spacer(Modifier.weight(1f))
                        header?.invoke()
                    }
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
    }
}

@Composable
fun Ps2OverlayScaffold(
    title: String,
    onBack: () -> Unit,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val registry = remember { PaneNavRegistry() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val restore = activity?.window?.bindPaneNav(registry, onDismiss = onBack)
        onDispose { restore?.invoke() }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPaneNav provides registry) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.paneNavItem(onActivate = onBack)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.retro_scr_back), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.width(0.dp).weight(1f))
                action?.invoke()
            }
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}
