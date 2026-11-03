package com.example.emulator

import java.io.DataInputStream
import java.io.DataOutputStream

class GbcApu {
    companion object {
        const val SAMPLE_RATE = 44100
        const val CPU_CLOCK = 4194304
        private val DUTY_TABLE = arrayOf(
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 1), // 12.5%
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 1), // 25%
            intArrayOf(1, 0, 0, 0, 0, 1, 1, 1), // 50%
            intArrayOf(0, 1, 1, 1, 1, 1, 1, 0)  // 75%
        )
    }

    private val registers = IntArray(0x30) // 0xFF10..0xFF3F
    var soundEnabled: Boolean = true

    // Channel 1 (Pulse + Sweep)
    private var ch1Enabled = false
    private var ch1Freq = 0
    private var ch1Timer = 0
    private var ch1DutyStep = 0
    private var ch1Volume = 0
    private var ch1Length = 0

    // Channel 2 (Pulse)
    private var ch2Enabled = false
    private var ch2Freq = 0
    private var ch2Timer = 0
    private var ch2DutyStep = 0
    private var ch2Volume = 0
    private var ch2Length = 0

    // Channel 3 (Wave)
    private var ch3Enabled = false
    private var ch3Freq = 0
    private var ch3Timer = 0
    private var ch3SampleIndex = 0
    private var ch3Length = 0

    // Channel 4 (Noise)
    private var ch4Enabled = false
    private var ch4Volume = 0
    private var ch4Timer = 0
    private var ch4Lfsr = 0x7FFF
    private var ch4Length = 0

    private var frameSequencerCycles = 0
    private var frameSequencerStep = 0
    private var sampleAccumulator = 0

    // Stereo PCM buffer for one frame (~735 stereo samples = 1470 shorts per frame)
    val sampleBuffer = ShortArray(2048)
    var samplesProduced = 0

    fun reset() {
        registers.fill(0)
        ch1Enabled = false
        ch2Enabled = false
        ch3Enabled = false
        ch4Enabled = false
        ch4Lfsr = 0x7FFF
        samplesProduced = 0
        registers[0x26 - 0x10] = 0xF1
    }

    fun clearFrameSamples() {
        samplesProduced = 0
    }

    fun readRegister(address: Int): Int {
        val idx = address - 0xFF10
        if (idx !in registers.indices) return 0xFF
        if (address == 0xFF26) {
            var status = if (soundEnabled) 0x80 else 0x00
            if (ch1Enabled) status = status or 0x01
            if (ch2Enabled) status = status or 0x02
            if (ch3Enabled) status = status or 0x04
            if (ch4Enabled) status = status or 0x08
            return status or 0x70
        }
        return registers[idx] and 0xFF
    }

    fun writeRegister(address: Int, value: Int) {
        val idx = address - 0xFF10
        if (idx !in registers.indices) return
        val v = value and 0xFF
        registers[idx] = v

        when (address) {
            0xFF11 -> ch1Length = 64 - (v and 0x3F)
            0xFF12 -> {
                ch1Volume = (v shr 4) and 0x0F
                if ((v and 0xF8) == 0) ch1Enabled = false
            }
            0xFF13 -> ch1Freq = (ch1Freq and 0x700) or v
            0xFF14 -> {
                ch1Freq = (ch1Freq and 0xFF) or ((v and 0x07) shl 8)
                if ((v and 0x80) != 0) {
                    ch1Enabled = (registers[0x12 - 0x10] and 0xF8) != 0
                    if (ch1Length == 0) ch1Length = 64
                    ch1Volume = (registers[0x12 - 0x10] shr 4) and 0x0F
                    ch1Timer = (2048 - ch1Freq) * 4
                }
            }
            0xFF16 -> ch2Length = 64 - (v and 0x3F)
            0xFF17 -> {
                ch2Volume = (v shr 4) and 0x0F
                if ((v and 0xF8) == 0) ch2Enabled = false
            }
            0xFF18 -> ch2Freq = (ch2Freq and 0x700) or v
            0xFF19 -> {
                ch2Freq = (ch2Freq and 0xFF) or ((v and 0x07) shl 8)
                if ((v and 0x80) != 0) {
                    ch2Enabled = (registers[0x17 - 0x10] and 0xF8) != 0
                    if (ch2Length == 0) ch2Length = 64
                    ch2Volume = (registers[0x17 - 0x10] shr 4) and 0x0F
                    ch2Timer = (2048 - ch2Freq) * 4
                }
            }
            0xFF1A -> if ((v and 0x80) == 0) ch3Enabled = false
            0xFF1B -> ch3Length = 256 - v
            0xFF1D -> ch3Freq = (ch3Freq and 0x700) or v
            0xFF1E -> {
                ch3Freq = (ch3Freq and 0xFF) or ((v and 0x07) shl 8)
                if ((v and 0x80) != 0) {
                    ch3Enabled = (registers[0x1A - 0x10] and 0x80) != 0
                    if (ch3Length == 0) ch3Length = 256
                    ch3Timer = (2048 - ch3Freq) * 2
                    ch3SampleIndex = 0
                }
            }
            0xFF20 -> ch4Length = 64 - (v and 0x3F)
            0xFF21 -> {
                ch4Volume = (v shr 4) and 0x0F
                if ((v and 0xF8) == 0) ch4Enabled = false
            }
            0xFF23 -> {
                if ((v and 0x80) != 0) {
                    ch4Enabled = (registers[0x21 - 0x10] and 0xF8) != 0
                    if (ch4Length == 0) ch4Length = 64
                    ch4Volume = (registers[0x21 - 0x10] shr 4) and 0x0F
                    ch4Lfsr = 0x7FFF
                    ch4Timer = 64
                }
            }
            0xFF26 -> {
                soundEnabled = (v and 0x80) != 0
                if (!soundEnabled) {
                    ch1Enabled = false
                    ch2Enabled = false
                    ch3Enabled = false
                    ch4Enabled = false
                }
            }
        }
    }

    fun step(tCycles: Int) {
        if (!soundEnabled) return

        // Frame sequencer (512 Hz = 8192 T-cycles)
        frameSequencerCycles += tCycles
        if (frameSequencerCycles >= 8192) {
            frameSequencerCycles -= 8192
            frameSequencerStep = (frameSequencerStep + 1) and 7
            if ((frameSequencerStep and 1) == 0) {
                clockLengths()
            }
        }

        // Channel 1 timer
        if (ch1Enabled) {
            ch1Timer -= tCycles
            while (ch1Timer <= 0) {
                ch1Timer += maxOf(4, (2048 - ch1Freq) * 4)
                ch1DutyStep = (ch1DutyStep + 1) and 7
            }
        }

        // Channel 2 timer
        if (ch2Enabled) {
            ch2Timer -= tCycles
            while (ch2Timer <= 0) {
                ch2Timer += maxOf(4, (2048 - ch2Freq) * 4)
                ch2DutyStep = (ch2DutyStep + 1) and 7
            }
        }

        // Channel 3 timer
        if (ch3Enabled) {
            ch3Timer -= tCycles
            while (ch3Timer <= 0) {
                ch3Timer += maxOf(2, (2048 - ch3Freq) * 2)
                ch3SampleIndex = (ch3SampleIndex + 1) and 31
            }
        }

        // Channel 4 timer
        if (ch4Enabled) {
            ch4Timer -= tCycles
            while (ch4Timer <= 0) {
                val nr43 = registers[0x22 - 0x10]
                val divisorCode = nr43 and 0x07
                val divisor = if (divisorCode == 0) 8 else divisorCode * 16
                val shift = (nr43 shr 4) and 0x0F
                ch4Timer += maxOf(8, divisor shl minOf(shift, 10))
                val xorBit = (ch4Lfsr and 1) xor ((ch4Lfsr shr 1) and 1)
                ch4Lfsr = (ch4Lfsr shr 1) or (xorBit shl 14)
                if ((nr43 and 0x08) != 0) {
                    ch4Lfsr = (ch4Lfsr and 0xFFBF) or (xorBit shl 6)
                }
            }
        }

        // Downsample to 44100 Hz
        sampleAccumulator += tCycles * SAMPLE_RATE
        while (sampleAccumulator >= CPU_CLOCK) {
            sampleAccumulator -= CPU_CLOCK
            if (samplesProduced + 2 <= sampleBuffer.size) {
                val mixed = mixChannels()
                sampleBuffer[samplesProduced++] = mixed
                sampleBuffer[samplesProduced++] = mixed
            }
        }
    }

    private fun clockLengths() {
        if ((registers[0x14 - 0x10] and 0x40) != 0 && ch1Length > 0) {
            ch1Length--
            if (ch1Length == 0) ch1Enabled = false
        }
        if ((registers[0x19 - 0x10] and 0x40) != 0 && ch2Length > 0) {
            ch2Length--
            if (ch2Length == 0) ch2Enabled = false
        }
        if ((registers[0x1E - 0x10] and 0x40) != 0 && ch3Length > 0) {
            ch3Length--
            if (ch3Length == 0) ch3Enabled = false
        }
        if ((registers[0x23 - 0x10] and 0x40) != 0 && ch4Length > 0) {
            ch4Length--
            if (ch4Length == 0) ch4Enabled = false
        }
    }

    private fun mixChannels(): Short {
        var total = 0
        if (ch1Enabled) {
            val duty = (registers[0x11 - 0x10] shr 6) and 0x03
            val bit = DUTY_TABLE[duty][ch1DutyStep]
            total += if (bit != 0) ch1Volume else -ch1Volume
        }
        if (ch2Enabled) {
            val duty = (registers[0x16 - 0x10] shr 6) and 0x03
            val bit = DUTY_TABLE[duty][ch2DutyStep]
            total += if (bit != 0) ch2Volume else -ch2Volume
        }
        if (ch3Enabled) {
            val shiftCode = (registers[0x1C - 0x10] shr 5) and 0x03
            if (shiftCode != 0) {
                val waveByte = registers[0x20 + (ch3SampleIndex shr 1)]
                val nibble = if ((ch3SampleIndex and 1) == 0) (waveByte shr 4) and 0x0F else waveByte and 0x0F
                val shifted = nibble shr (shiftCode - 1)
                total += (shifted - 8)
            }
        }
        if (ch4Enabled) {
            val bit = (ch4Lfsr and 1) == 0
            total += if (bit) ch4Volume else -ch4Volume
        }
        return (total * 280).coerceIn(-32767, 32767).toShort()
    }

    fun saveState(out: DataOutputStream) {
        out.writeBoolean(soundEnabled)
        for (r in registers) out.writeInt(r)
    }

    fun loadState(input: DataInputStream) {
        soundEnabled = input.readBoolean()
        for (i in registers.indices) registers[i] = input.readInt()
    }
}
