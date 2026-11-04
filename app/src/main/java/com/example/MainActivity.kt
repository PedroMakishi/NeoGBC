package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.RomEntity
import com.example.ui.screens.EmulatorScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.NeoGbcViewModel

class MainActivity : ComponentActivity() {

    internal val viewModel: NeoGbcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        setContent {
            MyApplicationTheme {
                NeoGbcAppContent(
                    viewModel = viewModel,
                    onPersistFolderUri = { uri ->
                        try {
                            val rwFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            contentResolver.takePersistableUriPermission(uri, rwFlags)
                        } catch (_: Exception) {
                            try {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: Exception) {
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && viewModel.sessionState.value.isRunning) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.handlePhysicalKeyEvent(keyCode, true)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppMinimized()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    override fun onStop() {
        super.onStop()
        viewModel.flushActiveSessionToDisk()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.handlePhysicalKeyEvent(keyCode, false)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
fun NeoGbcAppContent(
    viewModel: NeoGbcViewModel,
    onPersistFolderUri: (android.net.Uri) -> Unit
) {
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val saveStates by viewModel.activeSaveStates.collectAsStateWithLifecycle()
    val cheats by viewModel.activeCheats.collectAsStateWithLifecycle()

    val isGameActive = sessionState.isRunning && sessionState.activeRom != null
    val view = LocalView.current

    // Automatically toggle Android Immersive Fullscreen (hide status bar & navigation bar) when game opens
    LaunchedEffect(isGameActive) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (isGameActive) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var pendingRomForCover by remember { mutableStateOf<RomEntity?>(null) }

    // 1. Directory Picker Launcher (SAF OpenDocumentTree — displays "Usar esta pasta" at bottom)
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            onPersistFolderUri(treeUri)
            viewModel.onDirectorySelected(treeUri)
        }
    }

    // 2. Multiple ROM / ZIP File Picker Launcher (.gb, .gbc, .zip)
    val romFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { fileUris ->
        if (fileUris.isNotEmpty()) {
            viewModel.onMultipleRomsSelected(fileUris)
        }
    }

    // 3. Zero-Permission Photo Picker for Custom Cover Art
    val coverPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { imageUri ->
        val targetRom = pendingRomForCover
        if (imageUri != null && targetRom != null) {
            viewModel.onCustomCoverSelected(targetRom, imageUri)
        }
        pendingRomForCover = null
    }

    val initialInternalStorageUri = remember {
        try {
            android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3A")
        } catch (_: Exception) {
            null
        }
    }

    Crossfade(
        targetState = isGameActive,
        label = "screen_transition"
    ) { inGame ->
        if (inGame) {
            EmulatorScreen(
                sessionState = sessionState,
                framePixels = viewModel.currentFramePixels,
                saveStates = saveStates,
                cheats = cheats,
                onTouchInputChanged = viewModel::updateTouchControls,
                onToggleTurbo = viewModel::toggleTurboMode,
                onTogglePause = viewModel::togglePause,
                onTakeScreenshot = viewModel::captureGameScreenshotToGallery,
                onToggleAudio = viewModel::toggleAudio,
                onQuickSave = viewModel::performQuickSave,
                onQuickLoad = viewModel::performQuickLoad,
                onConfirmQuickLoad = viewModel::confirmQuickLoad,
                onOpenModal = viewModel::openModal,
                onEnterEditControls = viewModel::enterTouchControlsEditor,
                onExitEditControls = viewModel::exitTouchControlsEditor,
                onUpdateControlLayout = viewModel::updateControlLayout,
                onResetControlLayout = viewModel::resetControlLayout,
                onSaveToSlot = viewModel::saveStateToSlot,
                onLoadFromState = viewModel::loadStateFromEntity,
                onDeleteSaveState = viewModel::deleteSaveState,
                onAddCheat = viewModel::addCheatCode,
                onToggleCheat = viewModel::toggleCheatCode,
                onDeleteCheat = viewModel::deleteCheatCode,
                onSelectVideoFilter = viewModel::setVideoFilter,
                onSelectDmgPalette = viewModel::setDmgPalette,
                onStartRemapButton = viewModel::startRemappingButton,
                onResetKeyMappings = viewModel::resetKeyMappings,
                onResetGame = viewModel::resetCurrentGame,
                onClearToast = viewModel::clearToastMessage,
                onExitToLibrary = viewModel::exitToLibrary
            )
        } else {
            LibraryScreen(
                uiState = libraryState,
                sessionState = sessionState,
                onSearchChange = viewModel::updateSearchQuery,
                onFilterSelect = viewModel::selectLibraryFilter,
                onAddDirectoryClick = {
                    directoryPickerLauncher.launch(initialInternalStorageUri)
                },
                onImportRomFileClick = {
                    romFilePickerLauncher.launch(arrayOf("*/*"))
                },
                onPlayRom = { rom -> viewModel.launchRom(rom, loadLatestSave = false) },
                onContinueRom = viewModel::continueRom,
                onToggleFavorite = viewModel::toggleFavorite,
                onOpenCoverMenu = viewModel::openCoverMenuForRom,
                onDismissCoverMenu = { viewModel.openCoverMenuForRom(null) },
                onPickCustomCover = { rom ->
                    pendingRomForCover = rom
                    coverPhotoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRestoreAutoCover = viewModel::onRestoreAutoCover,
                onRenameRom = viewModel::renameRom,
                onDeleteRom = viewModel::deleteRom,
                onDismissBanner = viewModel::clearStatusBanner,
                onSelectVideoFilter = viewModel::setVideoFilter,
                onSelectDmgPalette = viewModel::setDmgPalette,
                onToggleAudio = viewModel::toggleAudio,
                onToggleFpsHud = viewModel::toggleFpsHud,
                onToggleHaptics = viewModel::toggleAllHaptics,
                onResetAllControlLayouts = viewModel::resetAllControlLayouts
            )
        }
    }
}
