package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RomCoverArtView
import com.example.data.RomEntity
import com.example.data.SaveStateEntity
import com.example.emulator.DmgPalettePreset
import com.example.emulator.VideoFilterMode
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.EmulatorSessionState
import com.example.viewmodel.LibraryFilter
import com.example.viewmodel.LibraryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    sessionState: EmulatorSessionState = EmulatorSessionState(),
    onSearchChange: (String) -> Unit,
    onFilterSelect: (LibraryFilter) -> Unit,
    onAddDirectoryClick: () -> Unit,
    onImportRomFileClick: () -> Unit,
    onPlayRom: (RomEntity) -> Unit,
    onContinueRom: (RomEntity) -> Unit = {},
    onToggleFavorite: (RomEntity) -> Unit,
    onOpenCoverMenu: (RomEntity) -> Unit,
    onDismissCoverMenu: () -> Unit,
    onPickCustomCover: (RomEntity) -> Unit,
    onRestoreAutoCover: (RomEntity) -> Unit,
    onRenameRom: (RomEntity, String) -> Unit = { _, _ -> },
    onDeleteRom: (RomEntity) -> Unit,
    onDismissBanner: () -> Unit,
    onSelectVideoFilter: (VideoFilterMode) -> Unit = {},
    onSelectDmgPalette: (DmgPalettePreset) -> Unit = {},
    onToggleAudio: () -> Unit = {},
    onToggleFpsHud: () -> Unit = {},
    onToggleHaptics: (Boolean) -> Unit = {},
    onResetAllControlLayouts: () -> Unit = {}
) {
    var showSettingsModal by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .testTag("library_screen"),
        containerColor = OledBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF141829),
                                OledBlack
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(NeonCyan, AtomicPurple)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = NeoIcons.Gamepad,
                                contentDescription = "Ícone NeoGBC",
                                tint = OledBlack,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "NeoGBC",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NeonCyan.copy(alpha = 0.16f))
                                        .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "GB / GBC • 120Hz",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyan,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                text = "${uiState.roms.size} ROM(s) prontas • Engine LR35902 CGB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Top-right button opens Emulator Settings directly from the home screen
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(OledSurfaceVariant)
                            .border(1.5.dp, NeonCyan.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .clickable { showSettingsModal = true }
                            .testTag("library_settings_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurações do Emulador",
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAddDirectoryClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = OledBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1.15f)
                            .testTag("add_directory_button")
                    ) {
                        Icon(
                            imageVector = NeoIcons.CreateNewFolder,
                            contentDescription = "Adicionar diretório de jogos",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Escolher Diretório",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onImportRomFileClick,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_rom_button")
                    ) {
                        Icon(
                            imageVector = NeoIcons.FileOpen,
                            contentDescription = "Importar arquivo ROM ou ZIP",
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Importar .GBC/.ZIP",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            text = "Buscar jogo por título, cabeçalho ou CRC...",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = NeonCyan
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar busca",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = OledCardBorder,
                        focusedContainerColor = OledSurface,
                        unfocusedContainerColor = OledSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_rom_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(LibraryFilter.entries) { filter ->
                        val selected = uiState.selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterSelect(filter) },
                            label = {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AtomicPurple.copy(alpha = 0.28f),
                                selectedLabelColor = NeonCyan,
                                containerColor = OledSurface,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = OledCardBorder,
                                selectedBorderColor = NeonCyan
                            ),
                            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                        )
                    }
                }

                if (uiState.isScanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        color = NeonCyan,
                        trackColor = OledSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AnimatedVisibility(visible = uiState.statusBanner != null) {
                    uiState.statusBanner?.let { msg ->
                        Surface(
                            color = OledSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeonCyan,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onDismissBanner,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar aviso",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .testTag("rom_grid")
        ) {
            if (uiState.directories.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ScannedDirectoriesStrip(
                        directories = uiState.directories.map { "${it.displayName} (${it.romCount})" },
                        onRescanClick = onAddDirectoryClick
                    )
                }
            }

            items(uiState.roms, key = { it.id }) { rom ->
                val latestSave = uiState.latestSaveByRomId[rom.id]
                RomLibraryCard(
                    rom = rom,
                    latestSave = latestSave,
                    onPlayClick = { onPlayRom(rom) },
                    onContinueClick = { onContinueRom(rom) },
                    onFavoriteClick = { onToggleFavorite(rom) },
                    onCoverOptionsClick = { onOpenCoverMenu(rom) }
                )
            }
        }
    }

    // Emulator Settings Bottom Sheet accessible directly from the Home / Library Screen
    if (showSettingsModal) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsModal = false },
            containerColor = OledSurface,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .testTag("library_settings_sheet"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Configurações do Emulador",
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ajustes globais de vídeo, áudio e controles",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = { showSettingsModal = false },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(OledSurfaceVariant)
                            .testTag("close_library_settings_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar configurações", tint = TextPrimary)
                    }
                }

                // 1. Audio, FPS & Haptics Toggles (Velocidade Padrão de Emulação removed as requested)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(OledSurfaceVariant)
                        .border(1.dp, OledCardBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Áudio Estéreo GBC (44.1 kHz)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Sintetizador APU de 4 canais em tempo real",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = sessionState.audioEnabled,
                            onCheckedChange = { onToggleAudio() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OledBlack,
                                checkedTrackColor = NeonCyan
                            ),
                            modifier = Modifier.testTag("settings_audio_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Contador de FPS na Tela",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Mostra taxa de quadros no topo durante o jogo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = sessionState.showFpsHud,
                            onCheckedChange = { onToggleFpsHud() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OledBlack,
                                checkedTrackColor = NeonCyan
                            ),
                            modifier = Modifier.testTag("settings_fps_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vibração Tátil nos Botões",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Resposta háptica ao pressionar D-Pad, A, B, Start/Select",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = sessionState.landscapeLayout.hapticEnabled,
                            onCheckedChange = { onToggleHaptics(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OledBlack,
                                checkedTrackColor = NeonCyan
                            ),
                            modifier = Modifier.testTag("settings_haptics_switch")
                        )
                    }
                }

                // 2. Video Filter Mode
                Text(
                    text = "Filtro Visual de Tela (Shader)",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
                VideoFilterMode.entries.forEach { mode ->
                    val selected = mode == sessionState.videoFilter
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
                            .clickable { onSelectVideoFilter(mode) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) NeonCyan else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
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

                // 3. DMG Palette Preset
                Text(
                    text = "Paleta de Cores (Jogos GB Clássicos)",
                    style = MaterialTheme.typography.titleMedium,
                    color = AtomicPurple,
                    fontWeight = FontWeight.Bold
                )
                DmgPalettePreset.entries.forEach { preset ->
                    val selected = preset == sessionState.dmgPalette
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
                            .clickable { onSelectDmgPalette(preset) }
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
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                )
                            }
                        }
                    }
                }

                // 4. Reset Virtual Controls Button
                OutlinedButton(
                    onClick = {
                        onResetAllControlLayouts()
                        showSettingsModal = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_reset_all_controls_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restaurar controles na tela",
                        tint = NeonCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Restaurar Layout Padrão dos Controles Virtuais",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Cover, Rename & Delete ROM Options Bottom Sheet
    uiState.romForCoverMenu?.let { selectedRom ->
        var isRenaming by remember(selectedRom.id) { mutableStateOf(false) }
        var renameText by remember(selectedRom.id) { mutableStateOf(selectedRom.title) }

        ModalBottomSheet(
            onDismissRequest = onDismissCoverMenu,
            containerColor = OledSurface,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("rom_options_sheet"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = selectedRom.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Arquivo: ${selectedRom.fileName} • ${selectedRom.cartridgeType} • CRC ${selectedRom.crc32Hex}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                if (isRenaming) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OledSurfaceVariant)
                            .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Novo nome do jogo e do arquivo no dispositivo:",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonCyan
                        )
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = OledCardBorder,
                                focusedContainerColor = OledBlack,
                                unfocusedContainerColor = OledBlack,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rename_rom_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { isRenaming = false },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancelar", color = TextSecondary)
                            }
                            Button(
                                onClick = {
                                    if (renameText.isNotBlank()) {
                                        onRenameRom(selectedRom, renameText.trim())
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = OledBlack
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("confirm_rename_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salvar Nome", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { isRenaming = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_rom_button")
                    ) {
                        Icon(
                            imageVector = NeoIcons.Edit,
                            contentDescription = "Renomear jogo e arquivo",
                            tint = CyberYellow
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Renomear Jogo (e Arquivo no Dispositivo)",
                            color = CyberYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = { onPickCustomCover(selectedRom) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = OledBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_custom_cover_button")
                ) {
                    Icon(
                        imageVector = NeoIcons.AddPhoto,
                        contentDescription = "Escolher capa da galeria"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Escolher Capa da Galeria (Foto)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { onRestoreAutoCover(selectedRom) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_auto_cover_button")
                ) {
                    Icon(
                        imageVector = NeoIcons.AutoAwesome,
                        contentDescription = "Capa automática",
                        tint = MatrixBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Restaurar Capa Automática do Emulador",
                        color = TextPrimary
                    )
                }

                OutlinedButton(
                    onClick = { onDeleteRom(selectedRom) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_rom_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir jogo da biblioteca",
                        tint = BerryMagenta
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Excluir Jogo da Biblioteca",
                        color = BerryMagenta,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ScannedDirectoriesStrip(
    directories: List<String>,
    onRescanClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OledSurface)
            .border(1.dp, OledCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = NeoIcons.Folder,
                contentDescription = "Diretórios monitorados",
                tint = AtomicPurple,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pastas: ${directories.joinToString(" • ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "+ Pasta",
            style = MaterialTheme.typography.labelLarge,
            color = NeonCyan,
            modifier = Modifier
                .clickable { onRescanClick() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RomLibraryCard(
    rom: RomEntity,
    latestSave: SaveStateEntity?,
    onPlayClick: () -> Unit,
    onContinueClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onCoverOptionsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0.45f),
                        AtomicPurple.copy(alpha = 0.35f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onPlayClick() }
            .testTag("rom_card_${rom.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OledSurface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.95f)
            ) {
                RomCoverArtView(
                    rom = rom,
                    modifier = Modifier.fillMaxSize()
                )

                // Top floating action pills (Favorite + Options Menu)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(OledBlack.copy(alpha = 0.72f))
                            .testTag("favorite_btn_${rom.id}")
                    ) {
                        Icon(
                            imageVector = if (rom.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favoritar ${rom.title}",
                            tint = if (rom.isFavorite) BerryMagenta else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onCoverOptionsClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(OledBlack.copy(alpha = 0.72f))
                            .testTag("cover_options_btn_${rom.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opções de ${rom.title}",
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledSurface)
                    .padding(12.dp)
            ) {
                Text(
                    text = rom.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                val minutes = (rom.totalPlayTimeSeconds / 60L)
                if (latestSave != null) {
                    val slotLabel = if (latestSave.slotIndex == 0) "Quick Save" else "Slot ${latestSave.slotIndex}"
                    Text(
                        text = if (minutes > 0) "${minutes}m jogados • $slotLabel" else "Salvo • $slotLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSuccess,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1.25f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldSuccess.copy(alpha = 0.22f))
                                .border(1.dp, EmeraldSuccess.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .testTag("continue_rom_btn_${rom.id}")
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { onContinueClick() }
                                .padding(horizontal = 6.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Continuar",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "CONTINUAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(0.85f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.14f))
                                .testTag("play_rom_btn_${rom.id}")
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { onPlayClick() }
                                .padding(horizontal = 6.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "JOGAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (minutes > 0) "${minutes}m jogados" else "Pronto",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.18f))
                                .testTag("play_rom_btn_${rom.id}")
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { onPlayClick() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Jogar",
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "JOGAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
