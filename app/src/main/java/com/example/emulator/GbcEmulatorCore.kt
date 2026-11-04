package com.example.emulator

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class VideoFilterMode(val displayName: String, val subtitle: String) {
    PIXEL_PERFECT("Pixel Nitidez", "Pixels nítidos 1:1 sem borrão"),
    LCD_GRID("Grade LCD GBC", "Matriz de subpixels autêntica do Game Boy Color"),
    CRT_SCANLINES("Scanlines CRT", "Linhas horizontais de monitor retrô"),
    SOFT_BILINEAR("Suave Moderno", "Bordas suavizadas com brilho vibrante")
}

class GbcEmulatorCore(
    romBytes: ByteArray
) {
    val cartridge = Cartridge(romBytes)
    val ppu = GbcPpu(isCgbMode = cartridge.header.isCgbSupported)
    val apu = GbcApu()
    val mmu = GbcMmu(cartridge, ppu, apu)
    val cpu = LR35902Cpu(mmu)

    var framesExecuted: Long = 0L
        private set

    fun reset() {
        cartridge.reset()
        ppu.reset()
        apu.reset()
        mmu.reset()
        cpu.reset()
        framesExecuted = 0L
    }

    /**
     * Executes a single full video frame (~70224 normal cycles or ~140448 double-speed cycles).
     */
    fun runSingleFrame(): IntArray {
        ppu.frameCompleted = false
        apu.clearFrameSamples()
        mmu.applyGameSharkCheats()

        val maxCycles = if (mmu.isDoubleSpeed) 140448 else 70224
        var elapsed = 0
        while (elapsed < maxCycles && !ppu.frameCompleted) {
            elapsed += cpu.step()
        }
        framesExecuted++
        return ppu.frontBuffer
    }

    fun updateInputs(
        right: Boolean = false,
        left: Boolean = false,
        up: Boolean = false,
        down: Boolean = false,
        a: Boolean = false,
        b: Boolean = false,
        select: Boolean = false,
        start: Boolean = false
    ) {
        mmu.updateJoypad(
            right = right,
            left = left,
            up = up,
            down = down,
            a = a,
            b = b,
            select = select,
            start = start
        )
    }

    fun applyCheatCodes(codes: List<String>) {
        val gsPatches = mutableListOf<GameSharkPatch>()
        val ggPatches = mutableMapOf<Int, Pair<Int, Int>>()

        for (rawCode in codes) {
            val cleaned = rawCode.uppercase().replace(" ", "").trim()
            if (cleaned.length == 8 && !cleaned.contains("-")) {
                // GameShark format: 01VVLLHH or 9XVVMMPP
                val type = cleaned.substring(0, 2).toIntOrNull(16) ?: continue
                val value = cleaned.substring(2, 4).toIntOrNull(16) ?: continue
                val lo = cleaned.substring(4, 6).toIntOrNull(16) ?: continue
                val hi = cleaned.substring(6, 8).toIntOrNull(16) ?: continue
                val addr = (hi shl 8) or lo
                val bank = if (type in 0x90..0x97) (type and 0x07) else 1
                gsPatches.add(GameSharkPatch(bank = bank, address = addr, value = value))
            } else {
                // Game Genie format: XXX-YYY-ZZZ or XXX-YYY
                val hexOnly = cleaned.replace("-", "")
                if (hexOnly.length == 9 || hexOnly.length == 6) {
                    val newVal = hexOnly.substring(0, 2).toIntOrNull(16) ?: continue
                    val d2 = hexOnly.substring(2, 3).toIntOrNull(16) ?: continue
                    val d3 = hexOnly.substring(3, 4).toIntOrNull(16) ?: continue
                    val d4 = hexOnly.substring(4, 5).toIntOrNull(16) ?: continue
                    val d5 = hexOnly.substring(5, 6).toIntOrNull(16) ?: continue
                    val highNibble = (d5 xor 0x0F) and 0x07
                    val addr = (highNibble shl 12) or (d2 shl 8) or (d3 shl 4) or d4
                    val compare = if (hexOnly.length == 9) {
                        val c0 = hexOnly.substring(6, 7).toIntOrNull(16) ?: continue
                        val c2 = hexOnly.substring(8, 9).toIntOrNull(16) ?: continue
                        val rawByte = (c0 shl 4) or c2
                        val rotated = ((rawByte shr 2) or ((rawByte and 0x03) shl 6)) and 0xFF
                        rotated xor 0xBA
                    } else {
                        -1
                    }
                    ggPatches[addr] = newVal to compare
                }
            }
        }

        mmu.setGameSharkPatches(gsPatches)
        cartridge.setGameGeniePatches(ggPatches)
    }

    fun serializeState(): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            DataOutputStream(gzip).use { out ->
                out.writeInt(0x4E474243) // Magic "NGBC"
                out.writeInt(1)          // Version 1
                out.writeLong(framesExecuted)
                cartridge.saveState(out)
                ppu.saveState(out)
                apu.saveState(out)
                mmu.saveState(out)
                cpu.saveState(out)
                apu.saveExtendedState(out)
            }
        }
        return bos.toByteArray()
    }

    fun deserializeState(stateBytes: ByteArray): Boolean {
        return try {
            GZIPInputStream(ByteArrayInputStream(stateBytes)).use { gzip ->
                DataInputStream(gzip).use { input ->
                    val magic = input.readInt()
                    val version = input.readInt()
                    if (magic != 0x4E474243 || version != 1) return false
                    framesExecuted = input.readLong()
                    cartridge.loadState(input)
                    ppu.loadState(input)
                    apu.loadState(input)
                    mmu.loadState(input)
                    cpu.loadState(input)
                    apu.loadExtendedStateIfPresent(input)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
