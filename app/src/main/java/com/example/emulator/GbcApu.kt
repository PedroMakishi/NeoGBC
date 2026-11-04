package com.example.emulator

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

class GbcApu {
    companion object {
        const val SAMPLE_RATE = 44100
        const val CPU_CLOCK = 4194304
        private const val EXTENDED_STATE_MAGIC = 0x41505532 // "APU2"
        private val DUTY_TABLE = arrayOf(
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 1), // 12.5%
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 1), // 25%
            intArrayOf(1, 0, 0, 0, 0, 1, 1, 1), // 50%
            intArrayOf(0, 1, 1, 1, 1, 1, 1, 0)  // 75%
        )
    }

    private val registers = IntArray(0x30) // 0xFF10..0xFF3F
    var soundEnabled: Boolean = true

    // Channel 1 (Pulse + Sweep + Envelope)
    var ch1Enabled = false
        private set
    private var ch1Freq = 0
    private var ch1Timer = 0
    private var ch1DutyStep = 0
    var ch1Volume = 0
        private set
    private var ch1Length = 0
    private var ch1EnvPeriod = 0
    private var ch1EnvDirection = false
    private var ch1EnvTimer = 0
    private var ch1SweepEnabled = false
    private var ch1SweepShadowFreq = 0
    private var ch1SweepTimer = 0

    // Channel 2 (Pulse + Envelope)
    var ch2Enabled = false
        private set
    private var ch2Freq = 0
    private var ch2Timer = 0
    private var ch2DutyStep = 0
    var ch2Volume = 0
        private set
    private var ch2Length = 0
    private var ch2EnvPeriod = 0
    private var ch2EnvDirection = false
    private var ch2EnvTimer = 0

    // Channel 3 (Wave)
    var ch3Enabled = false
        private set
    private var ch3Freq = 0
    private var ch3Timer = 0
    private var ch3SampleIndex = 0
    private var ch3Length = 0

    // Channel 4 (Noise + Envelope)
    var ch4Enabled = false
        private set
    var ch4Volume = 0
        private set
    private var ch4Timer = 0
    private var ch4Lfsr = 0x7FFF
    private var ch4Length = 0
    private var ch4EnvPeriod = 0
    private var ch4EnvDirection = false
    private var ch4EnvTimer = 0

    private var frameSequencerCycles = 0
    private var frameSequencerStep = 0
    private var sampleAccumulator = 0L

    // Stereo PCM buffer for one frame (~735 stereo samples = 1470 shorts per frame)
    val sampleBuffer = ShortArray(2048)
    var samplesProduced = 0

    init {
        reset()
    }

    fun reset() {
        registers.fill(0)
        soundEnabled = true
        ch1Enabled = false
        ch2Enabled = false
        ch3Enabled = false
        ch4Enabled = false
        ch1Freq = 0
        ch1Timer = 0
        ch1DutyStep = 0
        ch1Volume = 0
        ch1Length = 0
        ch1EnvPeriod = 0
        ch1EnvDirection = false
        ch1EnvTimer = 0
        ch1SweepEnabled = false
        ch1SweepShadowFreq = 0
        ch1SweepTimer = 0

        ch2Freq = 0
        ch2Timer = 0
        ch2DutyStep = 0
        ch2Volume = 0
        ch2Length = 0
        ch2EnvPeriod = 0
        ch2EnvDirection = false
        ch2EnvTimer = 0

        ch3Freq = 0
        ch3Timer = 0
        ch3SampleIndex = 0
        ch3Length = 0

        ch4Volume = 0
        ch4Timer = 0
        ch4Lfsr = 0x7FFF
        ch4Length = 0
        ch4EnvPeriod = 0
        ch4EnvDirection = false
        ch4EnvTimer = 0

        frameSequencerCycles = 0
        frameSequencerStep = 0
        sampleAccumulator = 0L
        samplesProduced = 0

        // Standard post-boot hardware defaults for master volume, stereo routing, and APU power
        registers[0x24 - 0x10] = 0x77 // NR50: Max Left/Right volume
        registers[0x25 - 0x10] = 0xF3 // NR51: Default stereo panning
        registers[0x26 - 0x10] = 0xF1 // NR52: Audio enabled
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

        if (address == 0xFF26) {
            val wasEnabled = soundEnabled
            soundEnabled = (v and 0x80) != 0
            registers[idx] = (v and 0x80) or 0x70
            if (!soundEnabled) {
                // Turning off APU power clears 0xFF10..0xFF25 and silences all channels
                for (regIdx in 0..(0x25 - 0x10)) {
                    registers[regIdx] = 0
                }
                ch1Enabled = false
                ch2Enabled = false
                ch3Enabled = false
                ch4Enabled = false
                ch1Volume = 0
                ch2Volume = 0
                ch4Volume = 0
            } else if (!wasEnabled) {
                frameSequencerStep = 0
                frameSequencerCycles = 0
                registers[0x24 - 0x10] = 0x77
                registers[0x25 - 0x10] = 0xF3
            }
            return
        }

        // When APU power is off, writes to 0xFF10..0xFF25 are ignored (Wave RAM 0xFF30..0xFF3F remains writable)
        if (!soundEnabled && address in 0xFF10..0xFF25) {
            return
        }

        registers[idx] = v

        when (address) {
            0xFF10 -> {
                // If sweep negate is cleared after being used, hardware disables Ch1
                val period = (v shr 4) and 0x07
                val shift = v and 0x07
                ch1SweepEnabled = period != 0 || shift != 0
            }
            0xFF11 -> ch1Length = 64 - (v and 0x3F)
            0xFF12 -> {
                ch1EnvDirection = (v and 0x08) != 0
                ch1EnvPeriod = v and 0x07
                ch1EnvTimer = if (ch1EnvPeriod == 0) 8 else ch1EnvPeriod
                ch1Volume = (v shr 4) and 0x0F
                if ((v and 0xF8) == 0) {
                    ch1Enabled = false
                    ch1Volume = 0
                }
            }
            0xFF13 -> ch1Freq = (ch1Freq and 0x700) or v
            0xFF14 -> {
                ch1Freq = (ch1Freq and 0xFF) or ((v and 0x07) shl 8)
                if ((v and 0x80) != 0) {
                    triggerChannel1()
                }
            }
            0xFF16 -> ch2Length = 64 - (v and 0x3F)
            0xFF17 -> {
                ch2EnvDirection = (v and 0x08) != 0
                ch2EnvPeriod = v and 0x07
                ch2EnvTimer = if (ch2EnvPeriod == 0) 8 else ch2EnvPeriod
                ch2Volume = (v shr 4) and 0x0F
                if ((v and 0xF8) == 0) {
                    ch2Enabled = false
                    ch2Volume = 0
                }
            }
            0xFF18 -> ch2Freq = (ch2Freq and 0x700) or v
            0xFF19 -> {
                ch2Freq = (ch2Freq and 0xFF) or ((v and 0x07) shl 8)
                if ((v and 0x80) != 0) {
                    triggerChannel2()
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
                    ch3Timer = maxOf(2, (2048 - ch3Freq) * 2)
                    ch3SampleIndex = 0
                }
            }
            0xFF20 -> ch4Length = 64 - (v and 0x3F)
            0xFF21 -> {
                ch4EnvDirection = (v and 0x08) != 0
                ch4EnvPeriod = v and 0x07
                ch4EnvTimer = if (ch4EnvPeriod == 0) 8 else ch4EnvPeriod
                ch4Volume = (v shr 4) and 0x0F
                if ((v and 0xF8) == 0) {
                    ch4Enabled = false
                    ch4Volume = 0
                }
            }
            0xFF23 -> {
                if ((v and 0x80) != 0) {
                    triggerChannel4()
                }
            }
        }
    }

    private fun triggerChannel1() {
        val nr12 = registers[0x12 - 0x10]
        val dacEnabled = (nr12 and 0xF8) != 0
        ch1Enabled = dacEnabled
        if (ch1Length == 0) ch1Length = 64
        ch1Timer = maxOf(4, (2048 - ch1Freq) * 4)
        ch1Volume = (nr12 shr 4) and 0x0F
        ch1EnvDirection = (nr12 and 0x08) != 0
        ch1EnvPeriod = nr12 and 0x07
        ch1EnvTimer = if (ch1EnvPeriod == 0) 8 else ch1EnvPeriod

        val nr10 = registers[0x10 - 0x10]
        val sweepPeriod = (nr10 shr 4) and 0x07
        val sweepShift = nr10 and 0x07
        ch1SweepShadowFreq = ch1Freq
        ch1SweepTimer = if (sweepPeriod == 0) 8 else sweepPeriod
        ch1SweepEnabled = sweepPeriod != 0 || sweepShift != 0
        if (sweepShift != 0 && calculateCh1SweepFreq() > 2047) {
            ch1Enabled = false
        }
    }

    private fun triggerChannel2() {
        val nr22 = registers[0x17 - 0x10]
        val dacEnabled = (nr22 and 0xF8) != 0
        ch2Enabled = dacEnabled
        if (ch2Length == 0) ch2Length = 64
        ch2Timer = maxOf(4, (2048 - ch2Freq) * 4)
        ch2Volume = (nr22 shr 4) and 0x0F
        ch2EnvDirection = (nr22 and 0x08) != 0
        ch2EnvPeriod = nr22 and 0x07
        ch2EnvTimer = if (ch2EnvPeriod == 0) 8 else ch2EnvPeriod
    }

    private fun triggerChannel4() {
        val nr42 = registers[0x21 - 0x10]
        val dacEnabled = (nr42 and 0xF8) != 0
        ch4Enabled = dacEnabled
        if (ch4Length == 0) ch4Length = 64
        ch4Volume = (nr42 shr 4) and 0x0F
        ch4EnvDirection = (nr42 and 0x08) != 0
        ch4EnvPeriod = nr42 and 0x07
        ch4EnvTimer = if (ch4EnvPeriod == 0) 8 else ch4EnvPeriod
        ch4Lfsr = 0x7FFF
        ch4Timer = computeCh4Period()
    }

    private fun computeCh4Period(): Int {
        val nr43 = registers[0x22 - 0x10]
        val divisorCode = nr43 and 0x07
        val divisor = if (divisorCode == 0) 8 else divisorCode * 16
        val shift = (nr43 shr 4) and 0x0F
        return maxOf(8, divisor shl minOf(shift, 13))
    }

    fun step(tCycles: Int) {
        if (tCycles <= 0) return
        if (tCycles > 64) {
            var remaining = tCycles
            while (remaining > 0) {
                val slice = minOf(32, remaining)
                stepSlice(slice)
                remaining -= slice
            }
        } else {
            stepSlice(tCycles)
        }
    }

    private fun stepSlice(tCycles: Int) {
        if (!soundEnabled) {
            // Produce silence while sound is disabled so AudioTrack stays smoothly fed
            sampleAccumulator += tCycles.toLong() * SAMPLE_RATE
            while (sampleAccumulator >= CPU_CLOCK) {
                sampleAccumulator -= CPU_CLOCK
                if (samplesProduced + 2 <= sampleBuffer.size) {
                    sampleBuffer[samplesProduced++] = 0
                    sampleBuffer[samplesProduced++] = 0
                }
            }
            return
        }

        // Frame sequencer (512 Hz = 8192 T-cycles)
        frameSequencerCycles += tCycles
        while (frameSequencerCycles >= 8192) {
            frameSequencerCycles -= 8192
            // Step 0, 2, 4, 6: Clock Length Counters (256 Hz)
            if ((frameSequencerStep and 1) == 0) {
                clockLengths()
            }
            // Step 2, 6: Clock Channel 1 Frequency Sweep (128 Hz)
            if (frameSequencerStep == 2 || frameSequencerStep == 6) {
                clockSweep()
            }
            // Step 7: Clock Volume Envelopes (64 Hz)
            if (frameSequencerStep == 7) {
                clockEnvelopes()
            }
            frameSequencerStep = (frameSequencerStep + 1) and 7
        }

        // Channel 1 timer
        if (ch1Enabled) {
            ch1Timer -= tCycles
            val period = maxOf(4, (2048 - ch1Freq) * 4)
            while (ch1Timer <= 0) {
                ch1Timer += period
                ch1DutyStep = (ch1DutyStep + 1) and 7
            }
        }

        // Channel 2 timer
        if (ch2Enabled) {
            ch2Timer -= tCycles
            val period = maxOf(4, (2048 - ch2Freq) * 4)
            while (ch2Timer <= 0) {
                ch2Timer += period
                ch2DutyStep = (ch2DutyStep + 1) and 7
            }
        }

        // Channel 3 timer
        if (ch3Enabled) {
            ch3Timer -= tCycles
            val period = maxOf(2, (2048 - ch3Freq) * 2)
            while (ch3Timer <= 0) {
                ch3Timer += period
                ch3SampleIndex = (ch3SampleIndex + 1) and 31
            }
        }

        // Channel 4 timer (Noise LFSR)
        if (ch4Enabled) {
            val nr43 = registers[0x22 - 0x10]
            val shift = (nr43 shr 4) and 0x0F
            // On real Game Boy hardware, shift values 14 and 15 freeze the LFSR clock
            if (shift < 14) {
                val period = computeCh4Period()
                ch4Timer -= tCycles
                while (ch4Timer <= 0) {
                    ch4Timer += period
                    val xorBit = (ch4Lfsr and 1) xor ((ch4Lfsr shr 1) and 1)
                    ch4Lfsr = (ch4Lfsr shr 1) or (xorBit shl 14)
                    if ((nr43 and 0x08) != 0) {
                        ch4Lfsr = (ch4Lfsr and 0xFFBF) or (xorBit shl 6)
                    }
                }
            }
        }

        // Downsample to 44100 Hz Stereo
        sampleAccumulator += tCycles.toLong() * SAMPLE_RATE
        while (sampleAccumulator >= CPU_CLOCK) {
            sampleAccumulator -= CPU_CLOCK
            if (samplesProduced + 2 <= sampleBuffer.size) {
                mixStereoSample()
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

    private fun clockSweep() {
        if (!ch1Enabled || !ch1SweepEnabled) return
        ch1SweepTimer--
        if (ch1SweepTimer <= 0) {
            val nr10 = registers[0x10 - 0x10]
            val period = (nr10 shr 4) and 0x07
            val shift = nr10 and 0x07
            ch1SweepTimer = if (period == 0) 8 else period
            if (period != 0) {
                val newFreq = calculateCh1SweepFreq()
                if (newFreq > 2047) {
                    ch1Enabled = false
                } else if (shift != 0) {
                    ch1SweepShadowFreq = newFreq
                    ch1Freq = newFreq
                    registers[0x13 - 0x10] = newFreq and 0xFF
                    registers[0x14 - 0x10] = (registers[0x14 - 0x10] and 0xF8) or ((newFreq shr 8) and 0x07)
                    if (calculateCh1SweepFreq() > 2047) {
                        ch1Enabled = false
                    }
                }
            }
        }
    }

    private fun calculateCh1SweepFreq(): Int {
        val nr10 = registers[0x10 - 0x10]
        val shift = nr10 and 0x07
        val delta = ch1SweepShadowFreq shr shift
        return if ((nr10 and 0x08) != 0) {
            (ch1SweepShadowFreq - delta).coerceAtLeast(0)
        } else {
            ch1SweepShadowFreq + delta
        }
    }

    private fun clockEnvelopes() {
        // Channel 1 Envelope
        if (ch1Enabled && ch1EnvPeriod > 0) {
            ch1EnvTimer--
            if (ch1EnvTimer <= 0) {
                ch1EnvTimer = ch1EnvPeriod
                if (ch1EnvDirection) {
                    if (ch1Volume < 15) ch1Volume++
                } else {
                    if (ch1Volume > 0) ch1Volume--
                }
            }
        }

        // Channel 2 Envelope
        if (ch2Enabled && ch2EnvPeriod > 0) {
            ch2EnvTimer--
            if (ch2EnvTimer <= 0) {
                ch2EnvTimer = ch2EnvPeriod
                if (ch2EnvDirection) {
                    if (ch2Volume < 15) ch2Volume++
                } else {
                    if (ch2Volume > 0) ch2Volume--
                }
            }
        }

        // Channel 4 Envelope (Noise)
        if (ch4Enabled && ch4EnvPeriod > 0) {
            ch4EnvTimer--
            if (ch4EnvTimer <= 0) {
                ch4EnvTimer = ch4EnvPeriod
                if (ch4EnvDirection) {
                    if (ch4Volume < 15) ch4Volume++
                } else {
                    if (ch4Volume > 0) ch4Volume--
                }
            }
        }
    }

    private fun mixStereoSample() {
        val nr50 = registers[0x24 - 0x10]
        val nr51 = registers[0x25 - 0x10]

        var ch1Out = 0
        if (ch1Enabled && ch1Volume > 0) {
            val duty = (registers[0x11 - 0x10] shr 6) and 0x03
            val bit = DUTY_TABLE[duty][ch1DutyStep]
            ch1Out = if (bit != 0) ch1Volume else -ch1Volume
        }

        var ch2Out = 0
        if (ch2Enabled && ch2Volume > 0) {
            val duty = (registers[0x16 - 0x10] shr 6) and 0x03
            val bit = DUTY_TABLE[duty][ch2DutyStep]
            ch2Out = if (bit != 0) ch2Volume else -ch2Volume
        }

        var ch3Out = 0
        if (ch3Enabled && (registers[0x1A - 0x10] and 0x80) != 0) {
            val shiftCode = (registers[0x1C - 0x10] shr 5) and 0x03
            if (shiftCode != 0) {
                val waveByte = registers[0x20 + (ch3SampleIndex shr 1)]
                val nibble = if ((ch3SampleIndex and 1) == 0) (waveByte shr 4) and 0x0F else waveByte and 0x0F
                // Center wave sample symmetrically around 0 before applying volume shift
                val centered = (nibble * 2) - 15
                ch3Out = centered shr (shiftCode - 1)
            }
        }

        var ch4Out = 0
        if (ch4Enabled && ch4Volume > 0) {
            val bit = (ch4Lfsr and 1) == 0
            ch4Out = if (bit) ch4Volume else -ch4Volume
        }

        var leftMix = 0
        if ((nr51 and 0x10) != 0) leftMix += ch1Out
        if ((nr51 and 0x20) != 0) leftMix += ch2Out
        if ((nr51 and 0x40) != 0) leftMix += ch3Out
        if ((nr51 and 0x80) != 0) leftMix += ch4Out

        var rightMix = 0
        if ((nr51 and 0x01) != 0) rightMix += ch1Out
        if ((nr51 and 0x02) != 0) rightMix += ch2Out
        if ((nr51 and 0x04) != 0) rightMix += ch3Out
        if ((nr51 and 0x08) != 0) rightMix += ch4Out

        val leftVol = ((nr50 shr 4) and 0x07) + 1
        val rightVol = (nr50 and 0x07) + 1

        val leftPcm = (leftMix * leftVol * 35).coerceIn(-32767, 32767).toShort()
        val rightPcm = (rightMix * rightVol * 35).coerceIn(-32767, 32767).toShort()

        sampleBuffer[samplesProduced++] = leftPcm
        sampleBuffer[samplesProduced++] = rightPcm
    }

    fun saveState(out: DataOutputStream) {
        out.writeBoolean(soundEnabled)
        for (r in registers) out.writeInt(r)
    }

    fun loadState(input: DataInputStream) {
        soundEnabled = input.readBoolean()
        for (i in registers.indices) registers[i] = input.readInt()
        reconstructStateFromRegisters()
    }

    /**
     * Reconstructs clean internal channel state from the hardware registers after loading a
     * legacy Version 1 save state, ensuring no transient SFX or noise channel remains stuck.
     */
    private fun reconstructStateFromRegisters() {
        samplesProduced = 0
        sampleAccumulator = 0L
        frameSequencerCycles = 0
        frameSequencerStep = 0

        if (!soundEnabled) {
            ch1Enabled = false
            ch2Enabled = false
            ch3Enabled = false
            ch4Enabled = false
            ch1Volume = 0
            ch2Volume = 0
            ch4Volume = 0
            return
        }

        val nr11 = registers[0x11 - 0x10]
        val nr12 = registers[0x12 - 0x10]
        val nr13 = registers[0x13 - 0x10]
        val nr14 = registers[0x14 - 0x10]
        ch1Length = 64 - (nr11 and 0x3F)
        ch1Freq = (nr13 and 0xFF) or ((nr14 and 0x07) shl 8)
        ch1Timer = maxOf(4, (2048 - ch1Freq) * 4)
        ch1EnvDirection = (nr12 and 0x08) != 0
        ch1EnvPeriod = nr12 and 0x07
        ch1EnvTimer = if (ch1EnvPeriod == 0) 8 else ch1EnvPeriod
        ch1Volume = if (!ch1EnvDirection && ch1EnvPeriod > 0) 0 else ((nr12 shr 4) and 0x0F)
        ch1Enabled = (nr12 and 0xF8) != 0 && ch1Volume > 0

        val nr21 = registers[0x16 - 0x10]
        val nr22 = registers[0x17 - 0x10]
        val nr23 = registers[0x18 - 0x10]
        val nr24 = registers[0x19 - 0x10]
        ch2Length = 64 - (nr21 and 0x3F)
        ch2Freq = (nr23 and 0xFF) or ((nr24 and 0x07) shl 8)
        ch2Timer = maxOf(4, (2048 - ch2Freq) * 4)
        ch2EnvDirection = (nr22 and 0x08) != 0
        ch2EnvPeriod = nr22 and 0x07
        ch2EnvTimer = if (ch2EnvPeriod == 0) 8 else ch2EnvPeriod
        ch2Volume = if (!ch2EnvDirection && ch2EnvPeriod > 0) 0 else ((nr22 shr 4) and 0x0F)
        ch2Enabled = (nr22 and 0xF8) != 0 && ch2Volume > 0

        val nr30 = registers[0x1A - 0x10]
        val nr31 = registers[0x1B - 0x10]
        val nr33 = registers[0x1D - 0x10]
        val nr34 = registers[0x1E - 0x10]
        ch3Length = 256 - (nr31 and 0xFF)
        ch3Freq = (nr33 and 0xFF) or ((nr34 and 0x07) shl 8)
        ch3Timer = maxOf(2, (2048 - ch3Freq) * 2)
        ch3Enabled = (nr30 and 0x80) != 0

        val nr41 = registers[0x20 - 0x10]
        val nr42 = registers[0x21 - 0x10]
        ch4Length = 64 - (nr41 and 0x3F)
        ch4EnvDirection = (nr42 and 0x08) != 0
        ch4EnvPeriod = nr42 and 0x07
        ch4EnvTimer = if (ch4EnvPeriod == 0) 8 else ch4EnvPeriod
        ch4Lfsr = 0x7FFF
        ch4Timer = computeCh4Period()
        // Prevent legacy save states from restoring a stuck transient noise SFX
        ch4Volume = 0
        ch4Enabled = false
    }

    fun saveExtendedState(out: DataOutputStream) {
        out.writeInt(EXTENDED_STATE_MAGIC)
        out.writeBoolean(ch1Enabled)
        out.writeInt(ch1Freq)
        out.writeInt(ch1Timer)
        out.writeInt(ch1DutyStep)
        out.writeInt(ch1Volume)
        out.writeInt(ch1Length)
        out.writeInt(ch1EnvPeriod)
        out.writeBoolean(ch1EnvDirection)
        out.writeInt(ch1EnvTimer)
        out.writeBoolean(ch1SweepEnabled)
        out.writeInt(ch1SweepShadowFreq)
        out.writeInt(ch1SweepTimer)

        out.writeBoolean(ch2Enabled)
        out.writeInt(ch2Freq)
        out.writeInt(ch2Timer)
        out.writeInt(ch2DutyStep)
        out.writeInt(ch2Volume)
        out.writeInt(ch2Length)
        out.writeInt(ch2EnvPeriod)
        out.writeBoolean(ch2EnvDirection)
        out.writeInt(ch2EnvTimer)

        out.writeBoolean(ch3Enabled)
        out.writeInt(ch3Freq)
        out.writeInt(ch3Timer)
        out.writeInt(ch3SampleIndex)
        out.writeInt(ch3Length)

        out.writeBoolean(ch4Enabled)
        out.writeInt(ch4Volume)
        out.writeInt(ch4Timer)
        out.writeInt(ch4Lfsr)
        out.writeInt(ch4Length)
        out.writeInt(ch4EnvPeriod)
        out.writeBoolean(ch4EnvDirection)
        out.writeInt(ch4EnvTimer)

        out.writeInt(frameSequencerCycles)
        out.writeInt(frameSequencerStep)
    }

    fun loadExtendedStateIfPresent(input: DataInputStream) {
        try {
            val magic = input.readInt()
            if (magic != EXTENDED_STATE_MAGIC) return
            ch1Enabled = input.readBoolean()
            ch1Freq = input.readInt()
            ch1Timer = input.readInt()
            ch1DutyStep = input.readInt()
            ch1Volume = input.readInt().coerceIn(0, 15)
            ch1Length = input.readInt()
            ch1EnvPeriod = input.readInt()
            ch1EnvDirection = input.readBoolean()
            ch1EnvTimer = input.readInt().coerceAtLeast(1)
            ch1SweepEnabled = input.readBoolean()
            ch1SweepShadowFreq = input.readInt()
            ch1SweepTimer = input.readInt().coerceAtLeast(1)

            ch2Enabled = input.readBoolean()
            ch2Freq = input.readInt()
            ch2Timer = input.readInt()
            ch2DutyStep = input.readInt()
            ch2Volume = input.readInt().coerceIn(0, 15)
            ch2Length = input.readInt()
            ch2EnvPeriod = input.readInt()
            ch2EnvDirection = input.readBoolean()
            ch2EnvTimer = input.readInt().coerceAtLeast(1)

            ch3Enabled = input.readBoolean()
            ch3Freq = input.readInt()
            ch3Timer = input.readInt()
            ch3SampleIndex = input.readInt() and 31
            ch3Length = input.readInt()

            ch4Enabled = input.readBoolean()
            ch4Volume = input.readInt().coerceIn(0, 15)
            ch4Timer = input.readInt()
            ch4Lfsr = input.readInt()
            ch4Length = input.readInt()
            ch4EnvPeriod = input.readInt()
            ch4EnvDirection = input.readBoolean()
            ch4EnvTimer = input.readInt().coerceAtLeast(1)

            frameSequencerCycles = input.readInt().coerceIn(0, 8191)
            frameSequencerStep = input.readInt() and 7
        } catch (_: EOFException) {
            // Legacy Version 1 save state without trailing APU2 block; reconstructStateFromRegisters() is already applied
        }
    }
}

