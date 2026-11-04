package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `library screen displays imported ROM, settings sheet without default speed, and rename or delete options`() {
        composeTestRule.activity.viewModel.importRawRomBytes(
            "NeoGBC Test Demo.gbc",
            ExampleUnitTest.createSyntheticGbcRomZipBytes()
        )
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule
                .onAllNodesWithText("NeoGBC Test Demo", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag("library_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("library_settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_directory_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_rom_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("NeoGBC Test Demo", substring = true).assertIsDisplayed()

        // 1. Open Emulator Settings on Library screen and verify "Velocidade Padrão de Emulação" was removed
        composeTestRule.onNodeWithTag("library_settings_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule
                .onAllNodesWithTag("library_settings_sheet")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("library_settings_sheet").assertIsDisplayed()
        assertTrue(
            "Velocidade Padrão de Emulação must not exist in library settings",
            composeTestRule.onAllNodesWithText("Velocidade Padrão de Emulação").fetchSemanticsNodes().isEmpty()
        )
        composeTestRule.onNodeWithTag("close_library_settings_btn").performClick()
        composeTestRule.waitForIdle()

        val romId1 = composeTestRule.activity.viewModel.libraryState.value.roms
            .first { it.title.contains("NeoGBC Test Demo", ignoreCase = true) }
            .id

        // 2. Open Game Options menu (3 dots on the ROM card) and test Rename Game (and file on device)
        composeTestRule.onNodeWithTag("cover_options_btn_$romId1").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule
                .onAllNodesWithTag("rename_rom_button")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("rename_rom_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_rom_button").assertExists()

        composeTestRule.onNodeWithTag("rename_rom_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rename_rom_input").performTextReplacement("NeoGBC Demo DX")
        composeTestRule.onNodeWithTag("confirm_rename_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule
                .onAllNodesWithText("NeoGBC Demo DX", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("NeoGBC Demo DX", substring = true).assertIsDisplayed()
    }

    @Test
    fun `emulator screen rectangular Turbo button, control editor lock, save states confirmation, and library Continuar flow`() {
        composeTestRule.activity.viewModel.importRawRomBytes(
            "NeoGBC Test Demo.gbc",
            ExampleUnitTest.createSyntheticGbcRomZipBytes()
        )
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule
                .onAllNodesWithText("NeoGBC Test Demo", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val romId = composeTestRule.activity.viewModel.libraryState.value.roms.first().id

        // 1. Before any save exists, "CONTINUAR" button must NOT be displayed on the library card
        assertTrue(
            "Continuar button must NOT appear before the user has made a save",
            composeTestRule.onAllNodesWithTag("continue_rom_btn_$romId", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        )
        composeTestRule.onNodeWithTag("play_rom_btn_$romId", useUnmergedTree = true).assertExists()

        // 2. Launch the ROM
        composeTestRule.onNodeWithText("NeoGBC Test Demo", substring = true).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule
                .onAllNodesWithTag("emulator_screen")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag("emulator_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gbc_lcd_display").assertIsDisplayed()
        composeTestRule.onNodeWithTag("virtual_dpad").assertIsDisplayed()
        composeTestRule.onNodeWithTag("virtual_ab_cluster").assertIsDisplayed()
        composeTestRule.onNodeWithTag("virtual_start_select").assertIsDisplayed()
        composeTestRule.onNodeWithTag("virtual_turbo_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_toggle_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_save_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_load_button").assertIsDisplayed()

        // Verify circular virtual_turbo button no longer exists
        assertTrue(
            "Circular virtual_turbo button must be removed",
            composeTestRule.onAllNodesWithTag("virtual_turbo").fetchSemanticsNodes().isEmpty()
        )

        // Toggle rectangular TURBO button during normal gameplay (turns ON -> 2.0x, then OFF -> 1.0x)
        composeTestRule.onNodeWithTag("speed_toggle_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("speed_toggle_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Open Quick Menu -> Save States Manager and verify confirmation rules
        composeTestRule.onNodeWithTag("emulator_menu_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("menu_save_states_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("menu_save_states_btn").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("save_states_sheet").fetchSemanticsNodes().isNotEmpty()
        }

        // Quick Save (Slot 0) saves and overwrites WITHOUT confirmation dialog
        composeTestRule.onNodeWithTag("save_slot_btn_0").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("save_states_sheet").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("emulator_menu_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("menu_save_states_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("menu_save_states_btn").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("load_slot_btn_0").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("save_slot_btn_0").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("save_states_sheet").fetchSemanticsNodes().isEmpty()
        }

        // Save to empty manual Slot 1 (no confirmation on empty slot)
        composeTestRule.onNodeWithTag("emulator_menu_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("menu_save_states_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("menu_save_states_btn").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("save_slot_btn_1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("save_slot_btn_1").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("save_states_sheet").fetchSemanticsNodes().isEmpty()
        }

        // Re-open Save States: Slot 1 now has a save -> pressing "Salvar" on Slot 1 MUST ask for confirmation
        composeTestRule.onNodeWithTag("emulator_menu_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("menu_save_states_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("menu_save_states_btn").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("load_slot_btn_1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("save_slot_btn_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("save_state_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sobrescrever Slot 1?", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_save_state_action_btn").performClick()
        composeTestRule.waitForIdle()

        // Pressing trash icon on Slot 1 MUST ask for deletion confirmation
        composeTestRule.onNodeWithTag("delete_slot_btn_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("save_state_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Excluir Slot 1?", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_save_state_action_btn").performClick()
        composeTestRule.waitForIdle()

        // Confirm overwrite on Slot 1 so it becomes the latest save
        composeTestRule.onNodeWithTag("save_slot_btn_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_save_state_action_btn").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("save_states_sheet").fetchSemanticsNodes().isEmpty()
        }

        // 4. Exit to Library and verify "CONTINUAR" button is now displayed on the game card
        composeTestRule.onNodeWithTag("emulator_menu_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("menu_exit_library_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithTag("menu_exit_library_btn")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("continue_rom_btn_$romId", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("continue_rom_btn_$romId", useUnmergedTree = true).assertExists()

        // 5. Tap "CONTINUAR" to launch the game directly at the latest save state (Slot 1)
        composeTestRule
            .onNodeWithTag("continue_rom_btn_$romId", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("emulator_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Continuando", substring = true).assertIsDisplayed()

        // 6. Open Quick Menu and enter "Editar Controles na Tela"
        composeTestRule.onNodeWithTag("emulator_menu_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule
                .onAllNodesWithTag("quick_menu_sheet")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("menu_edit_controls_btn").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule
                .onAllNodesWithTag("touch_layout_editor_panel")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("touch_layout_editor_panel").assertIsDisplayed()

        // Tapping top-right buttons while editing controls must NOT trigger Turbo, Pause, Screenshot, Quick Save, or Audio Mute
        composeTestRule.onNodeWithTag("speed_toggle_button").performClick()
        composeTestRule.onNodeWithTag("pause_toggle_button").performClick()
        composeTestRule.onNodeWithTag("screenshot_button").performClick()
        composeTestRule.onNodeWithTag("quick_save_button").performClick()
        composeTestRule.onNodeWithTag("audio_toggle_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            "Pause must NOT be triggered while editing virtual controls",
            !composeTestRule.activity.viewModel.sessionState.value.isPaused
        )
        assertTrue(
            "Quick Save must NOT be triggered while editing virtual controls",
            composeTestRule.onAllNodesWithText("Estado salvo no Slot 0!").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "Audio Mute must NOT be triggered while editing virtual controls",
            composeTestRule.onAllNodesWithText("Áudio Silenciado").fetchSemanticsNodes().isEmpty()
        )

        // Selecting chips and opacity slider in the editor works
        composeTestRule.onNodeWithTag("editor_chip_turbo").performClick()
        composeTestRule.onNodeWithTag("editor_opacity_slider").assertIsDisplayed()

        // Exit controls editor by clicking Salvar
        composeTestRule.onNodeWithTag("save_controls_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            !composeTestRule.activity.viewModel.sessionState.value.isEditingControls
        }
        composeTestRule.waitForIdle()

        // 7. Verify Pause & Screenshot buttons in the top-right cluster during normal gameplay
        composeTestRule.onNodeWithTag("pause_toggle_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screenshot_button").assertIsDisplayed()

        // Manual Pause via top-right pause button
        composeTestRule.onNodeWithTag("pause_toggle_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            "Emulator must be paused after tapping pause_toggle_button",
            composeTestRule.activity.viewModel.sessionState.value.isPaused
        )

        // Resume via top-right pause button
        composeTestRule.onNodeWithTag("pause_toggle_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            "Emulator must resume after tapping pause_toggle_button again",
            !composeTestRule.activity.viewModel.sessionState.value.isPaused
        )

        // 7b. Verify Quick Load confirmation dialog when tapping in-game quick_load_button
        composeTestRule.onNodeWithTag("quick_save_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.activity.viewModel.sessionState.value.toastMessage == "Estado salvo no Slot 0!"
        }
        composeTestRule.onNodeWithTag("quick_load_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("quick_load_confirm_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("quick_load_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retroceder ao Último Save?", substring = true).assertIsDisplayed()

        // Cancel Quick Load first to ensure accidental taps do not lose progress
        composeTestRule.onNodeWithTag("cancel_quick_load_btn").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("quick_load_confirm_dialog").fetchSemanticsNodes().isEmpty()
        }

        // Now trigger Quick Load again and confirm
        composeTestRule.onNodeWithTag("quick_load_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("confirm_quick_load_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("confirm_quick_load_btn").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.activity.viewModel.sessionState.value.toastMessage == "Slot 0 carregado com sucesso!"
        }

        // 7c. Game-only Screenshot via top-right screenshot button with active Video Filter & GB Palette
        val vm = composeTestRule.activity.viewModel
        vm.setDmgPalette(com.example.emulator.DmgPalettePreset.CYBER_NEON)
        vm.setVideoFilter(com.example.emulator.VideoFilterMode.CRT_SCANLINES)
        composeTestRule.waitForIdle()

        val screenshotsDir = java.io.File(composeTestRule.activity.filesDir, "screenshots")
        screenshotsDir.listFiles()?.forEach { it.delete() }

        composeTestRule.onNodeWithTag("screenshot_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            screenshotsDir.listFiles()?.any { it.extension == "png" && it.length() > 0L } == true
        }
        val savedScreenshots = screenshotsDir.listFiles()?.filter { it.extension == "png" && it.length() > 0L }.orEmpty()
        assertTrue("Game-only PNG screenshot must be generated and saved", savedScreenshots.isNotEmpty())

        // Verify that renderFilteredScreenshotBitmap bakes both the active DmgPalettePreset and VideoFilterMode
        val testPixels = IntArray(160 * 144) { 0xFFFFFFFF.toInt() }
        val filteredBmp = vm.repository.renderFilteredScreenshotBitmap(
            frameBuffer = testPixels,
            videoFilter = com.example.emulator.VideoFilterMode.CRT_SCANLINES,
            dmgPalette = com.example.emulator.DmgPalettePreset.CYBER_NEON,
            applyDmgPaletteFilter = true
        )
        assertTrue("Upscaled screenshot width must be 640", filteredBmp.width == 640 && filteredBmp.height == 576)
        // Non-scanline row (y = 2) has pure CYBER_NEON shade 0 (0xFF00F5D4), while scanline row (y = 0) is darkened by CRT scanline
        assertTrue(
            "Screenshot must include active GB Palette and CRT Scanlines filter",
            filteredBmp.getPixel(10, 2) == com.example.emulator.DmgPalettePreset.CYBER_NEON.colors[0] &&
                filteredBmp.getPixel(10, 0) != filteredBmp.getPixel(10, 2)
        )

        // 8. Verify Auto-Pause on Minimize and Auto-Resume on Return
        assertTrue("Game is running and unpaused before minimize", vm.sessionState.value.isRunning && !vm.sessionState.value.isPaused)
        vm.onAppMinimized()
        assertTrue(
            "Game must automatically pause when emulator is minimized",
            vm.sessionState.value.isPaused && vm.sessionState.value.isAutoPausedByLifecycle
        )
        vm.onAppResumed()
        assertTrue(
            "Game must automatically resume when user returns to the emulator",
            !vm.sessionState.value.isPaused && !vm.sessionState.value.isAutoPausedByLifecycle
        )

        // 9. Verify Save State & Battery Save (.sav) resilience across app updates / reconciliation
        val repo = vm.repository
        kotlinx.coroutines.runBlocking {
            repo.reconcilePersistedData()
            val recoveredSave = repo.getLatestSaveState(romId)
            assertTrue("Latest save state must remain intact after update reconciliation", recoveredSave != null)
            val stateBytes = repo.readSaveStateBytes(recoveredSave!!)
            assertTrue("Save state bytes must be readable after reconciliation", stateBytes != null && stateBytes.isNotEmpty())
        }

        // 10. Verify Bluetooth Controller Quick Load Confirmation ("B" cancels, "A" confirms) & Motion Axes
        // Remap B to custom keycode KEYCODE_BUTTON_C and A to custom keycode KEYCODE_BUTTON_Z to prove custom mappings work
        vm.startRemappingButton(com.example.viewmodel.GbcButton.B)
        assertTrue(vm.handlePhysicalKeyEvent(android.view.KeyEvent.KEYCODE_BUTTON_C, true))
        vm.startRemappingButton(com.example.viewmodel.GbcButton.A)
        assertTrue(vm.handlePhysicalKeyEvent(android.view.KeyEvent.KEYCODE_BUTTON_Z, true))

        // Trigger Quick Load via Bluetooth controller (L2)
        assertTrue(vm.handlePhysicalKeyEvent(android.view.KeyEvent.KEYCODE_BUTTON_L2, true))
        assertTrue(vm.handlePhysicalKeyEvent(android.view.KeyEvent.KEYCODE_BUTTON_L2, false))
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.waitForIdle()
            vm.sessionState.value.activeModal == com.example.viewmodel.EmulatorModal.CONFIRM_QUICK_LOAD
        }

        // Press mapped "B" button (KEYCODE_BUTTON_C) -> must Cancel and close modal
        assertTrue(vm.handlePhysicalKeyEvent(android.view.KeyEvent.KEYCODE_BUTTON_C, true))
        assertTrue(vm.handlePhysicalKeyEvent(android.view.KeyEvent.KEYCODE_BUTTON_C, false))
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.waitForIdle()
            vm.sessionState.value.activeModal == com.example.viewmodel.EmulatorModal.NONE
        }

        // Trigger Quick Load via Bluetooth controller (L2) again
        assertTrue(vm.handlePhysicalKeyEvent(android.view.KeyEvent.KEYCODE_BUTTON_L2, true))
        assertTrue(vm.handlePhysicalKeyEvent(android.view.KeyEvent.KEYCODE_BUTTON_L2, false))
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.waitForIdle()
            vm.sessionState.value.activeModal == com.example.viewmodel.EmulatorModal.CONFIRM_QUICK_LOAD
        }

        // Press mapped "A" button (KEYCODE_BUTTON_Z) -> must Confirm and load Save State!
        vm.clearToastMessage()
        assertTrue(vm.handlePhysicalKeyEvent(android.view.KeyEvent.KEYCODE_BUTTON_Z, true))
        assertTrue(vm.handlePhysicalKeyEvent(android.view.KeyEvent.KEYCODE_BUTTON_Z, false))
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.waitForIdle()
            vm.sessionState.value.activeModal == com.example.viewmodel.EmulatorModal.NONE
        }

        // Verify Bluetooth Hat / Analog Stick motion axes
        assertTrue(
            "Bluetooth D-Pad Hat and Analog Stick motion must be handled immediately",
            vm.handleGamepadMotionAxes(hatX = 1.0f, hatY = -1.0f, stickX = 0f, stickY = 0f)
        )
        vm.handleGamepadMotionAxes(hatX = 0f, hatY = 0f, stickX = 0f, stickY = 0f)
        vm.resetKeyMappings()
    }
}
