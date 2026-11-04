package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.data.NeoGbcRepository
import com.example.emulator.Cartridge
import com.example.emulator.GbcEmulatorCore
import com.example.ui.components.clampControlCenterOutsideGame
import com.example.ui.components.resolveNonOverlappingControlCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExampleUnitTest {

    companion object {
        /**
         * Builds a self-contained, public-domain synthetic Game Boy Color ROM wrapped in a ZIP archive
         * in memory for unit and UI testing (zero copyrighted assets in the repository).
         */
        fun createSyntheticGbcRomZipBytes(): ByteArray {
            // 64 KB (4 banks) MBC5+RAM+BATTERY Game Boy Color test ROM
            val rom = ByteArray(0x10000)

            // Entry point at 0x0100: NOP; JP 0x0150
            rom[0x0100] = 0x00.toByte()
            rom[0x0101] = 0xC3.toByte()
            rom[0x0102] = 0x50.toByte()
            rom[0x0103] = 0x01.toByte()

            // Title at 0x0134..0x0142: "NEOGBC DEMO"
            val titleBytes = "NEOGBC DEMO".toByteArray(Charsets.US_ASCII)
            System.arraycopy(titleBytes, 0, rom, 0x0134, titleBytes.size)

            // CGB Flag at 0x0143: 0xC0 (CGB Only)
            rom[0x0143] = 0xC0.toByte()
            // Cartridge Type at 0x0147: 0x1B (MBC5+RAM+BATTERY)
            rom[0x0147] = 0x1B.toByte()
            // ROM Size Code at 0x0148: 0x01 (64 KB / 4 banks)
            rom[0x0148] = 0x01.toByte()
            // RAM Size Code at 0x0149: 0x02 (8 KB)
            rom[0x0149] = 0x02.toByte()

            // Compute header checksum (0x0134..0x014C)
            var checksum = 0
            for (addr in 0x0134..0x014C) {
                checksum = (checksum - (rom[addr].toInt() and 0xFF) - 1) and 0xFF
            }
            rom[0x014D] = checksum.toByte()

            // Program at 0x0150: Populate VRAM 0x8000..0x807F with pattern bytes, then loop
            var pc = 0x0150
            // LD HL, 0x8000 (21 00 80)
            rom[pc++] = 0x21.toByte()
            rom[pc++] = 0x00.toByte()
            rom[pc++] = 0x80.toByte()
            // LD B, 0x80 (06 80)
            rom[pc++] = 0x06.toByte()
            rom[pc++] = 0x80.toByte()
            // LD A, 0x3C (3E 3C)
            rom[pc++] = 0x3E.toByte()
            rom[pc++] = 0x3C.toByte()
            // loop_fill (0x0157): LD (HL+), A (22); INC A (3C); DEC B (05); JR NZ, -5 (20 FB)
            rom[pc++] = 0x22.toByte()
            rom[pc++] = 0x3C.toByte()
            rom[pc++] = 0x05.toByte()
            rom[pc++] = 0x20.toByte()
            rom[pc++] = 0xFB.toByte()
            // Infinite main loop: INC C (0C); JR -3 (18 FD)
            rom[pc++] = 0x0C.toByte()
            rom[pc++] = 0x18.toByte()
            rom[pc] = 0xFD.toByte()

            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("NeoGBC_Test_Demo.gbc"))
                zos.write(rom)
                zos.closeEntry()
            }
            return baos.toByteArray()
        }
    }

    @Test
    fun `validate synthetic GBC ROM zip extraction and header parsing`() {
        val rawZipBytes = createSyntheticGbcRomZipBytes()
        val cartridge = Cartridge(rawZipBytes)

        assertEquals("NEOGBC DEMO", cartridge.header.title)
        assertTrue(cartridge.header.isCgbSupported)
        assertTrue(cartridge.header.isCgbOnly)
        assertEquals(0x1B, cartridge.header.cartridgeType)
        assertEquals("MBC5", cartridge.header.cartridgeTypeName)
        assertEquals(65536, cartridge.header.romSizeBytes)
        assertEquals(4, cartridge.header.romBanksCount)
    }

    @Test
    fun `validate NeoGBC Core Engine boots and renders frames of synthetic GBC ROM`() {
        val core = GbcEmulatorCore(createSyntheticGbcRomZipBytes())
        var frameBuffer = IntArray(0)
        for (i in 0 until 90) {
            if (i == 45) {
                core.updateInputs(start = true, a = true)
            } else {
                core.updateInputs()
            }
            frameBuffer = core.runSingleFrame()
        }

        assertEquals(90L, core.framesExecuted)
        assertEquals(160 * 144, frameBuffer.size)
        assertTrue("CPU must have executed millions of cycles", core.cpu.totalCycles > 3_000_000L)

        val nonZeroVramBytes = core.ppu.vram.count { it != 0.toByte() }
        assertTrue("ROM must populate VRAM during boot ($nonZeroVramBytes non-zero bytes)", nonZeroVramBytes > 64)
    }

    @Test
    fun `validate Save State serialization and restoration`() {
        val core = GbcEmulatorCore(createSyntheticGbcRomZipBytes())
        repeat(20) { core.runSingleFrame() }

        val savedPc = core.cpu.pc
        val savedSp = core.cpu.sp
        val savedA = core.cpu.a
        val savedFrames = core.framesExecuted
        val snapshot = core.serializeState()
        assertTrue("Snapshot must be non-empty", snapshot.size > 100)

        repeat(30) { core.runSingleFrame() }
        assertEquals(50L, core.framesExecuted)

        val restored = core.deserializeState(snapshot)
        assertTrue("State deserialization must succeed", restored)
        assertEquals(savedFrames, core.framesExecuted)
        assertEquals(savedPc, core.cpu.pc)
        assertEquals(savedSp, core.cpu.sp)
        assertEquals(savedA, core.cpu.a)
    }

    @Test
    fun `validate GameShark and Battery SRAM persistence`() {
        val core = GbcEmulatorCore(createSyntheticGbcRomZipBytes())
        core.applyCheatCodes(listOf("014223C1"))
        core.runSingleFrame()
        assertEquals(0x42, core.mmu.readByte(0xC123))

        core.mmu.writeByte(0x0000, 0x0A)
        core.mmu.writeByte(0xA010, 0x77)
        assertEquals(0x77, core.mmu.readByte(0xA010))
        val sramDump = core.cartridge.dumpBatterySram()
        assertNotEquals(0, sramDump.size)
    }

    @Test
    fun `validate Version 08 per-button opacity independence and default layout`() {
        val landscapeLayout = NeoGbcRepository.defaultControlLayout(isLandscape = true)
        assertEquals("LANDSCAPE_V5", landscapeLayout.profileId)
        assertTrue("D-Pad X must be in left side pillar", landscapeLayout.dpadX < 0.22f)
        assertTrue("Select/Start X must be in side pillar outside game screen", landscapeLayout.startSelectX < 0.22f)
        assertTrue("AB X must be in right side pillar", landscapeLayout.abX > 0.78f)
        assertTrue("Turbo Bar X must be in right side pillar", landscapeLayout.turboX > 0.78f)

        // Changing Select/Start opacity must NOT alter D-Pad, B/A, or Turbo Bar opacity
        val updatedSsOpacity = landscapeLayout.copy(startSelectOpacity = 0.35f)
        assertEquals(0.35f, updatedSsOpacity.startSelectOpacity, 0.001f)
        assertEquals(0.88f, updatedSsOpacity.dpadOpacity, 0.001f)
        assertEquals(0.88f, updatedSsOpacity.abOpacity, 0.001f)
        assertEquals(0.92f, updatedSsOpacity.turboOpacity, 0.001f)
    }

    @Test
    fun `validate Version 08 alphabetical sorting comparator for Todos list`() {
        val unsortedTitles = listOf("Zelda DX", "pokemon crystal", "Alleyway", "Mario Land", " donkey kong")
        val sorted = unsortedTitles
            .map { it.trim() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        assertEquals(
            listOf("Alleyway", "donkey kong", "Mario Land", "pokemon crystal", "Zelda DX"),
            sorted
        )
    }

    @Test
    fun `validate Version 08 anti-overlap collision prevents virtual controls from overlapping`() {
        val screenW = 2340f
        val screenH = 1080f
        val gameW = screenH * (10f / 9f) // 1200f
        val gameLeft = (screenW - gameW) / 2f // 570f
        val gameRight = gameLeft + gameW // 1770f
        val gameRect = Rect(gameLeft, 0f, gameRight, screenH)

        // Place D-Pad in the left pillar at (260, 560) with radius 150
        val dpadCenter = Offset(260f, 560f)
        val dpadHalf = 150f
        val dpadRect = Rect(
            dpadCenter.x - dpadHalf,
            dpadCenter.y - dpadHalf,
            dpadCenter.x + dpadHalf,
            dpadCenter.y + dpadHalf
        )

        // Attempt to drag Select/Start (halfW = 140, halfH = 45) directly on top of D-Pad center (260, 560)
        val ssHalfW = 140f
        val ssHalfH = 45f
        val ssFallback = Offset(260f, 920f)
        val resolvedSs = resolveNonOverlappingControlCenter(
            desiredCenterPx = Offset(260f, 560f),
            fallbackCenterPx = ssFallback,
            halfW = ssHalfW,
            halfH = ssHalfH,
            screenW = screenW,
            screenH = screenH,
            gameRect = gameRect,
            topHudHeightPx = 90f,
            isLandscape = true,
            otherControlRects = listOf(dpadRect)
        )
        val resolvedSsRect = Rect(
            resolvedSs.x - ssHalfW,
            resolvedSs.y - ssHalfH,
            resolvedSs.x + ssHalfW,
            resolvedSs.y + ssHalfH
        )

        assertFalse("Select/Start must NEVER overlap D-Pad", resolvedSsRect.overlaps(dpadRect))
        assertFalse("Select/Start must NEVER overlap gameRect", resolvedSsRect.overlaps(gameRect))
        assertTrue("Select/Start must stay inside screen bounds", resolvedSsRect.left >= 0f && resolvedSsRect.bottom <= screenH)

        // Attempt to drag B/A cluster (halfW = 155, halfH = 95) right onto D-Pad as well
        val abHalfW = 155f
        val abHalfH = 95f
        val abFallback = Offset(2050f, 650f)
        val resolvedAb = resolveNonOverlappingControlCenter(
            desiredCenterPx = Offset(260f, 560f),
            fallbackCenterPx = abFallback,
            halfW = abHalfW,
            halfH = abHalfH,
            screenW = screenW,
            screenH = screenH,
            gameRect = gameRect,
            topHudHeightPx = 90f,
            isLandscape = true,
            otherControlRects = listOf(dpadRect, resolvedSsRect)
        )
        val resolvedAbRect = Rect(
            resolvedAb.x - abHalfW,
            resolvedAb.y - abHalfH,
            resolvedAb.x + abHalfW,
            resolvedAb.y + abHalfH
        )

        assertFalse("B/A cluster must NEVER overlap D-Pad", resolvedAbRect.overlaps(dpadRect))
        assertFalse("B/A cluster must NEVER overlap Select/Start", resolvedAbRect.overlaps(resolvedSsRect))
        assertFalse("B/A cluster must NEVER overlap gameRect", resolvedAbRect.overlaps(gameRect))
    }

    @Test
    fun `validate virtual control clamping prevents escaping phone screen or entering game screen`() {
        val screenW = 2340f
        val screenH = 1080f
        val gameW = screenH * (10f / 9f)
        val gameLeft = (screenW - gameW) / 2f
        val gameRight = gameLeft + gameW
        val gameRect = Rect(gameLeft, 0f, gameRight, screenH)
        val halfW = 140f
        val halfH = 140f

        val clampedOutLeft = clampControlCenterOutsideGame(
            desiredCenterPx = Offset(-500f, -500f),
            halfW = halfW,
            halfH = halfH,
            screenW = screenW,
            screenH = screenH,
            gameRect = gameRect,
            topHudHeightPx = 100f,
            isLandscape = true
        )
        assertTrue("Left edge of control must stay >= 0", clampedOutLeft.x - halfW >= 0f)
        assertTrue("Top edge of control must stay >= topHud", clampedOutLeft.y - halfH >= 100f)

        val clampedIntoGameLeft = clampControlCenterOutsideGame(
            desiredCenterPx = Offset(900f, 540f),
            halfW = halfW,
            halfH = halfH,
            screenW = screenW,
            screenH = screenH,
            gameRect = gameRect,
            topHudHeightPx = 100f,
            isLandscape = true
        )
        assertTrue(
            "Right edge of left-side control (${clampedIntoGameLeft.x + halfW}) must stay <= gameRect.left ($gameLeft)",
            clampedIntoGameLeft.x + halfW <= gameRect.left
        )

        val clampedIntoGameRight = clampControlCenterOutsideGame(
            desiredCenterPx = Offset(1400f, 540f),
            halfW = halfW,
            halfH = halfH,
            screenW = screenW,
            screenH = screenH,
            gameRect = gameRect,
            topHudHeightPx = 100f,
            isLandscape = true
        )
        assertTrue(
            "Left edge of right-side control (${clampedIntoGameRight.x - halfW}) must stay >= gameRect.right ($gameRight)",
            clampedIntoGameRight.x - halfW >= gameRect.right
        )
    }

    @Test
    fun `validate Version 15 APU Volume Envelope decays Channel 4 noise SFX to silence without length counter`() {
        val core = GbcEmulatorCore(createSyntheticGbcRomZipBytes())
        val apu = core.apu

        // Trigger a GB Studio / hUGETracker style Noise SFX (like interacting with the pliers on the wall):
        // NR42 (0xFF21) = 0xF1 (Initial volume = 15, direction = 0 decrease, period = 1)
        // NR43 (0xFF22) = 0x52 (Noise clock shift & divisor)
        // NR44 (0xFF23) = 0x80 (Trigger = 1, Length counter enable = 0!)
        apu.writeRegister(0xFF21, 0xF1)
        apu.writeRegister(0xFF22, 0x52)
        apu.writeRegister(0xFF23, 0x80)

        assertTrue("Channel 4 must be enabled immediately upon trigger", apu.ch4Enabled)
        assertEquals("Channel 4 must start at volume 15", 15, apu.ch4Volume)

        // Step 1 frame (~70224 T-cycles): noise must be audible during the sound effect
        apu.clearFrameSamples()
        apu.step(70224)
        val initialNonZeroSamples = (0 until apu.samplesProduced).count { apu.sampleBuffer[it].toInt() != 0 }
        assertTrue("Noise SFX must produce audible samples initially", initialNonZeroSamples > 100)

        // Step 20 more frames (~0.33 seconds): 64Hz envelope must decay ch4Volume from 15 down to 0
        repeat(20) {
            apu.clearFrameSamples()
            apu.step(70224)
        }

        assertEquals("Channel 4 volume must decay completely to 0 via 64Hz envelope", 0, apu.ch4Volume)

        // Next frame must be 100% silent (zero stuck white noise!)
        apu.clearFrameSamples()
        apu.step(70224)
        val stuckNoiseSamples = (0 until apu.samplesProduced).count { apu.sampleBuffer[it].toInt() != 0 }
        assertEquals("Channel 4 must produce 0 residual noise after envelope decay", 0, stuckNoiseSamples)
    }

    @Test
    fun `validate Version 15 APU NR51 stereo panning mute and Save State audio restoration`() {
        val core = GbcEmulatorCore(createSyntheticGbcRomZipBytes())
        val apu = core.apu

        // Trigger Channel 4 noise with constant envelope (0xF0) routed to L+R via NR51 (0xFF25 = 0x88)
        apu.writeRegister(0xFF25, 0x88)
        apu.writeRegister(0xFF21, 0xF0)
        apu.writeRegister(0xFF22, 0x31)
        apu.writeRegister(0xFF23, 0x80)

        apu.clearFrameSamples()
        apu.step(70224)
        assertTrue("Channel 4 routed in NR51 must produce non-zero samples", (0 until apu.samplesProduced).any { apu.sampleBuffer[it].toInt() != 0 })

        // Mute Channel 4 via NR51 (0xFF25 = 0x00, disconnecting it from Left and Right outputs)
        apu.writeRegister(0xFF25, 0x00)
        apu.clearFrameSamples()
        apu.step(70224)
        assertTrue("Channel 4 disconnected in NR51 must produce complete silence", (0 until apu.samplesProduced).all { apu.sampleBuffer[it].toInt() == 0 })

        // Restore NR51 and trigger a decaying noise SFX right before a Quick Save
        apu.writeRegister(0xFF25, 0xF3)
        apu.writeRegister(0xFF21, 0xF1)
        apu.writeRegister(0xFF23, 0x80)
        assertEquals(15, apu.ch4Volume)

        val savedState = core.serializeState()

        // Advance until noise has decayed to 0
        repeat(25) { core.runSingleFrame() }
        assertEquals(0, core.apu.ch4Volume)

        // Quick Load the state that was saved mid-SFX and verify the SFX finishes decaying to 0 cleanly
        assertTrue(core.deserializeState(savedState))
        repeat(25) { core.runSingleFrame() }
        assertEquals("Restored mid-SFX state must still decay ch4Volume to 0", 0, core.apu.ch4Volume)
    }

    @Test
    fun `validate Version 15 Channel 1 frequency sweep overflow and legacy save state audio cleanup`() {
        val core = GbcEmulatorCore(createSyntheticGbcRomZipBytes())
        val apu = core.apu

        // Configure Channel 1 with upward frequency sweep that overflows 2047:
        // NR10 (0xFF10) = 0x11 (period = 1, direction = 0 addition, shift = 1)
        // NR12 (0xFF12) = 0xF0 (volume = 15, period = 0)
        // NR13 (0xFF13) = 0xFF, NR14 (0xFF14) = 0x85 -> freq = 0x5FF (1535); 1535 + (1535 >> 1) = 2302 > 2047!
        apu.writeRegister(0xFF10, 0x11)
        apu.writeRegister(0xFF12, 0xF0)
        apu.writeRegister(0xFF13, 0xFF)
        apu.writeRegister(0xFF14, 0x85)

        assertFalse("Channel 1 must auto-disable immediately upon sweep overflow (> 2047)", apu.ch1Enabled)

        // Simulate a legacy save state (without APU2 block) where NR42 had 0xF1 written
        val legacyBos = ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(legacyBos).use { gzip ->
            java.io.DataOutputStream(gzip).use { out ->
                out.writeInt(0x4E474243)
                out.writeInt(1)
                out.writeLong(core.framesExecuted)
                core.cartridge.saveState(out)
                core.ppu.saveState(out)
                core.apu.writeRegister(0xFF21, 0xF1)
                core.apu.writeRegister(0xFF23, 0x80)
                core.apu.saveState(out)
                core.mmu.saveState(out)
                core.cpu.saveState(out)
            }
        }
        assertTrue(core.deserializeState(legacyBos.toByteArray()))
        assertFalse("Legacy save state load must silence stuck transient Channel 4 noise", core.apu.ch4Enabled)
        assertEquals(0, core.apu.ch4Volume)
    }
}

