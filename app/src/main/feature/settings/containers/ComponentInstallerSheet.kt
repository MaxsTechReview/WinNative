package com.winlator.cmod.feature.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.content.Downloader
import com.winlator.cmod.runtime.content.component.ComponentInstaller
import com.winlator.cmod.shared.util.StringUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val DATASET_BASE = "https://huggingface.co/datasets/Xnick417x/WN-Components/resolve/main"
private const val INDEX_URL = "$DATASET_BASE/index.json"

// One install at a time across the app (the boot session + result bridge are single-instance).
private val installMutex = Mutex()

private val SheetRoot = Color(0xFF18181D)
private val SheetCard = Color(0xFF1C1C2A)
private val SheetSubcard = Color(0xFF161622)
private val SheetOutline = Color(0xFF2A2A3A)
private val SheetAccent = Color(0xFF1A9FFF)
private val SheetTextPrimary = Color(0xFFF0F4FF)
private val SheetTextSecondary = Color(0xFF7A8FA8)
private val SheetDanger = Color(0xFFFF7A88)
private val SheetGood = Color(0xFF6BD08A)

private sealed interface InstallUi {
    data class Running(
        val text: String,
    ) : InstallUi

    data object Done : InstallUi

    data class Failed(
        val message: String,
    ) : InstallUi
}

data class CatalogComponent(
    val name: String,
    val description: String,
    val provider: String,
    val category: String,
    val species: String,
    val size: Long,
    val manifest: String,
    val dependencies: List<String>,
)

private sealed interface CatalogUiState {
    data object Loading : CatalogUiState

    data class Error(
        val message: String,
    ) : CatalogUiState

    data class Loaded(
        val items: List<CatalogComponent>,
    ) : CatalogUiState
}

private fun parseCatalog(json: String): List<CatalogComponent> {
    val arr = JSONObject(json).getJSONArray("components")
    val out = ArrayList<CatalogComponent>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val depsArr = o.optJSONArray("dependencies")
        val deps =
            if (depsArr != null) {
                (0 until depsArr.length()).map { depsArr.getString(it) }
            } else {
                emptyList()
            }
        out.add(
            CatalogComponent(
                name = o.getString("name"),
                description = o.optString("description"),
                provider = o.optString("provider"),
                category = o.optString("category", "Other"),
                species = o.optString("species", "installer"),
                size = o.optLong("size"),
                manifest = o.optString("manifest"),
                dependencies = deps,
            ),
        )
    }
    return out
}

private val CATEGORY_ORDER =
    listOf(
        "Wine Mono / Gecko",
        "Visual C++ / VB",
        "OS Update",
        "Graphics",
        "DirectX",
        ".NET",
        "Media / Codecs",
        "System / Web",
    )

private fun categoryRank(category: String): Int =
    when {
        category == "Other" -> Int.MAX_VALUE
        else -> CATEGORY_ORDER.indexOf(category).let { if (it >= 0) it else CATEGORY_ORDER.size }
    }

