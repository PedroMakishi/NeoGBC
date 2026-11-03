package com.example.emulator

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

data class RomHeaderInfo(
    val title: String,
    val isCgbSupported: Boolean,
    val isCgbOnly: Boolean,
    val cartridgeType: Int,
    val cartridgeTypeName: String,
    val romSizeCode: Int,
    val romBanksCount: Int,
    val ramSizeCode: Int,
    val ramBytesSize: Int,
    val headerChecksum: Int,
    val crc32Hex: String,
    val romSizeBytes: Int
)

class Cartridge(
    rawBytes: ByteArray
) {
    val romData: ByteArray = extractRomIfZip(rawBytes)
    val header: RomHeaderInfo = parseHeader(romData)

    // External Cartridge RAM (up to 128KB for MBC5)
    val ramData: ByteArray = ByteArray(maxOf(header.ramBytesSize, 0x8000))

    // MBC State Registers
    var ramEnabled: Boolean = false
    var romBank: Int = 1
    var ramBank: Int = 0
    var bankingMode: Int = 0 // 0 = ROM mode, 1 = RAM mode (MBC1)

    // MBC3 Real-Time Clock (RTC) Registers
    val rtcRegisters = IntArray(5) // 0x08..0x0C: S, M, H, DL, DH
    var rtcMappedReg: Int = -1
    var rtcLatchState: Int = 0

    // GameGenie ROM patches: address -> (newVal, compareVal or -1)
    private val gameGeniePatches = mutableMapOf<Int, Pair<Int, Int>>()

    fun setGameGeniePatches(patches: Map<Int, Pair<Int, Int>>) {
        gameGeniePatches.clear()
        gameGeniePatches.putAll(patches)
    }

    fun reset() {
        ramEnabled = false
        romBank = 1
        ramBank = 0
        bankingMode = 0
        rtcMappedReg = -1
        rtcLatchState = 0
    }

    fun readRom(address: Int): Int {
        val addr = address and 0xFFFF
        val physicalAddress = if (addr < 0x4000) {
            if (isMbc1() && bankingMode == 1) {
                val zeroBank = (ramBank shl 5) % header.romBanksCount
                (zeroBank * 0x4000) + addr
            } else {
                addr
            }
        } else {
            val effectiveBank = when {
                isMbc5() -> romBank % header.romBanksCount
                isMbc1() -> {
                    val b = ((ramBank shl 5) or (romBank and 0x1F)).let { if ((it and 0x1F) == 0) it or 1 else it }
                    b % header.romBanksCount
                }
                else -> (if (romBank == 0) 1 else romBank) % header.romBanksCount
            }
            (effectiveBank * 0x4000) + (addr - 0x4000)
        }

        val rawVal = if (physicalAddress in romData.indices) {
            romData[physicalAddress].toInt() and 0xFF
        } else {
            0xFF
        }

        val patch = gameGeniePatches[addr]
        if (patch != null) {
            val (replacement, compare) = patch
            if (compare == -1 || compare == rawVal) {
                return replacement and 0xFF
            }
        }
        return rawVal
    }

    fun writeRom(address: Int, value: Int) {
        val addr = address and 0xFFFF
        val v = value and 0xFF

        when {
            isMbc5() -> {
                when (addr) {
                    in 0x0000..0x1FFF -> {
                        ramEnabled = (v and 0x0F) == 0x0A
                    }
                    in 0x2000..0x2FFF -> {
                        romBank = (romBank and 0x100) or v
                    }
                    in 0x3000..0x3FFF -> {
                        romBank = (romBank and 0xFF) or ((v and 0x01) shl 8)
                    }
                    in 0x4000..0x5FFF -> {
                        ramBank = v and 0x0F
                    }
                }
            }
            isMbc3() -> {
                when (addr) {
                    in 0x0000..0x1FFF -> {
                        ramEnabled = (v and 0x0F) == 0x0A
                    }
                    in 0x2000..0x3FFF -> {
                        val bank = v and 0x7F
                        romBank = if (bank == 0) 1 else bank
                    }
                    in 0x4000..0x5FFF -> {
                        if (v in 0x00..0x03) {
                            ramBank = v
                            rtcMappedReg = -1
                        } else if (v in 0x08..0x0C) {
                            rtcMappedReg = v - 0x08
                        }
                    }
                    in 0x6000..0x7FFF -> {
                        if (rtcLatchState == 0 && v == 0) {
                            rtcLatchState = 1
                        } else if (rtcLatchState == 1 && v == 1) {
                            // Latch current system time into RTC registers
                            val nowSec = (System.currentTimeMillis() / 1000L)
                            rtcRegisters[0] = (nowSec % 60).toInt()
                            rtcRegisters[1] = ((nowSec / 60) % 60).toInt()
                            rtcRegisters[2] = ((nowSec / 3600) % 24).toInt()
                            rtcLatchState = 0
                        } else {
                            rtcLatchState = 0
                        }
                    }
                }
            }
            isMbc2() -> {
                if (addr in 0x0000..0x3FFF) {
                    if ((addr and 0x0100) == 0) {
                        ramEnabled = (v and 0x0F) == 0x0A
                    } else {
                        val b = v and 0x0F
                        romBank = if (b == 0) 1 else b
                    }
                }
            }
            else -> {
                // Default MBC1 behavior
                when (addr) {
                    in 0x0000..0x1FFF -> {
                        ramEnabled = (v and 0x0F) == 0x0A
                    }
                    in 0x2000..0x3FFF -> {
                        val b = v and 0x1F
                        romBank = if (b == 0) 1 else b
                    }
                    in 0x4000..0x5FFF -> {
                        ramBank = v and 0x03
                    }
                    in 0x6000..0x7FFF -> {
                        bankingMode = v and 0x01
                    }
                }
            }
        }
    }

    fun readRam(address: Int): Int {
        if (!ramEnabled) return 0xFF
        if (isMbc3() && rtcMappedReg in 0..4) {
            return rtcRegisters[rtcMappedReg] and 0xFF
        }
        val offset = address - 0xA000
        if (isMbc2()) {
            val idx = offset and 0x01FF
            return (ramData[idx].toInt() and 0x0F) or 0xF0
        }
        val effectiveRamBank = if (isMbc1() && bankingMode == 0) 0 else ramBank
        val ramAddr = (effectiveRamBank * 0x2000) + offset
        return if (ramAddr in ramData.indices) {
            ramData[ramAddr].toInt() and 0xFF
        } else {
            0xFF
        }
    }

    fun writeRam(address: Int, value: Int) {
        if (!ramEnabled) return
        val v = value and 0xFF
        if (isMbc3() && rtcMappedReg in 0..4) {
            rtcRegisters[rtcMappedReg] = v
            return
        }
        val offset = address - 0xA000
        if (isMbc2()) {
            val idx = offset and 0x01FF
            ramData[idx] = (v and 0x0F).toByte()
            return
        }
        val effectiveRamBank = if (isMbc1() && bankingMode == 0) 0 else ramBank
        val ramAddr = (effectiveRamBank * 0x2000) + offset
        if (ramAddr in ramData.indices) {
            ramData[ramAddr] = v.toByte()
        }
    }

    fun loadBatterySram(sramBytes: ByteArray) {
        val copyLen = minOf(sramBytes.size, ramData.size)
        System.arraycopy(sramBytes, 0, ramData, 0, copyLen)
    }

    fun dumpBatterySram(): ByteArray {
        return ramData.copyOf()
    }

    fun saveState(out: DataOutputStream) {
        out.writeBoolean(ramEnabled)
        out.writeInt(romBank)
        out.writeInt(ramBank)
        out.writeInt(bankingMode)
        out.writeInt(rtcMappedReg)
        out.writeInt(rtcLatchState)
        for (i in 0..4) out.writeInt(rtcRegisters[i])
        out.writeInt(ramData.size)
        out.write(ramData)
    }

    fun loadState(input: DataInputStream) {
        ramEnabled = input.readBoolean()
        romBank = input.readInt()
        ramBank = input.readInt()
        bankingMode = input.readInt()
        rtcMappedReg = input.readInt()
        rtcLatchState = input.readInt()
        for (i in 0..4) rtcRegisters[i] = input.readInt()
        val size = input.readInt()
        val buf = ByteArray(size)
        input.readFully(buf)
        System.arraycopy(buf, 0, ramData, 0, minOf(size, ramData.size))
    }

    private fun isMbc1(): Boolean = header.cartridgeType in listOf(0x01, 0x02, 0x03)
    private fun isMbc2(): Boolean = header.cartridgeType in listOf(0x05, 0x06)
    private fun isMbc3(): Boolean = header.cartridgeType in listOf(0x0F, 0x10, 0x11, 0x12, 0x13)
    private fun isMbc5(): Boolean = header.cartridgeType in listOf(0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E)

    companion object {
        fun extractRomIfZip(bytes: ByteArray): ByteArray {
            if (bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()
            ) {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        if (!entry.isDirectory && (name.endsWith(".gbc") || name.endsWith(".gb") || name.endsWith(".cgb"))) {
                            val bos = ByteArrayOutputStream()
                            val buffer = ByteArray(16384)
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                bos.write(buffer, 0, read)
                            }
                            return bos.toByteArray()
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            return bytes
        }

        fun parseHeader(rom: ByteArray): RomHeaderInfo {
            if (rom.size < 0x150) {
                return RomHeaderInfo(
                    title = "UNKNOWN ROM",
                    isCgbSupported = false,
                    isCgbOnly = false,
                    cartridgeType = 0,
                    cartridgeTypeName = "ROM ONLY",
                    romSizeCode = 0,
                    romBanksCount = 2,
                    ramSizeCode = 0,
                    ramBytesSize = 0x2000,
                    headerChecksum = 0,
                    crc32Hex = "00000000",
                    romSizeBytes = rom.size
                )
            }

            val cgbFlag = rom[0x0143].toInt() and 0xFF
            val isCgbSupported = (cgbFlag == 0x80 || cgbFlag == 0xC0)
            val isCgbOnly = (cgbFlag == 0xC0)

            val titleEnd = if (isCgbSupported) 0x0143 else 0x0144
            val sb = StringBuilder()
            for (addr in 0x0134 until titleEnd) {
                val b = rom[addr].toInt() and 0xFF
                if (b == 0) break
                if (b in 32..126) {
                    sb.append(b.toChar())
                }
            }
            val cleanTitle = sb.toString().trim().ifEmpty { "GBC GAME" }

            val cartType = rom[0x0147].toInt() and 0xFF
            val cartTypeName = when (cartType) {
                0x00 -> "ROM ONLY"
                0x01, 0x02, 0x03 -> "MBC1"
                0x05, 0x06 -> "MBC2"
                0x0F, 0x10, 0x11, 0x12, 0x13 -> "MBC3 + RTC"
                0x19, 0x1A, 0x1B -> "MBC5"
                0x1C, 0x1D, 0x1E -> "MBC5 + RUMBLE"
                else -> "MBC5 / GENERIC"
            }

            val romSizeCode = rom[0x0148].toInt() and 0xFF
            val calculatedBanks = maxOf(2, rom.size / 0x4000)

            val ramSizeCode = rom[0x0149].toInt() and 0xFF
            val ramBytes = when (ramSizeCode) {
                0x00 -> 0x2000
                0x01 -> 0x800
                0x02 -> 0x2000
                0x03 -> 0x8000
                0x04 -> 0x20000
                0x05 -> 0x10000
                else -> 0x8000
            }

            val headerChecksum = rom[0x014D].toInt() and 0xFF
            val crc = CRC32()
            crc.update(rom)
            val crcHex = String.format("%08X", crc.value)

            return RomHeaderInfo(
                title = cleanTitle,
                isCgbSupported = isCgbSupported,
                isCgbOnly = isCgbOnly,
                cartridgeType = cartType,
                cartridgeTypeName = cartTypeName,
                romSizeCode = romSizeCode,
                romBanksCount = calculatedBanks,
                ramSizeCode = ramSizeCode,
                ramBytesSize = ramBytes,
                headerChecksum = headerChecksum,
                crc32Hex = crcHex,
                romSizeBytes = rom.size
            )
        }
    }
}
