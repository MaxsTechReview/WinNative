package com.winlator.cmod.feature.settings
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.winlator.cmod.R
import com.winlator.cmod.feature.settings.ContainerSettingsComposeDialog
import com.winlator.cmod.feature.shortcuts.ShortcutsFragment
import com.winlator.cmod.feature.sync.google.ContainerBackupManager
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.content.ContentsManager
import com.winlator.cmod.runtime.display.XServerDisplayActivity
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.ui.dialog.PreloaderDialog
import java.io.File
import kotlin.math.roundToInt

class ContainersFragment : Fragment() {
    private lateinit var manager: ContainerManager
    private lateinit var preloaderDialog: PreloaderDialog

    private var screenState by mutableStateOf(ContainersScreenState())
    private var pendingBackups = emptyList<ContainerBackupManager.DriveBackupFile>()
    private var storageScanToken = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preloaderDialog = PreloaderDialog(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(
                    colorScheme =
                        darkColorScheme(
                            primary = Color(0xFF1A9FFF),
                            background = Color(0xFF18181D),
                            surface = Color(0xFF1C1C2A),
                        ),
                ) {
                    ContainersScreen(
                        state = screenState,
                        onAddContainer = ::openAddContainer,
                        onRunContainer = ::runContainer,
                        onEditContainer = ::editContainer,
                        onDuplicateContainer = ::duplicateContainer,
                        onShowBackups = ::showContainerBackupsDialog,
                        onRemoveContainer = ::removeContainer,
                        onShowInfo = ::showContainerInfo,
                        onDismissDialog = ::dismissDialog,
                        onConfirmDuplicateDialog = ::performDuplicateContainer,
                        onConfirmRemoveDialog = ::performRemoveContainer,
                        onConfirmBackupDialog = ::startContainerBackup,
                        onConfirmRestoreDialog = ::startContainerRestore,
                        onClearCacheDialog = ::clearContainerCache,
                        onBackupSelectionChosen = ::restoreBackupByIndex,
                    )
                }
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.common_ui_containers)
        loadContainersList()
    }

    override fun onResume() {
        super.onResume()
        loadContainersList()
    }

    private fun loadContainersList() {
        val context = context ?: return
        manager = ContainerManager(context)
        screenState = screenState.copy(containers = manager.containers.toList())
    }

    private fun openAddContainer() {
        val context = context ?: return
        if (!ImageFs.find(context).isValid) {
            AppUtils.showToast(context, R.string.setup_wizard_system_image_not_installed, Toast.LENGTH_LONG)
            return
        }

        Thread {
            val ctx = context ?: return@Thread
            val installed = ContentsManager.hasInstalledRuntimes(ctx)
            if (!isAdded) return@Thread

            requireActivity().runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (!installed) {
                    AppUtils.showToast(ctx, R.string.container_no_wine_installed, Toast.LENGTH_LONG)
                    return@runOnUiThread
                }
                ContainerSettingsComposeDialog(requireActivity(), null, ::loadContainersList).show()
            }
        }.start()
    }

    private fun runContainer(container: Container) {
        val ctx = context ?: return
        startActivity(
            Intent(ctx, XServerDisplayActivity::class.java).apply {
                putExtra("container_id", container.id)
            },
        )
    }

    private fun editContainer(container: Container) {
        ContainerSettingsComposeDialog(requireActivity(), container, ::loadContainersList).show()
    }

    private fun duplicateContainer(container: Container) {
        screenState = screenState.copy(dialog = ContainersDialogUiState.ConfirmDuplicate(container))
    }

    private fun removeContainer(container: Container) {
        screenState = screenState.copy(dialog = ContainersDialogUiState.ConfirmRemove(container))
    }

    private fun showContainerInfo(container: Container) {
        screenState =
            screenState.copy(
                dialog =
                    ContainersDialogUiState.StorageInfo(
                        ContainerStorageInfoUiState(container = container),
                    ),
            )
        startStorageScan(container)
    }

    private fun showContainerBackupsDialog(container: Container) {
        screenState = screenState.copy(dialog = ContainersDialogUiState.Backups(container))
    }

    private fun startContainerBackup(container: Container) {
        dismissDialog()
        preloaderDialog.show(R.string.container_backups_backing_up)
        ContainerBackupManager.backupContainer(requireActivity(), container) { result ->
            preloaderDialog.close()
            screenState =
                screenState.copy(
                    dialog =
                        ContainersDialogUiState.Message(
                            title = getString(R.string.container_backups_title),
                            message = result.message,
                        ),
                )
        }
    }

    private fun startContainerRestore(container: Container) {
        dismissDialog()
        preloaderDialog.show(R.string.container_backups_checking)
        ContainerBackupManager.prepareRestore(requireActivity(), container) { preparation ->
            if (!isAdded) {
                preloaderDialog.close()
                return@prepareRestore
            }

            if (!preparation.success) {
                preloaderDialog.close()
                screenState =
                    screenState.copy(
                        dialog =
                            ContainersDialogUiState.Message(
                                title = getString(R.string.container_backups_title),
                                message = preparation.message,
                            ),
                    )
                return@prepareRestore
            }

            if (preparation.matchedFile != null) {
                executeContainerRestore(container, preparation.matchedFile)
                return@prepareRestore
            }

            preloaderDialog.close()
            showBackupSelectionDialog(container, preparation.candidates)
        }
    }

    private fun showBackupSelectionDialog(
        container: Container,
        backups: List<ContainerBackupManager.DriveBackupFile>?,
    ) {
        if (backups.isNullOrEmpty()) {
            screenState =
                screenState.copy(
                    dialog =
                        ContainersDialogUiState.Message(
                            title = getString(R.string.container_backups_title),
                            message = getString(R.string.container_backups_no_files),
                        ),
                )
            return
        }

        pendingBackups = backups
        screenState =
            screenState.copy(
                dialog =
                    ContainersDialogUiState.BackupSelection(
                        container = container,
                        backupNames = backups.map { it.name },
                    ),
            )
    }

    private fun executeContainerRestore(
        container: Container,
        driveFile: ContainerBackupManager.DriveBackupFile,
    ) {
        dismissDialog()
        preloaderDialog.show(R.string.container_backups_restoring)
        ContainerBackupManager.restoreContainerFromDriveFile(requireActivity(), container, driveFile) { result ->
            preloaderDialog.close()
            screenState =
                screenState.copy(
                    dialog =
                        ContainersDialogUiState.Message(
                            title = getString(R.string.container_backups_title),
                            message = result.message,
                        ),
                )
        }
    }

    private fun performDuplicateContainer(container: Container) {
        dismissDialog()
        preloaderDialog.show(R.string.containers_list_duplicating, false)
        manager.duplicateContainerAsync(container, { progress ->
            preloaderDialog.setProgress(progress)
        }) {
            preloaderDialog.setProgress(100)
            preloaderDialog.closeWithDelay(600)
            Handler(Looper.getMainLooper()).postDelayed({
                loadContainersList()
            }, 600)
        }
    }

    private fun performRemoveContainer(container: Container) {
        val ctx = context ?: return
        dismissDialog()
        preloaderDialog.show(R.string.containers_list_removing)
        for (shortcut in manager.loadShortcuts()) {
            if (shortcut.container == container) {
                ShortcutsFragment.disableShortcutOnScreen(ctx, shortcut)
            }
        }
        manager.removeContainerAsync(container) {
            preloaderDialog.close()
            loadContainersList()
        }
    }

    private fun clearContainerCache(container: Container) {
        val cacheDir = File(container.rootDir, ".cache")
        Thread {
            FileUtils.clear(cacheDir)
            container.putExtra("desktopTheme", null)
            container.saveData()
            if (!isAdded) return@Thread
            Handler(Looper.getMainLooper()).post {
                if (!isAdded) return@post
                showContainerInfo(container)
            }
        }.start()
    }

    private fun restoreBackupByIndex(index: Int) {
        val dialog = screenState.dialog as? ContainersDialogUiState.BackupSelection ?: return
        val backup = pendingBackups.getOrNull(index) ?: return
        executeContainerRestore(dialog.container, backup)
    }

    private fun dismissDialog() {
        storageScanToken += 1L
        screenState = screenState.copy(dialog = ContainersDialogUiState.None)
    }

    private fun startStorageScan(container: Container) {
        val token = System.nanoTime()
        storageScanToken = token
        Thread {
            val driveCBytes = calculateDirectorySize(File(container.rootDir, ".wine/drive_c"))
            val cacheBytes = calculateDirectorySize(File(container.rootDir, ".cache"))
            val totalBytes = driveCBytes + cacheBytes
            val internalStorage = FileUtils.getInternalStorageSize().coerceAtLeast(1L)
            val usedPercent =
                ((totalBytes.toDouble() / internalStorage.toDouble()) * 100.0)
                    .toFloat()
                    .coerceIn(0f, 100f)

            if (!isAdded) return@Thread
            Handler(Looper.getMainLooper()).post {
                if (!isAdded || storageScanToken != token) return@post
                val dialog = screenState.dialog as? ContainersDialogUiState.StorageInfo ?: return@post
                if (dialog.data.container.id != container.id) return@post
                screenState =
                    screenState.copy(
                        dialog =
                            ContainersDialogUiState.StorageInfo(
                                dialog.data.copy(
                                    driveCBytes = driveCBytes,
                                    cacheBytes = cacheBytes,
                                    totalBytes = totalBytes,
                                    usedPercent = usedPercent,
                                    isLoading = false,
                                ),
                            ),
                    )
            }
        }.start()
    }

    private fun calculateDirectorySize(root: File?): Long {
        if (root == null || !root.exists()) return 0L
        if (root.isFile) return root.length()

        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val files = current.listFiles() ?: continue
            for (file in files) {
                if (file.isDirectory) {
                    stack.add(file)
                } else {
                    total += file.length()
                }
            }
        }
        return total
    }
}
