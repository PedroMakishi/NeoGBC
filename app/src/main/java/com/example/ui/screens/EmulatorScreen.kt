package com.example.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CheatEntity
import com.example.data.ControlLayoutEntity
import com.example.data.SaveStateEntity
import com.example.emulator.DmgPalettePreset
import com.example.emulator.GbcPpu
import com.example.emulator.VideoFilterMode
import com.example.ui.components.VirtualGamepadOverlay
import com.example.ui.theme.AtomicPurple
import com.example.ui.theme.BerryMagenta
import com.example.ui.theme.CyberYellow
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MatrixBlue
import com.example.ui.theme.NeoIcons
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.OledCardBorder
import com.example.ui.theme.OledSurface
import com.example.ui.theme.OledSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.EmulatorModal
import com.example.viewmodel.EmulatorSessionState
import com.example.viewmodel.GbcButton
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorScreen(
    sessionState: EmulatorSessionState,
    framePixels: IntArray,
    saveStates: List<SaveStateEntity>,
    cheats: List<CheatEntity>,
    onTouchInputChanged: (
        up: Boolean,
        down: Boolean,
        left: Boolean,
        right: Boolean,
        a: Boolean,
        b: Boolean,
        start: Boolean,
        select: Boolean
    ) -> Unit,
    onToggleTurbo: () -> Unit,
    onToggleAudio: () -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onOpenModal: (EmulatorModal) -> Unit,
    onEnterEditControls: () -> Unit,
    onExitEditControls: () -> Unit,
    onUpdateControlLayout: (Boolean, ControlLayoutEntity) -> Unit,
    onResetControlLayout: (Boolean) -> Unit,
    onSaveToSlot: (Int, String) -> Unit,
    onLoadFromState: (SaveStateEntity) -> Unit,
    onDeleteSaveState: (SaveStateEntity) -> Unit,
    onAddCheat: (String, String) -> Unit,
    onToggleCheat: (CheatEntity) -> Unit,
    onDeleteCheat: (CheatEntity) -> Unit,
    onSelectVideoFilter: (VideoFilterMode) -> Unit,
    onSelectDmgPalette: (DmgPalettePreset) -> Unit,
    onStartRemapButton: (GbcButton?) -> Unit,
    onResetKeyMappings: () -> Unit,
    onResetGame: () -> Unit,
    onClearToast: () -> Unit,
    onExitToLibrary: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activeControlLayout = if (isLandscape) sessionState.landscapeLayout else sessionState.portraitLayout

    BackHandler {
        when {
            sessionState.isEditingControls -> onExitEditControls()
            sessionState.activeModal != EmulatorModal.NONE -> onOpenModal(EmulatorModal.NONE)
            else -> onOpenModal(EmulatorModal.QUICK_MENU)
        }
    }

    LaunchedEffect(sessionState.toastMessage) {
        if (sessionState.toastMessage != null) {
            delay(2500L)
            onClearToast()
        }
    }

    val screenBitmap = remember {
        Bitmap.createBitmap(
            GbcPpu.SCREEN_WIDTH,
            GbcPpu.SCREEN_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .testTag("emulator_screen")
    ) {
        val availW = maxWidth
        val availH = maxHeight
        val density = androidx.compose.ui.platform.LocalDensity.current
        val gbcAspect = GbcPpu.SCREEN_WIDTH.toFloat() / GbcPpu.SCREEN_HEIGHT.toFloat() // 10:9 (1.1111)

        // Calculate maximum possible size that preserves the exact 160:144 aspect ratio without stretching
        val topHudReserved = if (isLandscape) 0.dp else 46.dp
        val maxGameH = (availH - topHudReserved).coerceAtLeast(100.dp)
        val widthLimitedH = availW / gbcAspect

        val (gameWidthDp, gameHeightDp) = if (widthLimitedH <= maxGameH) {
            availW to widthLimitedH
        } else {
            (maxGameH * gbcAspect) to maxGameH
        }

        val availWpx = with(density) { availW.toPx() }
        val gameWpx = with(density) { gameWidthDp.toPx() }
        val gameHpx = with(density) { gameHeightDp.toPx() }
        val topHudPx = with(density) { (if (isLandscape) 46.dp else topHudReserved).toPx() }

        val gameLeftPx = (availWpx - gameWpx) / 2f
        val gameTopPx = if (isLandscape) 0f else topHudPx
        val gameRectPx = androidx.compose.ui.geometry.Rect(
            left = gameLeftPx,
            top = gameTopPx,
            right = gameLeftPx + gameWpx,
            bottom = gameTopPx + gameHpx
        )

        // 1. Game Boy Color LCD Display — fills maximum possible screen space keeping 160:144 aspect ratio
        Box(
            modifier = Modifier
                .size(gameWidthDp, gameHeightDp)
                .then(
                    if (isLandscape) {
                        Modifier.align(Alignment.Center)
                    } else {
                        Modifier
                            .padding(top = topHudReserved)
                            .align(Alignment.TopCenter)
                    }
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.45f), AtomicPurple.copy(alpha = 0.45f))
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
                .testTag("gbc_lcd_display")
        ) {
            val tick = sessionState.frameTick
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                if (tick >= 0L) {
                    synchronized(framePixels) {
                        screenBitmap.setPixels(
                            framePixels,
                            0,
                            GbcPpu.SCREEN_WIDTH,
                            0,
                            0,
                            GbcPpu.SCREEN_WIDTH,
                            GbcPpu.SCREEN_HEIGHT
                        )
                    }
                }

                val dstW = size.width.toInt().coerceAtLeast(1)
                val dstH = size.height.toInt().coerceAtLeast(1)
                val filterQuality = if (sessionState.videoFilter == VideoFilterMode.SOFT_BILINEAR) {
                    FilterQuality.Low
                } else {
                    FilterQuality.None
                }

                drawImage(
                    image = screenBitmap.asImageBitmap(),
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(GbcPpu.SCREEN_WIDTH, GbcPpu.SCREEN_HEIGHT),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(dstW, dstH),
                    filterQuality = filterQuality
                )

                when (sessionState.videoFilter) {
                    VideoFilterMode.LCD_GRID -> {
                        val cellW = size.width / GbcPpu.SCREEN_WIDTH
                        val cellH = size.height / GbcPpu.SCREEN_HEIGHT
                        val gridColor = Color.Black.copy(alpha = 0.22f)
                        for (gx in 1 until GbcPpu.SCREEN_WIDTH step 2) {
                            val x = gx * cellW
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        }
                        for (gy in 1 until GbcPpu.SCREEN_HEIGHT step 2) {
                            val y = gy * cellH
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        }
                    }
                    VideoFilterMode.CRT_SCANLINES -> {
                        val lineH = size.height / GbcPpu.SCREEN_HEIGHT
                        val scanColor = Color.Black.copy(alpha = 0.34f)
                        for (gy in 0 until GbcPpu.SCREEN_HEIGHT) {
                            val y = gy * lineH
                            drawLine(scanColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
                        }
                    }
                    else -> Unit
                }
            }
        }

        // 2. Virtual Gamepad Touch Controls Overlay & Layout Editor (includes rectangular Turbo & Quick Action Bar)
        VirtualGamepadOverlay(
            layout = activeControlLayout,
            isEditing = sessionState.isEditingControls,
            isTurboActive = sessionState.isTurboEnabled,
            audioEnabled = sessionState.audioEnabled,
            gameRect = gameRectPx,
            topHudHeightPx = topHudPx,
            onLayoutChange = { updated -> onUpdateControlLayout(isLandscape, updated) },
            onResetLayout = { onResetControlLayout(isLandscape) },
            onSaveLayout = onExitEditControls,
            onToggleTurbo = onToggleTurbo,
            onQuickSave = onQuickSave,
            onQuickLoad = onQuickLoad,
            onToggleAudio = onToggleAudio,
            onInputChanged = onTouchInputChanged,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Top-Left Menu Button & FPS HUD — drawn after VirtualGamepadOverlay so the 3-bar Menu button
        // is always accessible in the top-left corner.
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OledSurfaceVariant.copy(alpha = 0.95f))
                        .border(1.5.dp, NeonCyan.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                        .clickable { onOpenModal(EmulatorModal.QUICK_MENU) }
                        .testTag("emulator_menu_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu do Emulador",
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (sessionState.showFpsHud) {
                    Text(
                        text = "${sessionState.currentFps} FPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        fontSize = 10.sp
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OledSurfaceVariant)
                        .border(1.5.dp, NeonCyan.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                        .clickable { onOpenModal(EmulatorModal.QUICK_MENU) }
                        .testTag("emulator_menu_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu do Emulador",
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = sessionState.activeRom?.title ?: "NeoGBC",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (sessionState.showFpsHud) {
                        Text(
                            text = "${sessionState.currentFps} FPS • ${sessionState.videoFilter.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // 4. Toast Notification Pill
        AnimatedVisibility(
            visible = sessionState.toastMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
        ) {
            sessionState.toastMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(OledSurface.copy(alpha = 0.94f))
                        .border(1.dp, NeonCyan, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyan
                    )
                }
            }
        }
        // 5. Modals (Quick Menu, Save States, Cheats, Video Filters, Physical Gamepad Remap)
        // Rendered inside the main full-screen BoxWithConstraints so Android system bars never pop up
        // and the menu works identically in both Portrait and Landscape orientations.
        when (sessionState.activeModal) {
            EmulatorModal.QUICK_MENU -> {
                QuickMenuSheet(
                    onDismiss = { onOpenModal(EmulatorModal.NONE) },
                    onOpenSaveStates = { onOpenModal(EmulatorModal.SAVE_STATES) },
                    onEditTouchControls = onEnterEditControls,
                    onOpenVideoFilters = { onOpenModal(EmulatorModal.VIDEO_FILTERS) },
                    onOpenCheats = { onOpenModal(EmulatorModal.CHEATS) },
                    onOpenGamepadRemap = { onOpenModal(EmulatorModal.GAMEPAD_REMAP) },
                    onResetGame = onResetGame,
                    onExitToLibrary = onExitToLibrary
                )
            }
            EmulatorModal.SAVE_STATES -> {
                SaveStatesSheet(
                    saveStates = saveStates,
                    onDismiss = { onOpenModal(EmulatorModal.NONE) },
                    onSaveSlot = onSaveToSlot,
                    onLoadState = onLoadFromState,
                    onDeleteState = onDeleteSaveState
                )
            }
            EmulatorModal.CHEATS -> {
                CheatsSheet(
                    cheats = cheats,
                    onDismiss = { onOpenModal(EmulatorModal.NONE) },
                    onAddCheat = onAddCheat,
                    onToggleCheat = onToggleCheat,
                    onDeleteCheat = onDeleteCheat
                )
            }
            EmulatorModal.VIDEO_FILTERS -> {
                VideoFiltersSheet(
                    activeFilter = sessionState.videoFilter,
                    activePalette = sessionState.dmgPalette,
                    onDismiss = { onOpenModal(EmulatorModal.NONE) },
                    onSelectFilter = onSelectVideoFilter,
                    onSelectPalette = onSelectDmgPalette
                )
            }
            EmulatorModal.GAMEPAD_REMAP -> {
                GamepadRemapSheet(
                    keyMappings = sessionState.keyMappings,
                    buttonBeingRemapped = sessionState.buttonBeingRemapped,
                    onDismiss = {
                        onStartRemapButton(null)
                        onOpenModal(EmulatorModal.NONE)
                    },
                    onSelectButtonToRemap = onStartRemapButton,
                    onResetDefaults = onResetKeyMappings
                )
            }
            EmulatorModal.NONE -> Unit
        }
    }
}

@Composable
private fun NeoGbcModalOverlay(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Sibling backdrop scrim so the modal card itself does NOT need Modifier.clickable
        // (which would merge child semantics and hide descendant testTags).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss
                )
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(OledSurface)
                .border(1.5.dp, NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun QuickMenuSheet(
    onDismiss: () -> Unit,
    onOpenSaveStates: () -> Unit,
    onEditTouchControls: () -> Unit,
    onOpenVideoFilters: () -> Unit,
    onOpenCheats: () -> Unit,
    onOpenGamepadRemap: () -> Unit,
    onResetGame: () -> Unit,
    onExitToLibrary: () -> Unit
) {
    NeoGbcModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .testTag("quick_menu_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Menu do Emulador NeoGBC",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(OledSurfaceVariant)
                        .testTag("close_quick_menu_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar Menu",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            QuickMenuButton(
                icon = NeoIcons.Save,
                title = "Save States (8 Slots + Captura de Tela)",
                subtitle = "Salvar ou carregar progresso instantaneamente",
                accent = EmeraldSuccess,
                testTag = "menu_save_states_btn",
                onClick = onOpenSaveStates
            )

            QuickMenuButton(
                icon = NeoIcons.OpenWith,
                title = "Editar Controles na Tela",
                subtitle = "Arrastar posição, tamanho, opacidade e vibração tátil",
                accent = NeonCyan,
                testTag = "menu_edit_controls_btn",
                onClick = onEditTouchControls
            )

            QuickMenuButton(
                icon = NeoIcons.Palette,
                title = "Filtros de Tela e Paletas GB",
                subtitle = "LCD Grid, Scanlines CRT, Nitidez e Paletas Clássicas",
                accent = AtomicPurple,
                testTag = "menu_video_filters_btn",
                onClick = onOpenVideoFilters
            )

            QuickMenuButton(
                icon = NeoIcons.Code,
                title = "Cheats (GameShark & Game Genie)",
                subtitle = "Ativar códigos de trapaça em tempo real",
                accent = CyberYellow,
                testTag = "menu_cheats_btn",
                onClick = onOpenCheats
            )

            QuickMenuButton(
                icon = NeoIcons.Gamepad,
                title = "Controle Físico Bluetooth / USB",
                subtitle = "Remapear botões de controles externos",
                accent = MatrixBlue,
                testTag = "menu_gamepad_remap_btn",
                onClick = onOpenGamepadRemap
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onResetGame,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("menu_reset_game_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = CyberYellow)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reiniciar", color = CyberYellow)
                }

                Button(
                    onClick = onExitToLibrary,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BerryMagenta,
                        contentColor = OledBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("menu_exit_library_btn")
                ) {
                    Icon(NeoIcons.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Biblioteca", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun QuickMenuButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OledSurfaceVariant)
            .border(1.dp, OledCardBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = accent)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SaveStatesSheet(
    saveStates: List<SaveStateEntity>,
    onDismiss: () -> Unit,
    onSaveSlot: (Int, String) -> Unit,
    onLoadState: (SaveStateEntity) -> Unit,
    onDeleteState: (SaveStateEntity) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    NeoGbcModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .testTag("save_states_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gerenciador de Save States",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeonCyan
                    )
                    Text(
                        text = "Salve ou restaure o estado exato da memória + bateria (.sav)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(OledSurfaceVariant)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextPrimary)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                items((0..8).toList()) { slotIdx ->
                    val existing = saveStates.firstOrNull { it.slotIndex == slotIdx }
                    val slotTitle = if (slotIdx == 0) "Slot 0 • Quick Save" else "Slot $slotIdx"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(OledSurfaceVariant)
                            .border(
                                1.dp,
                                if (existing != null) NeonCyan.copy(alpha = 0.45f) else OledCardBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(10.dp)
                            .testTag("save_slot_row_$slotIdx"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(68.dp)
                                .aspectRatio(160f / 144f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OledBlack)
                                .border(1.dp, OledCardBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (existing?.previewImagePath != null && File(existing.previewImagePath).exists()) {
                                AsyncImage(
                                    model = File(existing.previewImagePath),
                                    contentDescription = "Preview $slotTitle",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "VAZIO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = slotTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (existing != null) {
                                    "Salvo em ${dateFormat.format(Date(existing.timestamp))}"
                                } else {
                                    "Disponível para gravação"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (existing != null) NeonCyan else TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onSaveSlot(slotIdx, slotTitle) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = OledBlack
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("save_slot_btn_$slotIdx")
                            ) {
                                Text("Salvar", style = MaterialTheme.typography.labelLarge)
                            }

                            if (existing != null) {
                                OutlinedButton(
                                    onClick = { onLoadState(existing) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("load_slot_btn_$slotIdx")
                                ) {
                                    Text("Abrir", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                }

                                IconButton(
                                    onClick = { onDeleteState(existing) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir slot",
                                        tint = BerryMagenta
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheatsSheet(
    cheats: List<CheatEntity>,
    onDismiss: () -> Unit,
    onAddCheat: (String, String) -> Unit,
    onToggleCheat: (CheatEntity) -> Unit,
    onDeleteCheat: (CheatEntity) -> Unit
) {
    var cheatTitle by remember { mutableStateOf("") }
    var cheatCode by remember { mutableStateOf("") }

    NeoGbcModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .testTag("cheats_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cheats (GameShark & Game Genie)",
                        style = MaterialTheme.typography.titleLarge,
                        color = CyberYellow
                    )
                    Text(
                        text = "Formatos: GameShark (01XXYYZZ) ou Game Genie (XXX-YYY-ZZZ)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(OledSurfaceVariant)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextPrimary)
                }
            }

            OutlinedTextField(
                value = cheatTitle,
                onValueChange = { cheatTitle = it },
                label = { Text("Descrição (ex: Vidas Infinitas)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cheat_title_input")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = cheatCode,
                    onValueChange = { cheatCode = it.uppercase() },
                    label = { Text("Código (ex: 019900C0)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cheat_code_input")
                )

                Button(
                    onClick = {
                        if (cheatTitle.isNotBlank() && cheatCode.isNotBlank()) {
                            onAddCheat(cheatTitle, cheatCode)
                            cheatTitle = ""
                            cheatCode = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberYellow,
                        contentColor = OledBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("add_cheat_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar Cheat")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Adicionar", fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 200.dp)
            ) {
                items(cheats, key = { it.id }) { cheat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OledSurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cheat.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = cheat.code,
                                style = MaterialTheme.typography.labelLarge,
                                color = CyberYellow
                            )
                        }

                        Switch(
                            checked = cheat.isEnabled,
                            onCheckedChange = { onToggleCheat(cheat) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OledBlack,
                                checkedTrackColor = CyberYellow
                            )
                        )

                        IconButton(onClick = { onDeleteCheat(cheat) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remover Cheat",
                                tint = BerryMagenta
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoFiltersSheet(
    activeFilter: VideoFilterMode,
    activePalette: DmgPalettePreset,
    onDismiss: () -> Unit,
    onSelectFilter: (VideoFilterMode) -> Unit,
    onSelectPalette: (DmgPalettePreset) -> Unit
) {
    NeoGbcModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .testTag("video_filters_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtros de Tela (Shaders)",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonCyan
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(OledSurfaceVariant)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextPrimary)
                }
            }

            VideoFilterMode.entries.forEach { mode ->
                val selected = mode == activeFilter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) NeonCyan.copy(alpha = 0.16f) else OledSurfaceVariant)
                        .border(
                            1.dp,
                            if (selected) NeonCyan else OledCardBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectFilter(mode) }
                        .padding(10.dp)
                        .testTag("filter_option_${mode.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) NeonCyan else TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = mode.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    if (selected) {
                        Text("ATIVO", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Paleta de Cores (Jogos GB Clássicos)",
                style = MaterialTheme.typography.titleMedium,
                color = AtomicPurple
            )

            DmgPalettePreset.entries.forEach { preset ->
                val selected = preset == activePalette
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AtomicPurple.copy(alpha = 0.2f) else OledSurfaceVariant)
                        .border(
                            1.dp,
                            if (selected) AtomicPurple else OledCardBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectPalette(preset) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = preset.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        preset.colors.forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GamepadRemapSheet(
    keyMappings: Map<GbcButton, Int>,
    buttonBeingRemapped: GbcButton?,
    onDismiss: () -> Unit,
    onSelectButtonToRemap: (GbcButton?) -> Unit,
    onResetDefaults: () -> Unit
) {
    NeoGbcModalOverlay(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .testTag("gamepad_remap_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Controles Bluetooth / USB",
                        style = MaterialTheme.typography.titleLarge,
                        color = MatrixBlue
                    )
                    Text(
                        text = if (buttonBeingRemapped != null) {
                            "Pressione qualquer botão no controle para mapear ${buttonBeingRemapped.displayName}..."
                        } else {
                            "Toque em uma função abaixo para remapear seu gamepad físico"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (buttonBeingRemapped != null) NeonCyan else TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onResetDefaults,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Padrão", color = TextPrimary, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(OledSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextPrimary)
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 260.dp)
            ) {
                items(GbcButton.entries) { btn ->
                    val waiting = buttonBeingRemapped == btn
                    val keyCode = keyMappings[btn] ?: 0
                    val keyName = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (waiting) NeonCyan.copy(alpha = 0.2f) else OledSurfaceVariant)
                            .border(
                                1.dp,
                                if (waiting) NeonCyan else OledCardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectButtonToRemap(if (waiting) null else btn) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = btn.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(OledBlack)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (waiting) "AGUARDANDO TECLA..." else keyName,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (waiting) CyberYellow else NeonCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
