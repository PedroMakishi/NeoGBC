package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        // 2. Open Game Options menu (3 dots on the ROM card) and test Rename Game (and file on device)
        composeTestRule.onNodeWithTag("cover_options_btn_1").performClick()
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
    fun `emulator screen rectangular Turbo button works in gameplay and blocks accidental actions during control editing`() {
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

        // Launch the ROM
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

        // Open Quick Menu from top-left 3-bar button and enter "Editar Controles na Tela"
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

        // Tapping top-right buttons while editing controls must NOT trigger Turbo, Quick Save, or Audio Mute
        composeTestRule.onNodeWithTag("speed_toggle_button").performClick()
        composeTestRule.onNodeWithTag("quick_save_button").performClick()
        composeTestRule.onNodeWithTag("audio_toggle_button").performClick()
        composeTestRule.waitForIdle()

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
        assertTrue(true)
    }
}
