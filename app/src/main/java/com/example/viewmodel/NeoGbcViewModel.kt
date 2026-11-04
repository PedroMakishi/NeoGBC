package com.example.viewmodel

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CheatEntity
import com.example.data.ControlLayoutEntity
import com.example.data.NeoGbcDatabase
import com.example.data.NeoGbcRepository
import com.example.data.RomEntity
import com.example.data.SaveStateEntity
import com.example.data.ScannedDirectoryEntity
import com.example.emulator.DmgPalettePreset
import com.example.emulator.GbcApu
import com.example.emulator.GbcEmulatorCore
import com.example.emulator.GbcPpu
import com.example.emulator.VideoFilterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("Todos"),
    FAVORITES("Favoritos"),
    RECENT("Recentes"),
    GBC("GBC Color"),
    GB("GB Clássico")
}

enum class GbcButton(val displayName: String) {
    UP("Cima (D-Pad)"),
    DOWN("Baixo (D-Pad)"),
    LEFT("Esquerda (D-Pad)"),
    RIGHT("Direita (D-Pad)"),
    A("Botão A"),
    B("Botão B"),
    START("Start"),
    SELECT("Select"),
    TURBO("Turbo (Fast-Forward)"),
    QUICK_SAVE("Salvar Rápido"),
    QUICK_LOAD("Carregar Rápido")
}

data class LibraryUiState(
    val roms: List<RomEntity> = emptyList(),
    val latestSaveByRomId: Map<Long, SaveStateEntity> = emptyMap(),
    val directories: List<ScannedDirectoryEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: LibraryFilter = LibraryFilter.ALL,
    val isScanning: Boolean = false,
    val statusBanner: String? = null,
    val romForCoverMenu: RomEntity? = null
)

data class EmulatorSessionState(
    val activeRom: RomEntity? = null,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isAutoPausedByLifecycle: Boolean = false,
    val speedMultiplier: Float = 1.0f, // 1.0f normal, 2.0f Turbo
    val audioEnabled: Boolean = true,
    val showFpsHud: Boolean = true,
    val currentFps: Int = 60,
    val frameTick: Long = 0L,
    val videoFilter: VideoFilterMode = VideoFilterMode.LCD_GRID,
    val dmgPalette: DmgPalettePreset = DmgPalettePreset.CLASSIC_GREEN,
    val isEditingControls: Boolean = false,
    val portraitLayout: ControlLayoutEntity = NeoGbcRepository.defaultControlLayout(false),
    val landscapeLayout: ControlLayoutEntity = NeoGbcRepository.defaultControlLayout(true),
    val activeModal: EmulatorModal = EmulatorModal.NONE,
    val buttonBeingRemapped: GbcButton? = null,
    val keyMappings: Map<GbcButton, Int> = defaultKeyMappings(),
    val toastMessage: String? = null
) {
    val isTurboEnabled: Boolean
        get() = speedMultiplier > 1.0f

    companion object {
        fun defaultKeyMappings(): Map<GbcButton, Int> = mapOf(
            GbcButton.UP to KeyEvent.KEYCODE_DPAD_UP,
            GbcButton.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            GbcButton.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
            GbcButton.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
            GbcButton.A to KeyEvent.KEYCODE_BUTTON_A,
            GbcButton.B to KeyEvent.KEYCODE_BUTTON_B,
            GbcButton.START to KeyEvent.KEYCODE_BUTTON_START,
            GbcButton.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT,
            GbcButton.TURBO to KeyEvent.KEYCODE_BUTTON_R1,
            GbcButton.QUICK_SAVE to KeyEvent.KEYCODE_BUTTON_L1,
            GbcButton.QUICK_LOAD to KeyEvent.KEYCODE_BUTTON_L2
        )
    }
}