@Composable
fun ComponentInstallerSheet(
    container: Container,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installStates = remember { mutableStateMapOf<String, InstallUi>() }
    var ui by remember { mutableStateOf<CatalogUiState>(CatalogUiState.Loading) }

    LaunchedEffect(Unit) {
        ui =
            withContext(Dispatchers.IO) {
                val json = Downloader.downloadString(INDEX_URL)
                if (json == null) {
                    CatalogUiState.Error("Couldn't reach the component catalog.")
                } else {
                    try {
                        CatalogUiState.Loaded(
                            parseCatalog(json)
                                .sortedWith(
                                    compareBy(
                                        { categoryRank(it.category) },
                                        { it.category },
                                        { it.name.lowercase() },
                                    ),
                                ),
                        )
                    } catch (e: Exception) {
                        CatalogUiState.Error("The component catalog is malformed.")
                    }
                }
            }
    }

    LaunchedEffect(container.id) {
        val installed =
            withContext(Dispatchers.IO) {
                ComponentInstaller.installedComponents(container)
            }
        installed.forEach { installStates[it] = InstallUi.Done }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val popupWidth = if (maxWidth < 420.dp) maxWidth else 420.dp
            val popupHeight = if (maxHeight < 500.dp) maxHeight else 500.dp
            Column(
                modifier =
                    Modifier
                        .width(popupWidth)
                        .height(popupHeight)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SheetRoot)
                        .border(1.dp, SheetOutline, RoundedCornerShape(18.dp)),
            ) {
                SheetHeader(containerName = container.name, onClose = onDismiss)
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when (val state = ui) {
                        CatalogUiState.Loading -> SheetCentered { Spinner() }
                        is CatalogUiState.Error -> SheetCentered { ErrorText(state.message) }
                        is CatalogUiState.Loaded ->
                            ComponentList(
                                items = state.items,
                                installStates = installStates,
                                onInstall = { item ->
                                    installStates[item.name] = InstallUi.Running("Queued…")
                                    scope.launch(Dispatchers.IO) {
                                        installMutex.withLock {
                                            try {
                                                installStates[item.name] = InstallUi.Running("Starting…")
                                                runInterruptible {
                                                    val yaml =
                                                        Downloader.downloadString("$DATASET_BASE/${item.manifest}")
                                                            ?: throw Exception("Couldn't fetch the manifest.")
                                                    ComponentInstaller(
                                                        context = context,
                                                        container = container,
                                                        componentName = item.name,
                                                        manifestYaml = yaml,
                                                        listener =
                                                            object : ComponentInstaller.Listener {
                                                                override fun onStatus(text: String) {
                                                                    installStates[item.name] = InstallUi.Running(text)
                                                                }

                                                                override fun onProgress(fraction: Float) {}
                                                            },
                                                    ).run()
                                                }
                                                installStates[item.name] = InstallUi.Done
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                installStates[item.name] =
                                                    InstallUi.Failed(e.message ?: "Install failed.")
                                            }
                                        }
                                    }
                                },
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    containerName: String,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Install components",
                    color = SheetTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = containerName,
                    color = SheetTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SheetSubcard)
                        .border(1.dp, SheetOutline, RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = SheetTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SheetOutline))
    }
}

@Composable
private fun ComponentList(
    items: List<CatalogComponent>,
    installStates: Map<String, InstallUi>,
    onInstall: (CatalogComponent) -> Unit,
) {
    val grouped = items.groupBy { it.category }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        grouped.forEach { (category, group) ->
            item(key = "h_$category") {
                Text(
                    text = category.uppercase(),
                    color = SheetTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                )
            }
            items(group.size, key = { group[it].manifest }) { idx ->
                val component = group[idx]
                ComponentRow(
                    item = component,
                    status = installStates[component.name],
                    onInstall = onInstall,
                )
            }
        }
    }
}

@Composable
private fun ComponentRow(
    item: CatalogComponent,
    status: InstallUi?,
    onInstall: (CatalogComponent) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SheetCard)
                .border(1.dp, SheetOutline, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    color = SheetTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                SpeciesBadge(item.species)
            }
            val (secondLine, secondColor) =
                when (status) {
                    is InstallUi.Running -> status.text to SheetAccent
                    is InstallUi.Failed -> status.message to SheetDanger
                    else -> item.description to SheetTextSecondary
                }
            if (secondLine.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondLine,
                    color = secondColor,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            when (status) {
                null -> {
                    if (item.size > 0L) {
                        Text(
                            text = StringUtils.formatBytes(item.size),
                            color = SheetTextSecondary,
                            fontSize = 10.sp,
                        )
                        Spacer(Modifier.height(5.dp))
                    }
                    InstallButton(label = "Install", onClick = { onInstall(item) })
                }

                is InstallUi.Running ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = SheetAccent,
                        strokeWidth = 2.5.dp,
                    )

                InstallUi.Done ->
                    Text(
                        text = "Installed",
                        color = SheetGood,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )

                is InstallUi.Failed -> InstallButton(label = "Retry", onClick = { onInstall(item) })
            }
        }
    }
}

@Composable
private fun SpeciesBadge(species: String) {
    val label = if (species == "installer") "INSTALLER" else "LIBRARY"
    val color = if (species == "installer") SheetAccent else SheetTextSecondary
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.12f))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun InstallButton(
    label: String = "Install",
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SheetAccent.copy(alpha = 0.14f))
                .border(1.dp, SheetAccent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = SheetAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SheetCentered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(40.dp),
        color = SheetAccent,
        strokeWidth = 3.dp,
    )
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = SheetTextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(24.dp),
    )
}
