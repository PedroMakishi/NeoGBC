package com.example.emulator

import java.io.DataInputStream
import java.io.DataOutputStream

data class GameSharkPatch(
    val bank: Int,
    val address: Int,
    val value: Int
)

class GbcMmu(
    val cartridge: Cartridge,
    val ppu: GbcPpu,
    val apu: GbcApu
) {
    val isCgb: Boolean = cartridge.header.isCgbSupported

    // 8 banks of 4KB WRAM for CGB (32KB total)
    val wram = ByteArray(0x8000)
    var wramBank: Int = 1

    // High RAM (0xFF80..0xFFFE)
    val hram = ByteArray(0x7F)

    // Interrupt registers
    var interruptFlag: Int = 0xE1 // 0xFF0F (IF)
    var interruptEnable: Int = 0x00 // 0xFFFF (IE)

    // Joypad state (bit=1 means pressed internally, mapped inverted to 0xFF00)
    var joypadSelect: Int = 0x30
    var joypadDirections: Int = 0x00 // bit0=Right, bit1=Left, bit2=Up, bit3=Down
    var joypadButtons: Int = 0x00    // bit0=A, bit1=B, bit2=Select, bit3=Start

    // Timer registers
    private var divCounter: Int = 0xABCC
    var tima: Int = 0
    var tma: Int = 0
    var tac: Int = 0xF8
    private var timerAcc: Int = 0

    // CGB Double Speed & HDMA
    var key1: Int = 0x7E
    var isDoubleSpeed: Boolean = false
    private var hdma1: Int = 0xFF
    private var hdma2: Int = 0xFF
    private var hdma3: Int = 0xFF
    private var hdma4: Int = 0xFF
    private var hdma5: Int = 0xFF
    private var hdmaActive: Boolean = false

    // Serial registers
    private var sb: Int = 0x00
    private var sc: Int = 0x7E

    // Active GameShark patches
    private val gameSharkPatches = mutableListOf<GameSharkPatch>()

    fun setGameSharkPatches(patches: List<GameSharkPatch>) {
        gameSharkPatches.clear()
        gameSharkPatches.addAll(patches)
    }

    fun applyGameSharkCheats() {
        for (patch in gameSharkPatches) {
            val addr = patch.address and 0xFFFF
            val v = patch.value and 0xFF
            when (addr) {
                in 0xA000..0xBFFF -> cartridge.writeRam(addr, v)
                in 0xC000..0xCFFF -> wram[addr - 0xC000] = v.toByte()
                in 0xD000..0xDFFF -> {
                    val b = patch.bank.coerceIn(1, 7)
                    wram[(b * 0x1000) + (addr - 0xD000)] = v.toByte()
                }
                in 0xFF80..0xFFFE -> hram[addr - 0xFF80] = v.toByte()
            }
        }
    }

    fun reset() {
        wram.fill(0)
        hram.fill(0)
        wramBank = 1
        interruptFlag = 0xE1
        interruptEnable = 0x00
        joypadSelect = 0x30
        divCounter = 0xABCC
        tima = 0
        tma = 0
        tac = 0xF8
        timerAcc = 0
        key1 = 0x7E
        isDoubleSpeed = false
        hdmaActive = false
        hdma5 = 0xFF
    }

    fun updateJoypad(
        right: Boolean,
        left: Boolean,
        up: Boolean,
        down: Boolean,
        a: Boolean,
        b: Boolean,
        select: Boolean,
        start: Boolean
    ) {
        val prevDir = joypadDirections
        val prevBtn = joypadButtons

        var dir = 0
        if (right) dir = dir or 0x01
        if (left) dir = dir or 0x02
        if (up) dir = dir or 0x04
        if (down) dir = dir or 0x08
        joypadDirections = dir

        var btn = 0
        if (a) btn = btn or 0x01
        if (b) btn = btn or 0x02
        if (select) btn = btn or 0x04
        if (start) btn = btn or 0x08
        joypadButtons = btn

        if ((dir and prevDir.inv()) != 0 || (btn and prevBtn.inv()) != 0) {
            interruptFlag = interruptFlag or 0x10
        }
    }

    fun stepTimersAndDma(tCycles: Int) {
        val effectiveCycles = if (isDoubleSpeed) tCycles shr 1 else tCycles
        divCounter = (divCounter + tCycles) and 0xFFFF

        if ((tac and 0x04) != 0) {
            timerAcc += tCycles
            val threshold = when (tac and 0x03) {
                0 -> 1024
                1 -> 16
                2 -> 64
                else -> 256
            }
            while (timerAcc >= threshold) {
                timerAcc -= threshold
                tima++
                if (tima > 0xFF) {
                    tima = tma
                    interruptFlag = interruptFlag or 0x04
                }
            }
        }

        ppu.step(effectiveCycles)
        if (ppu.requestVBlankInterrupt) {
            interruptFlag = interruptFlag or 0x01
            ppu.requestVBlankInterrupt = false
        }
        if (ppu.requestStatInterrupt) {
            interruptFlag = interruptFlag or 0x02
            ppu.requestStatInterrupt = false
        }

        if (isCgb && hdmaActive && ppu.enteredHBlankThisStep) {
            performHdmaBlock(1)
        }

        apu.step(effectiveCycles)
    }

    fun performSpeedSwitch() {
        if (isCgb && (key1 and 0x01) != 0) {
            isDoubleSpeed = !isDoubleSpeed
            key1 = if (isDoubleSpeed) 0xFE else 0x7E
        }
    }

    fun readByte(address: Int): Int {
        val addr = address and 0xFFFF
        return when (addr) {
            in 0x0000..0x7FFF -> cartridge.readRom(addr)
            in 0x8000..0x9FFF -> ppu.readVram(addr)
            in 0xA000..0xBFFF -> cartridge.readRam(addr)
            in 0xC000..0xCFFF -> wram[addr - 0xC000].toInt() and 0xFF
            in 0xD000..0xDFFF -> {
                val b = if (isCgb) wramBank else 1
                wram[(b * 0x1000) + (addr - 0xD000)].toInt() and 0xFF
            }
            in 0xE000..0xFDFF -> readByte(addr - 0x2000)
            in 0xFE00..0xFE9F -> ppu.readOam(addr)
            in 0xFEA0..0xFEFF -> 0xFF
            in 0xFF00..0xFF7F -> readIo(addr)
            in 0xFF80..0xFFFE -> hram[addr - 0xFF80].toInt() and 0xFF
            0xFFFF -> interruptEnable and 0xFF
            else -> 0xFF
        }
    }

    fun writeByte(address: Int, value: Int) {
        val addr = address and 0xFFFF
        val v = value and 0xFF
        when (addr) {
            in 0x0000..0x7FFF -> cartridge.writeRom(addr, v)
            in 0x8000..0x9FFF -> ppu.writeVram(addr, v)
            in 0xA000..0xBFFF -> cartridge.writeRam(addr, v)
            in 0xC000..0xCFFF -> wram[addr - 0xC000] = v.toByte()
            in 0xD000..0xDFFF -> {
                val b = if (isCgb) wramBank else 1
                wram[(b * 0x1000) + (addr - 0xD000)] = v.toByte()
            }
            in 0xE000..0xFDFF -> writeByte(addr - 0x2000, v)
            in 0xFE00..0xFE9F -> ppu.writeOam(addr, v)
            in 0xFEA0..0xFEFF -> Unit
            in 0xFF00..0xFF7F -> writeIo(addr, v)
            in 0xFF80..0xFFFE -> hram[addr - 0xFF80] = v.toByte()
            0xFFFF -> interruptEnable = v
        }
    }

    private fun readIo(addr: Int): Int {
        return when (addr) {
            0xFF00 -> {
                var result = 0xCF or (joypadSelect and 0x30)
                if ((joypadSelect and 0x10) == 0) {
                    result = result and (joypadDirections.inv() or 0xF0)
                }
                if ((joypadSelect and 0x20) == 0) {
                    result = result and (joypadButtons.inv() or 0xF0)
                }
                result
            }
            0xFF01 -> sb
            0xFF02 -> sc
            0xFF04 -> (divCounter shr 8) and 0xFF
            0xFF05 -> tima and 0xFF
            0xFF06 -> tma and 0xFF
            0xFF07 -> tac or 0xF8
            0xFF0F -> interruptFlag or 0xE0
            in 0xFF10..0xFF3F -> apu.readRegister(addr)
            0xFF40 -> ppu.lcdc
            0xFF41 -> ppu.stat or 0x80
            0xFF42 -> ppu.scy
            0xFF43 -> ppu.scx
            0xFF44 -> ppu.ly
            0xFF45 -> ppu.lyc
            0xFF47 -> ppu.bgp
            0xFF48 -> ppu.obp0
            0xFF49 -> ppu.obp1
            0xFF4A -> ppu.wy
            0xFF4B -> ppu.wx
            0xFF4D -> if (isCgb) (if (isDoubleSpeed) 0x80 else 0x00) or (key1 and 0x01) or 0x7E else 0xFF
            0xFF4F -> if (isCgb) ppu.vramBank or 0xFE else 0xFF
            0xFF55 -> if (isCgb) {
                if (hdmaActive) (hdma5 and 0x7F) else (hdma5 or 0x80)
            } else 0xFF
            0xFF68 -> if (isCgb) ppu.bcps or 0x40 else 0xFF
            0xFF69 -> if (isCgb) ppu.readBcpd() else 0xFF
            0xFF6A -> if (isCgb) ppu.ocps or 0x40 else 0xFF
            0xFF6B -> if (isCgb) ppu.readOcpd() else 0xFF
            0xFF6C -> if (isCgb) ppu.opri or 0xFE else 0xFF
            0xFF70 -> if (isCgb) wramBank or 0xF8 else 0xFF
            else -> 0xFF
        }
    }

    private fun writeIo(addr: Int, v: Int) {
        when (addr) {
            0xFF00 -> joypadSelect = v and 0x30
            0xFF01 -> sb = v
            0xFF02 -> {
                sc = v
                if ((v and 0x81) == 0x81) {
                    sb = 0xFF
                    sc = v and 0x7F
                    interruptFlag = interruptFlag or 0x08
                }
            }
            0xFF04 -> divCounter = 0
            0xFF05 -> tima = v
            0xFF06 -> tma = v
            0xFF07 -> tac = v and 0x07
            0xFF0F -> interruptFlag = v or 0xE0
            in 0xFF10..0xFF3F -> apu.writeRegister(addr, v)
            0xFF40 -> ppu.writeLcdc(v)
            0xFF41 -> ppu.stat = (ppu.stat and 0x07) or (v and 0x78) or 0x80
            0xFF42 -> ppu.scy = v
            0xFF43 -> ppu.scx = v
            0xFF45 -> {
                ppu.lyc = v
                ppu.updateLycComparison()
            }
            0xFF46 -> {
                // OAM DMA Transfer
                val srcBase = v shl 8
                for (i in 0 until 0xA0) {
                    ppu.writeOam(0xFE00 + i, readByte(srcBase + i))
                }
            }
            0xFF47 -> ppu.bgp = v
            0xFF48 -> ppu.obp0 = v
            0xFF49 -> ppu.obp1 = v
            0xFF4A -> ppu.wy = v
            0xFF4B -> ppu.wx = v
            0xFF4D -> if (isCgb) key1 = (key1 and 0x80) or (v and 0x01) or 0x7E
            0xFF4F -> if (isCgb) ppu.vramBank = v and 0x01
            0xFF51 -> if (isCgb) hdma1 = v
            0xFF52 -> if (isCgb) hdma2 = v and 0xF0
            0xFF53 -> if (isCgb) hdma3 = v and 0x1F
            0xFF54 -> if (isCgb) hdma4 = v and 0xF0
            0xFF55 -> if (isCgb) startOrControlHdma(v)
            0xFF68 -> if (isCgb) ppu.bcps = v
            0xFF69 -> if (isCgb) ppu.writeBcpd(v)
            0xFF6A -> if (isCgb) ppu.ocps = v
            0xFF6B -> if (isCgb) ppu.writeOcpd(v)
            0xFF6C -> if (isCgb) ppu.opri = v and 0x01
            0xFF70 -> if (isCgb) {
                val bank = v and 0x07
                wramBank = if (bank == 0) 1 else bank
            }
        }
    }

    private fun startOrControlHdma(v: Int) {
        if (hdmaActive && (v and 0x80) == 0) {
            hdmaActive = false
            hdma5 = (hdma5 or 0x80) and 0xFF
            return
        }
        val blocks = (v and 0x7F) + 1
        if ((v and 0x80) == 0) {
            // General Purpose DMA: copy immediately
            hdma5 = v and 0x7F
            performHdmaBlock(blocks)
            hdmaActive = false
            hdma5 = 0xFF
        } else {
            // H-Blank DMA
            hdma5 = v and 0x7F
            hdmaActive = true
        }
    }

    private fun performHdmaBlock(blockCount: Int) {
        var src = ((hdma1 shl 8) or hdma2) and 0xFFF0
        var dst = (0x8000 or ((hdma3 and 0x1F) shl 8) or (hdma4 and 0xF0))
        val totalBytes = blockCount * 16
        for (i in 0 until totalBytes) {
            ppu.writeVram(0x8000 + ((dst + i) and 0x1FFF), readByte((src + i) and 0xFFFF))
        }
        src = (src + totalBytes) and 0xFFFF
        dst = (dst + totalBytes) and 0x1FFF
        hdma1 = (src shr 8) and 0xFF
        hdma2 = src and 0xF0
        hdma3 = (dst shr 8) and 0x1F
        hdma4 = dst and 0xF0

        val remaining = (hdma5 and 0x7F) - blockCount
        if (remaining < 0) {
            hdma5 = 0xFF
            hdmaActive = false
        } else {
            hdma5 = remaining and 0x7F
        }
    }

    fun saveState(out: DataOutputStream) {
        out.writeInt(wramBank)
        out.writeInt(interruptFlag)
        out.writeInt(interruptEnable)
        out.writeInt(joypadSelect)
        out.writeInt(divCounter)
        out.writeInt(tima)
        out.writeInt(tma)
        out.writeInt(tac)
        out.writeInt(timerAcc)
        out.writeInt(key1)
        out.writeBoolean(isDoubleSpeed)
        out.writeInt(hdma1)
        out.writeInt(hdma2)
        out.writeInt(hdma3)
        out.writeInt(hdma4)
        out.writeInt(hdma5)
        out.writeBoolean(hdmaActive)
        out.write(wram)
        out.write(hram)
    }

    fun loadState(input: DataInputStream) {
        wramBank = input.readInt()
        interruptFlag = input.readInt()
        interruptEnable = input.readInt()
        joypadSelect = input.readInt()
        divCounter = input.readInt()
        tima = input.readInt()
        tma = input.readInt()
        tac = input.readInt()
        timerAcc = input.readInt()
        key1 = input.readInt()
        isDoubleSpeed = input.readBoolean()
        hdma1 = input.readInt()
        hdma2 = input.readInt()
        hdma3 = input.readInt()
        hdma4 = input.readInt()
        hdma5 = input.readInt()
        hdmaActive = input.readBoolean()
        input.readFully(wram)
        input.readFully(hram)
    }
}