enum class EmulatorModal {
    NONE,
    QUICK_MENU,
    SAVE_STATES,
    CHEATS,
    VIDEO_FILTERS,
    GAMEPAD_REMAP
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NeoGbcViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NeoGbcDatabase.getInstance(application)
    val repository = NeoGbcRepository(application, database.dao())

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow(LibraryFilter.ALL)
    private val _isScanning = MutableStateFlow(false)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _romForCoverMenu = MutableStateFlow<RomEntity?>(null)

    val libraryState: StateFlow<LibraryUiState> = combine(
        repository.allRoms,
        repository.allSaveStates,
        repository.scannedDirectories,
        _searchQuery,
        combine(_selectedFilter, _isScanning, _statusBanner, _romForCoverMenu) { f, s, b, r ->
             listOf(f, s, b, r)
        }
    ) { allRoms, allSaves, dirs, query, extras ->
        val filter = extras[0] as LibraryFilter
        val scanning = extras[1] as Boolean
        val banner = extras[2] as String?
        val coverRom = extras[3] as RomEntity?

        val latestSavesMap = linkedMapOf<Long, SaveStateEntity>()
        for (save in allSaves) {
            val current = latestSavesMap[save.romId]
            if (current == null || save.timestamp > current.timestamp) {
                latestSavesMap[save.romId] = save
            }
        }

        val filtered = allRoms.filter { rom ->
            val matchesQuery = query.isBlank() ||
                rom.title.contains(query, ignoreCase = true) ||
                rom.headerTitle.contains(query, ignoreCase = true) ||
                rom.crc32Hex.contains(query, ignoreCase = true)

            val matchesCategory = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.FAVORITES -> rom.isFavorite
                LibraryFilter.RECENT -> rom.lastPlayedTimestamp > 0L
                LibraryFilter.GBC -> rom.isCgb
                LibraryFilter.GB -> !rom.isCgb
            }
            matchesQuery && matchesCategory
        }

        val sorted = if (filter == LibraryFilter.RECENT) {
            filtered.sortedWith(
                compareByDescending<RomEntity> { it.lastPlayedTimestamp }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
        } else {
            // Strictly alphabetical order (A -> Z) for "Todos" and category tabs
            filtered.sortedWith(
                compareBy<RomEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
                    .thenBy { it.fileName }
                    .thenBy { it.id }
            )
        }

        LibraryUiState(
            roms = sorted,
            latestSaveByRomId = latestSavesMap,
            directories = dirs,
            searchQuery = query,
            selectedFilter = filter,
            isScanning = scanning,
            statusBanner = banner,
            romForCoverMenu = coverRom
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    private val _sessionState = MutableStateFlow(EmulatorSessionState())
    val sessionState: StateFlow<EmulatorSessionState> = _sessionState.asStateFlow()

    private val _activeRomId = MutableStateFlow<Long?>(null)

    val activeSaveStates: StateFlow<List<SaveStateEntity>> = _activeRomId
        .flatMapLatest { id -> if (id != null) repository.observeSaveStates(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCheats: StateFlow<List<CheatEntity>> = _activeRomId
        .flatMapLatest { id -> if (id != null) repository.observeCheats(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Framebuffer exposed for Canvas rendering
    val currentFramePixels = IntArray(GbcPpu.SCREEN_WIDTH * GbcPpu.SCREEN_HEIGHT) { 0xFF07080C.toInt() }

    private var emulatorCore: GbcEmulatorCore? = null
    private var emulationJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var sessionStartTimestamp: Long = 0L

    // Live Input states (combined touch + physical gamepad)
    @Volatile private var inputUp = false
    @Volatile private var inputDown = false
    @Volatile private var inputLeft = false
    @Volatile private var inputRight = false
    @Volatile private var inputA = false
    @Volatile private var inputB = false
    @Volatile private var inputStart = false
    @Volatile private var inputSelect = false
    @Volatile private var physicalTurboHeld = false

    init {
        viewModelScope.launch {
            repository.reconcilePersistedData()
            val portrait = repository.getOrCreateControlLayout(false)
            val landscape = repository.getOrCreateControlLayout(true)
            _sessionState.update { current ->
                current.copy(
                    portraitLayout = portrait,
                    landscapeLayout = landscape
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectLibraryFilter(filter: LibraryFilter) {
        _selectedFilter.value = filter
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun openCoverMenuForRom(rom: RomEntity?) {
        _romForCoverMenu.value = rom
    }

    fun toggleFavorite(rom: RomEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(rom)
        }
    }

    fun renameRom(rom: RomEntity, newTitle: String) {
        viewModelScope.launch {
            val updated = repository.renameRom(rom, newTitle)
            _romForCoverMenu.value = null
            if (updated != null) {
                _statusBanner.value = "Jogo e arquivo renomeados para \"${updated.title}\"!"
            }
        }
    }

    fun deleteRom(rom: RomEntity) {
        viewModelScope.launch {
            repository.deleteRom(rom)
            _romForCoverMenu.value = null
            _statusBanner.value = "Jogo \"${rom.title}\" removido da biblioteca."
        }
    }

    fun onDirectorySelected(treeUri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            _statusBanner.value = "Escaneando diretório em busca de ROMs (.gbc, .gb, .zip)..."
            val count = repository.scanDirectoryTree(treeUri)
            _isScanning.value = false
            _statusBanner.value = if (count > 0) {
                "$count novo(s) jogo(s) importado(s) com capas automáticas!"
            } else {
                "Diretório sincronizado. Nenhuma ROM inédita encontrada."
            }
        }
    }

    fun onMultipleRomsSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isScanning.value = true
            var count = 0
            var lastTitle = ""
            for (uri in uris) {
                val imported = repository.importSingleRomUri(uri)
                if (imported != null) {
                    count++
                    lastTitle = imported.title
                }
            }
            _isScanning.value = false
            _statusBanner.value = when {
                count == 1 -> "ROM \"$lastTitle\" adicionada à biblioteca!"
                count > 1 -> "$count jogos adicionados à biblioteca com capas automáticas!"
                else -> "Não foi possível ler o(s) arquivo(s) selecionado(s)."
            }
        }
    }

    fun importRawRomBytes(fileName: String, rawBytes: ByteArray) {
        viewModelScope.launch {
            repository.importRomBytes(fileName, rawBytes)
        }
    }

    fun onCustomCoverSelected(rom: RomEntity, imageUri: Uri) {
        viewModelScope.launch {
            repository.setCustomCoverForRom(rom, imageUri)
            _romForCoverMenu.value = null
            _statusBanner.value = "Capa personalizada aplicada em \"${rom.title}\"!"
        }
    }

    fun onRestoreAutoCover(rom: RomEntity) {
        viewModelScope.launch {
            repository.clearCustomCoverForRom(rom)
            _romForCoverMenu.value = null
            _statusBanner.value = "Capa automática restaurada para \"${rom.title}\"."
        }
    }

    fun launchRom(rom: RomEntity, loadLatestSave: Boolean = false) {
        viewModelScope.launch {
            stopEmulationInternal()
            val bytes = repository.loadRomBytes(rom)
            if (bytes == null) {
                _statusBanner.value = "Erro ao carregar os dados da ROM \"${rom.title}\"."
                return@launch
            }

            val core = GbcEmulatorCore(bytes)
            repository.loadBatterySram(rom.crc32Hex)?.let { sram ->
                core.cartridge.loadBatterySram(sram)
            }
            core.ppu.dmgPalettePreset = _sessionState.value.dmgPalette

            val cheats = database.dao().getCheatsForRomOnce(rom.id)
                .filter { it.isEnabled }
                .map { it.code }
            core.applyCheatCodes(cheats)

            var initialToast = "Executando ${rom.title} (${rom.cartridgeType})"
            if (loadLatestSave) {
                val latestSave = repository.getLatestSaveState(rom.id)
                if (latestSave != null) {
                    val stateBytes = repository.readSaveStateBytes(latestSave)
                    if (stateBytes != null && core.deserializeState(stateBytes)) {
                        synchronized(currentFramePixels) {
                            System.arraycopy(
                                core.ppu.frontBuffer,
                                0,
                                currentFramePixels,
                                0,
                                currentFramePixels.size
                            )
                        }
                        val slotLabel = if (latestSave.slotIndex == 0) "Quick Save" else "Slot ${latestSave.slotIndex}"
                        initialToast = "Continuando ${rom.title} ($slotLabel)"
                    }
                }
            }

            emulatorCore = core
            _activeRomId.value = rom.id
            sessionStartTimestamp = System.currentTimeMillis()

            _sessionState.update { current ->
                current.copy(
                    activeRom = rom,
                    isRunning = true,
                    isPaused = false,
                    isAutoPausedByLifecycle = false,
                    speedMultiplier = 1.0f,
                    frameTick = core.framesExecuted,
                    activeModal = EmulatorModal.NONE,
                    isEditingControls = false,
                    toastMessage = initialToast
                )
            }

            startEmulationLoop()
        }
    }

    fun continueRom(rom: RomEntity) {
        launchRom(rom, loadLatestSave = true)
    }

    private fun startEmulationLoop() {
        emulationJob?.cancel()
        initAudioTrackIfNeeded()

        emulationJob = viewModelScope.launch(Dispatchers.Default) {
            var fpsFrames = 0
            var fpsTimer = System.currentTimeMillis()

            while (isActive && _sessionState.value.isRunning) {
                val state = _sessionState.value
                val core = emulatorCore
                if (core == null || state.isPaused || state.isEditingControls || state.activeModal != EmulatorModal.NONE) {
                    delay(16L)
                    continue
                }

                val frameStartNs = System.nanoTime()

                core.updateInputs(
                    right = inputRight,
                    left = inputLeft,
                    up = inputUp,
                    down = inputDown,
                    a = inputA,
                    b = inputB,
                    select = inputSelect,
                    start = inputStart
                )

                val isTurbo = state.isTurboEnabled || physicalTurboHeld
                val stepsCount = if (isTurbo) 2 else 1

                var latestBuffer = core.ppu.frontBuffer
                for (s in 0 until stepsCount) {
                    latestBuffer = core.runSingleFrame()
                }

                synchronized(currentFramePixels) {
                    System.arraycopy(latestBuffer, 0, currentFramePixels, 0, currentFramePixels.size)
                }

                if (state.audioEnabled && stepsCount == 1 && core.apu.samplesProduced > 0) {
                    try {
                        audioTrack?.write(core.apu.sampleBuffer, 0, core.apu.samplesProduced)
                    } catch (_: Exception) {
                    }
                }

                fpsFrames += stepsCount
                val nowMs = System.currentTimeMillis()
                val updatedFps = if (nowMs - fpsTimer >= 1000L) {
                    val calc = fpsFrames
                    fpsFrames = 0
                    fpsTimer = nowMs
                    calc
                } else {
                    null
                }

                if (core.framesExecuted > 0L && core.framesExecuted % 300L == 0L) {
                    state.activeRom?.let { rom ->
                        try {
                            repository.saveBatterySram(rom.crc32Hex, core.cartridge.dumpBatterySram())
                        } catch (_: Exception) {
                        }
                    }
                }

                _sessionState.update { current ->
                    current.copy(
                        frameTick = core.framesExecuted,
                        currentFps = updatedFps ?: current.currentFps
                    )
                }

                val targetFrameNs = 16_742_000L
                val elapsedNs = System.nanoTime() - frameStartNs
                val sleepMs = (targetFrameNs - elapsedNs) / 1_000_000L
                if (sleepMs > 1L) {
                    delay(sleepMs)
                } else {
                    delay(1L)
                }
            }
        }
    }

    private fun initAudioTrackIfNeeded() {
        if (audioTrack != null) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                GbcApu.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(GbcApu.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            audioTrack = track
        } catch (_: Exception) {
        }
    }

    fun updateTouchControls(
        up: Boolean,
        down: Boolean,
        left: Boolean,
        right: Boolean,
        a: Boolean,
        b: Boolean,
        start: Boolean,
        select: Boolean
    ) {
        if (_sessionState.value.isEditingControls) return
        inputUp = up
        inputDown = down
        inputLeft = left
        inputRight = right
        inputA = a
        inputB = b
        inputStart = start
        inputSelect = select
    }

    fun handlePhysicalKeyEvent(keyCode: Int, isPressed: Boolean): Boolean {
        val state = _sessionState.value
        if (!state.isRunning || state.isEditingControls) return false

        val remappingBtn = state.buttonBeingRemapped
        if (remappingBtn != null && isPressed) {
            _sessionState.update { current ->
                val updatedMap = current.keyMappings.toMutableMap()
                updatedMap[remappingBtn] = keyCode
                current.copy(
                    keyMappings = updatedMap,
                    buttonBeingRemapped = null,
                    toastMessage = "${remappingBtn.displayName} mapeado para tecla #$keyCode"
                )
            }
            return true
        }

        val matchedButton = state.keyMappings.entries.firstOrNull { it.value == keyCode }?.key
            ?: when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> GbcButton.UP
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> GbcButton.DOWN
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> GbcButton.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> GbcButton.RIGHT
                KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_BUTTON_A -> GbcButton.A
                KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_BUTTON_B -> GbcButton.B
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_START -> GbcButton.START
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_BUTTON_SELECT -> GbcButton.SELECT
                else -> null
            } ?: return false

        when (matchedButton) {
            GbcButton.UP -> inputUp = isPressed
            GbcButton.DOWN -> inputDown = isPressed
            GbcButton.LEFT -> inputLeft = isPressed
            GbcButton.RIGHT -> inputRight = isPressed
            GbcButton.A -> inputA = isPressed
            GbcButton.B -> inputB = isPressed
            GbcButton.START -> inputStart = isPressed
            GbcButton.SELECT -> inputSelect = isPressed
            GbcButton.TURBO -> physicalTurboHeld = isPressed
            GbcButton.QUICK_SAVE -> if (isPressed) performQuickSave()
            GbcButton.QUICK_LOAD -> if (isPressed) performQuickLoad()
        }
        return true
    }

    /**
     * Toggles the rectangular Turbo button ON (2.0x) or OFF (1.0x).
     * Strictly blocked while editing virtual controls (`isEditingControls == true`).
     */
    fun toggleTurboMode() {
        if (_sessionState.value.isEditingControls) return
        _sessionState.update { current ->
            val enableTurbo = current.speedMultiplier <= 1.0f
            val nextSpeed = if (enableTurbo) 2.0f else 1.0f
            current.copy(
                speedMultiplier = nextSpeed,
                toastMessage = if (enableTurbo) "Modo Turbo Ativado" else "Modo Turbo Desativado"
            )
        }
    }

    fun toggleAudio() {
        if (_sessionState.value.isEditingControls) return
        _sessionState.update { current ->
            val next = !current.audioEnabled
            current.copy(
                audioEnabled = next,
                toastMessage = if (next) "Áudio Estéreo GBC Ativado" else "Áudio Silenciado"
            )
        }
    }

    /**
     * Toggles manual emulation pause/resume from the top-right action bar.
     * Strictly blocked while editing virtual controls (`isEditingControls == true`).
     */
    fun togglePause() {
        if (_sessionState.value.isEditingControls) return
        _sessionState.update { current ->
            if (!current.isRunning) return@update current
            val nextPaused = !current.isPaused
            if (nextPaused) {
                pauseAudioTrackSafe()
            } else {
                resumeAudioTrackSafe()
            }
            current.copy(
                isPaused = nextPaused,
                isAutoPausedByLifecycle = false,
                toastMessage = if (nextPaused) "Emulador Pausado" else "Jogo Retomado"
            )
        }
    }

    /**
     * Captures a pure game-only screenshot (directly from the 160x144 GBC/GB framebuffer without
     * any emulator UI or virtual controls) and saves it automatically to the phone's Gallery.
     * Strictly blocked while editing virtual controls (`isEditingControls == true`).
     */
    fun captureGameScreenshotToGallery() {
        if (_sessionState.value.isEditingControls) return
        viewModelScope.launch {
            val rom = _sessionState.value.activeRom ?: return@launch
            val core = emulatorCore
            val frameCopy = synchronized(currentFramePixels) {
                if (core != null) {
                    System.arraycopy(core.ppu.frontBuffer, 0, currentFramePixels, 0, currentFramePixels.size)
                }
                currentFramePixels.copyOf()
            }
            val savedFile = repository.saveScreenshotToGallery(rom.title, frameCopy)
            _sessionState.update { current ->
                current.copy(
                    toastMessage = if (savedFile != null) {
                        "Captura de tela salva na Galeria!"
                    } else {
                        "Erro ao salvar captura de tela"
                    }
                )
            }
        }
    }

    /**
     * Automatically pauses an active game when the emulator app is minimized/backgrounded,
     * and flushes battery SRAM to disk.
     */
    fun onAppMinimized() {
        flushActiveSessionToDisk()
        inputUp = false
        inputDown = false
        inputLeft = false
        inputRight = false
        inputA = false
        inputB = false
        inputStart = false
        inputSelect = false
        physicalTurboHeld = false

        _sessionState.update { current ->
            if (!current.isRunning) return@update current
            pauseAudioTrackSafe()
            if (!current.isPaused) {
                current.copy(
                    isPaused = true,
                    isAutoPausedByLifecycle = true
                )
            } else {
                current
            }
        }
    }

    /**
     * Automatically resumes the game when the user returns to the emulator after minimizing,
     * provided it was auto-paused by minimizing (and not manually paused by the user beforehand).
     */
    fun onAppResumed() {
        _sessionState.update { current ->
            if (current.isRunning && current.isAutoPausedByLifecycle) {
                resumeAudioTrackSafe()
                current.copy(
                    isPaused = false,
                    isAutoPausedByLifecycle = false,
                    toastMessage = "Jogo retomado"
                )
            } else {
                current
            }
        }
    }

    private fun pauseAudioTrackSafe() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {
        }
    }

    private fun resumeAudioTrackSafe() {
        try {
            audioTrack?.play()
        } catch (_: Exception) {
        }
    }

    fun toggleFpsHud() {
        _sessionState.update { current ->
            current.copy(showFpsHud = !current.showFpsHud)
        }
    }

    fun setVideoFilter(filter: VideoFilterMode) {
        _sessionState.update { current ->
            current.copy(
                videoFilter = filter,
                toastMessage = "Filtro aplicado: ${filter.displayName}"
            )
        }
    }

    fun setDmgPalette(preset: DmgPalettePreset) {
        emulatorCore?.ppu?.dmgPalettePreset = preset
        _sessionState.update { current ->
            current.copy(
                dmgPalette = preset,
                toastMessage = "Paleta DMG: ${preset.displayName}"
            )
        }
    }

    fun openModal(modal: EmulatorModal) {
        _sessionState.update { current ->
            current.copy(
                activeModal = modal,
                isEditingControls = false
            )
        }
    }

    fun enterTouchControlsEditor() {
        _sessionState.update { current ->
            current.copy(
                activeModal = EmulatorModal.NONE,
                isEditingControls = true,
                toastMessage = "Arraste os controles na tela ou ajuste tamanho/opacidade"
            )
        }
    }

    fun exitTouchControlsEditor() {
        val snapshot = _sessionState.value
        _sessionState.update { current ->
            current.copy(
                isEditingControls = false,
                toastMessage = "Layout de controles salvo!"
            )
        }
        viewModelScope.launch {
            repository.saveControlLayout(snapshot.portraitLayout)
            repository.saveControlLayout(snapshot.landscapeLayout)
        }
    }

    fun updateControlLayout(isLandscape: Boolean, updated: ControlLayoutEntity) {
        _sessionState.update { current ->
            if (isLandscape) {
                current.copy(landscapeLayout = updated)
            } else {
                current.copy(portraitLayout = updated)
            }
        }
    }

    fun resetControlLayout(isLandscape: Boolean) {
        viewModelScope.launch {
            val reset = repository.resetControlLayout(isLandscape)
            updateControlLayout(isLandscape, reset)
            _sessionState.update { current ->
                current.copy(toastMessage = "Controles restaurados para o padrão")
            }
        }
    }

    fun resetAllControlLayouts() {
        viewModelScope.launch {
            val resetPortrait = repository.resetControlLayout(false)
            val resetLandscape = repository.resetControlLayout(true)
            _sessionState.update { current ->
                current.copy(
                    portraitLayout = resetPortrait,
                    landscapeLayout = resetLandscape,
                    toastMessage = "Controles virtuais restaurados para o padrão"
                )
            }
            _statusBanner.value = "Layout padrão dos controles virtuais restaurado!"
        }
    }

    fun toggleAllHaptics(enabled: Boolean) {
        viewModelScope.launch {
            val snap = _sessionState.value
            val pUpdated = snap.portraitLayout.copy(hapticEnabled = enabled)
            val lUpdated = snap.landscapeLayout.copy(hapticEnabled = enabled)
            repository.saveControlLayout(pUpdated)
            repository.saveControlLayout(lUpdated)
            _sessionState.update { current ->
                current.copy(
                    portraitLayout = pUpdated,
                    landscapeLayout = lUpdated
                )
            }
        }
    }

    // Save States (Quick Save Slot 0 + Manual Slots 1..8)
    fun performQuickSave() {
        if (_sessionState.value.isEditingControls) return
        saveStateToSlot(0, "Quick Save", closeModal = false)
    }

    fun performQuickLoad() {
        if (_sessionState.value.isEditingControls) return
        viewModelScope.launch {
            val rom = _sessionState.value.activeRom ?: return@launch
            val quickState = database.dao().getSaveState(rom.id, 0)
            if (quickState != null) {
                loadStateFromEntity(quickState)
            } else {
                _sessionState.update { current ->
                    current.copy(toastMessage = "Nenhum Quick Save encontrado ainda")
                }
            }
        }
    }

    fun saveStateToSlot(slotIndex: Int, label: String, closeModal: Boolean = true) {
        viewModelScope.launch {
            val rom = _sessionState.value.activeRom ?: return@launch
            val core = emulatorCore ?: return@launch
            val stateBytes = core.serializeState()
            val frameCopy = synchronized(currentFramePixels) { currentFramePixels.copyOf() }
            val elapsedSec = rom.totalPlayTimeSeconds + ((System.currentTimeMillis() - sessionStartTimestamp) / 1000L)

            repository.writeSaveState(
                romId = rom.id,
                slotIndex = slotIndex,
                label = label,
                stateBytes = stateBytes,
                frameBuffer = frameCopy,
                playTimeSeconds = elapsedSec
            )
            repository.saveBatterySram(rom.crc32Hex, core.cartridge.dumpBatterySram())

            _sessionState.update { current ->
                current.copy(
                    activeModal = if (closeModal) EmulatorModal.NONE else current.activeModal,
                    toastMessage = "Estado salvo no Slot $slotIndex!"
                )
            }
        }
    }

    fun loadStateFromEntity(saveState: SaveStateEntity) {
        viewModelScope.launch {
            val core = emulatorCore ?: return@launch
            val bytes = repository.readSaveStateBytes(saveState)
            if (bytes != null && core.deserializeState(bytes)) {
                repository.touchSaveStateTimestamp(saveState)
                synchronized(currentFramePixels) {
                    System.arraycopy(core.ppu.frontBuffer, 0, currentFramePixels, 0, currentFramePixels.size)
                }
                _sessionState.update { current ->
                    current.copy(
                        activeModal = EmulatorModal.NONE,
                        isPaused = false,
                        isAutoPausedByLifecycle = false,
                        frameTick = core.framesExecuted,
                        toastMessage = "Slot ${saveState.slotIndex} carregado com sucesso!"
                    )
                }
            } else {
                _sessionState.update { current ->
                    current.copy(toastMessage = "Falha ao carregar o Save State")
                }
            }
        }
    }

    fun deleteSaveState(saveState: SaveStateEntity) {
        viewModelScope.launch {
            repository.deleteSaveState(saveState)
            _sessionState.update { current ->
                current.copy(toastMessage = "Slot ${saveState.slotIndex} excluído")
            }
        }
    }

    // Cheats (GameShark / Game Genie)
    fun addCheatCode(title: String, code: String) {
        viewModelScope.launch {
            val rom = _sessionState.value.activeRom ?: return@launch
            if (title.isBlank() || code.isBlank()) return@launch
            repository.addCheat(rom.id, title, code)
            refreshCoreCheats(rom.id)
            _sessionState.update { current ->
                current.copy(toastMessage = "Cheat \"$title\" ativado!")
            }
        }
    }

    fun toggleCheatCode(cheat: CheatEntity) {
        viewModelScope.launch {
            val rom = _sessionState.value.activeRom ?: return@launch
            repository.toggleCheat(cheat)
            refreshCoreCheats(rom.id)
        }
    }

    fun deleteCheatCode(cheat: CheatEntity) {
        viewModelScope.launch {
            val rom = _sessionState.value.activeRom ?: return@launch
            repository.deleteCheat(cheat)
            refreshCoreCheats(rom.id)
        }
    }

    private suspend fun refreshCoreCheats(romId: Long) {
        val enabledCodes = database.dao().getCheatsForRomOnce(romId)
            .filter { it.isEnabled }
            .map { it.code }
        emulatorCore?.applyCheatCodes(enabledCodes)
    }

    // Physical Gamepad Remapping
    fun startRemappingButton(button: GbcButton?) {
        _sessionState.update { current ->
            current.copy(buttonBeingRemapped = button)
        }
    }

    fun resetKeyMappings() {
        _sessionState.update { current ->
            current.copy(
                keyMappings = EmulatorSessionState.defaultKeyMappings(),
                buttonBeingRemapped = null,
                toastMessage = "Mapeamento de controle físico restaurado"
            )
        }
    }

    fun resetCurrentGame() {
        emulatorCore?.reset()
        _sessionState.update { current ->
            current.copy(
                activeModal = EmulatorModal.NONE,
                isPaused = false,
                isAutoPausedByLifecycle = false,
                toastMessage = "Jogo reiniciado"
            )
        }
    }

    fun clearToastMessage() {
        _sessionState.update { current ->
            current.copy(toastMessage = null)
        }
    }

    fun flushActiveSessionToDisk() {
        val rom = _sessionState.value.activeRom ?: return
        val core = emulatorCore ?: return
        try {
            repository.saveBatterySram(rom.crc32Hex, core.cartridge.dumpBatterySram())
        } catch (_: Exception) {
        }
    }

    fun exitToLibrary() {
        viewModelScope.launch {
            stopEmulationInternal()
        }
    }

    private suspend fun stopEmulationInternal() {
        val rom = _sessionState.value.activeRom
        val core = emulatorCore
        emulationJob?.cancel()
        emulationJob = null

        if (rom != null && core != null) {
            repository.saveBatterySram(rom.crc32Hex, core.cartridge.dumpBatterySram())
            val sessionSec = ((System.currentTimeMillis() - sessionStartTimestamp) / 1000L).coerceAtLeast(1L)
            repository.recordPlaySession(rom.id, sessionSec)
        }

        emulatorCore = null
        _activeRomId.value = null
        _sessionState.update { current ->
            current.copy(
                activeRom = null,
                isRunning = false,
                isPaused = false,
                isAutoPausedByLifecycle = false,
                activeModal = EmulatorModal.NONE,
                isEditingControls = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        emulationJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
    }
}
