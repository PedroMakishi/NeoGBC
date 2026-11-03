package com.example.emulator

import java.io.DataInputStream
import java.io.DataOutputStream

class LR35902Cpu(
    private val mmu: GbcMmu
) {
    // 8-bit registers
    var a: Int = 0x11
    var f: Int = 0x80 // bit7=Z, bit6=N, bit5=H, bit4=C
    var b: Int = 0x00
    var c: Int = 0x00
    var d: Int = 0xFF
    var e: Int = 0x56
    var h: Int = 0x00
    var l: Int = 0x0D

    // 16-bit Stack Pointer & Program Counter
    var sp: Int = 0xFFFE
    var pc: Int = 0x0100

    // Interrupt Master Enable & Halt State
    var ime: Boolean = false
    private var imePending: Boolean = false
    var halted: Boolean = false
    private var haltBug: Boolean = false

    var totalCycles: Long = 0L

    init {
        reset()
    }

    fun reset() {
        if (mmu.isCgb) {
            a = 0x11
            f = 0x80
            b = 0x00
            c = 0x00
            d = 0xFF
            e = 0x56
            h = 0x00
            l = 0x0D
        } else {
            a = 0x01
            f = 0xB0
            b = 0x00
            c = 0x13
            d = 0x00
            e = 0xD8
            h = 0x01
            l = 0x4D
        }
        sp = 0xFFFE
        pc = 0x0100
        ime = false
        imePending = false
        halted = false
        haltBug = false
        totalCycles = 0L
    }

    // Flag helpers
    private var flagZ: Boolean
        get() = (f and 0x80) != 0
        set(v) { f = if (v) (f or 0x80) else (f and 0x70) }

    private var flagN: Boolean
        get() = (f and 0x40) != 0
        set(v) { f = if (v) (f or 0x40) else (f and 0xB0) }

    private var flagH: Boolean
        get() = (f and 0x20) != 0
        set(v) { f = if (v) (f or 0x20) else (f and 0xD0) }

    private var flagC: Boolean
        get() = (f and 0x10) != 0
        set(v) { f = if (v) (f or 0x10) else (f and 0xE0) }

    private var af: Int
        get() = ((a and 0xFF) shl 8) or (f and 0xF0)
        set(v) {
            a = (v shr 8) and 0xFF
            f = v and 0xF0
        }

    private var bc: Int
        get() = ((b and 0xFF) shl 8) or (c and 0xFF)
        set(v) {
            b = (v shr 8) and 0xFF
            c = v and 0xFF
        }

    private var de: Int
        get() = ((d and 0xFF) shl 8) or (e and 0xFF)
        set(v) {
            d = (v shr 8) and 0xFF
            e = v and 0xFF
        }

    var hl: Int
        get() = ((h and 0xFF) shl 8) or (l and 0xFF)
        set(v) {
            h = (v shr 8) and 0xFF
            l = v and 0xFF
        }

    fun step(): Int {
        val pendingInterrupts = mmu.interruptFlag and mmu.interruptEnable and 0x1F
        if (halted && pendingInterrupts != 0) {
            halted = false
        }

        if (ime && pendingInterrupts != 0) {
            val irqCycles = serviceInterrupt(pendingInterrupts)
            mmu.stepTimersAndDma(irqCycles)
            totalCycles += irqCycles
            return irqCycles
        }

        if (halted) {
            mmu.stepTimersAndDma(4)
            totalCycles += 4
            return 4
        }

        val activateImeAfter = imePending
        val opcode = fetchByte()
        if (haltBug) {
            pc = (pc - 1) and 0xFFFF
            haltBug = false
        }

        val tCycles = executeOpcode(opcode)

        if (activateImeAfter && imePending) {
            ime = true
            imePending = false
        }

        mmu.stepTimersAndDma(tCycles)
        totalCycles += tCycles
        return tCycles
    }

    private fun serviceInterrupt(pending: Int): Int {
        ime = false
        imePending = false
        val bit = Integer.numberOfTrailingZeros(pending)
        mmu.interruptFlag = mmu.interruptFlag and (1 shl bit).inv()
        pushWord(pc)
        pc = 0x0040 + (bit * 8)
        return 20
    }

    private fun fetchByte(): Int {
        val v = mmu.readByte(pc)
        pc = (pc + 1) and 0xFFFF
        return v
    }

    private fun fetchSignedByte(): Int {
        return fetchByte().toByte().toInt()
    }

    private fun fetchWord(): Int {
        val lo = fetchByte()
        val hi = fetchByte()
        return (hi shl 8) or lo
    }

    private fun readWord(addr: Int): Int {
        val lo = mmu.readByte(addr)
        val hi = mmu.readByte((addr + 1) and 0xFFFF)
        return (hi shl 8) or lo
    }

    private fun writeWord(addr: Int, value: Int) {
        mmu.writeByte(addr, value and 0xFF)
        mmu.writeByte((addr + 1) and 0xFFFF, (value shr 8) and 0xFF)
    }

    private fun pushWord(value: Int) {
        sp = (sp - 1) and 0xFFFF
        mmu.writeByte(sp, (value shr 8) and 0xFF)
        sp = (sp - 1) and 0xFFFF
        mmu.writeByte(sp, value and 0xFF)
    }

    private fun popWord(): Int {
        val lo = mmu.readByte(sp)
        sp = (sp + 1) and 0xFFFF
        val hi = mmu.readByte(sp)
        sp = (sp + 1) and 0xFFFF
        return (hi shl 8) or lo
    }

    private fun getReg8(idx: Int): Int = when (idx and 7) {
        0 -> b
        1 -> c
        2 -> d
        3 -> e
        4 -> h
        5 -> l
        6 -> mmu.readByte(hl)
        else -> a
    }

    private fun setReg8(idx: Int, value: Int) {
        val v = value and 0xFF
        when (idx and 7) {
            0 -> b = v
            1 -> c = v
            2 -> d = v
            3 -> e = v
            4 -> h = v
            5 -> l = v
            6 -> mmu.writeByte(hl, v)
            else -> a = v
        }
    }

    private fun aluAdd(v: Int, withCarry: Boolean) {
        val carry = if (withCarry && flagC) 1 else 0
        val res = a + v + carry
        flagZ = (res and 0xFF) == 0
        flagN = false
        flagH = ((a and 0x0F) + (v and 0x0F) + carry) > 0x0F
        flagC = res > 0xFF
        a = res and 0xFF
    }

    private fun aluSub(v: Int, withCarry: Boolean) {
        val carry = if (withCarry && flagC) 1 else 0
        val res = a - v - carry
        flagZ = (res and 0xFF) == 0
        flagN = true
        flagH = ((a and 0x0F) - (v and 0x0F) - carry) < 0
        flagC = res < 0
        a = res and 0xFF
    }

    private fun aluAnd(v: Int) {
        a = (a and v) and 0xFF
        flagZ = a == 0
        flagN = false
        flagH = true
        flagC = false
    }

    private fun aluXor(v: Int) {
        a = (a xor v) and 0xFF
        flagZ = a == 0
        flagN = false
        flagH = false
        flagC = false
    }

    private fun aluOr(v: Int) {
        a = (a or v) and 0xFF
        flagZ = a == 0
        flagN = false
        flagH = false
        flagC = false
    }

    private fun aluCp(v: Int) {
        val res = a - v
        flagZ = (res and 0xFF) == 0
        flagN = true
        flagH = ((a and 0x0F) - (v and 0x0F)) < 0
        flagC = res < 0
    }

    private fun aluInc(v: Int): Int {
        val res = (v + 1) and 0xFF
        flagZ = res == 0
        flagN = false
        flagH = (v and 0x0F) == 0x0F
        return res
    }

    private fun aluDec(v: Int): Int {
        val res = (v - 1) and 0xFF
        flagZ = res == 0
        flagN = true
        flagH = (v and 0x0F) == 0x00
        return res
    }

    private fun aluAddHl(v: Int) {
        val orig = hl
        val res = orig + v
        flagN = false
        flagH = ((orig and 0x0FFF) + (v and 0x0FFF)) > 0x0FFF
        flagC = res > 0xFFFF
        hl = res and 0xFFFF
    }

    private fun executeOpcode(op: Int): Int {
        // Block 0x40..0x7F: LD r, r' and HALT (0x76)
        if (op in 0x40..0x7F) {
            if (op == 0x76) {
                val pending = mmu.interruptFlag and mmu.interruptEnable and 0x1F
                if (!ime && pending != 0) {
                    haltBug = true
                } else {
                    halted = true
                }
                return 4
            }
            val dst = (op shr 3) and 7
            val src = op and 7
            setReg8(dst, getReg8(src))
            return if (dst == 6 || src == 6) 8 else 4
        }

        // Block 0x80..0xBF: 8-bit ALU operations on register/HL
        if (op in 0x80..0xBF) {
            val src = op and 7
            val v = getReg8(src)
            when ((op shr 3) and 7) {
                0 -> aluAdd(v, false)
                1 -> aluAdd(v, true)
                2 -> aluSub(v, false)
                3 -> aluSub(v, true)
                4 -> aluAnd(v)
                5 -> aluXor(v)
                6 -> aluOr(v)
                7 -> aluCp(v)
            }
            return if (src == 6) 8 else 4
        }

        return when (op) {
            0x00 -> 4 // NOP
            0x01 -> { bc = fetchWord(); 12 }
            0x02 -> { mmu.writeByte(bc, a); 8 }
            0x03 -> { bc = (bc + 1) and 0xFFFF; 8 }
            0x04 -> { b = aluInc(b); 4 }
            0x05 -> { b = aluDec(b); 4 }
            0x06 -> { b = fetchByte(); 8 }
            0x07 -> { // RLCA
                val cBit = (a shr 7) and 1
                a = ((a shl 1) or cBit) and 0xFF
                flagZ = false; flagN = false; flagH = false; flagC = cBit != 0
                4
            }
            0x08 -> { writeWord(fetchWord(), sp); 20 }
            0x09 -> { aluAddHl(bc); 8 }
            0x0A -> { a = mmu.readByte(bc); 8 }
            0x0B -> { bc = (bc - 1) and 0xFFFF; 8 }
            0x0C -> { c = aluInc(c); 4 }
            0x0D -> { c = aluDec(c); 4 }
            0x0E -> { c = fetchByte(); 8 }
            0x0F -> { // RRCA
                val cBit = a and 1
                a = ((a shr 1) or (cBit shl 7)) and 0xFF
                flagZ = false; flagN = false; flagH = false; flagC = cBit != 0
                4
            }
            0x10 -> { // STOP
                fetchByte()
                mmu.performSpeedSwitch()
                4
            }
            0x11 -> { de = fetchWord(); 12 }
            0x12 -> { mmu.writeByte(de, a); 8 }
            0x13 -> { de = (de + 1) and 0xFFFF; 8 }
            0x14 -> { d = aluInc(d); 4 }
            0x15 -> { d = aluDec(d); 4 }
            0x16 -> { d = fetchByte(); 8 }
            0x17 -> { // RLA
                val oldC = if (flagC) 1 else 0
                val newC = (a shr 7) and 1
                a = ((a shl 1) or oldC) and 0xFF
                flagZ = false; flagN = false; flagH = false; flagC = newC != 0
                4
            }
            0x18 -> { // JR r8
                val offset = fetchSignedByte()
                pc = (pc + offset) and 0xFFFF
                12
            }
            0x19 -> { aluAddHl(de); 8 }
            0x1A -> { a = mmu.readByte(de); 8 }
            0x1B -> { de = (de - 1) and 0xFFFF; 8 }
            0x1C -> { e = aluInc(e); 4 }
            0x1D -> { e = aluDec(e); 4 }
            0x1E -> { e = fetchByte(); 8 }
            0x1F -> { // RRA
                val oldC = if (flagC) 1 else 0
                val newC = a and 1
                a = ((a shr 1) or (oldC shl 7)) and 0xFF
                flagZ = false; flagN = false; flagH = false; flagC = newC != 0
                4
            }
            0x20 -> { // JR NZ, r8
                val offset = fetchSignedByte()
                if (!flagZ) { pc = (pc + offset) and 0xFFFF; 12 } else 8
            }
            0x21 -> { hl = fetchWord(); 12 }
            0x22 -> { mmu.writeByte(hl, a); hl = (hl + 1) and 0xFFFF; 8 }
            0x23 -> { hl = (hl + 1) and 0xFFFF; 8 }
            0x24 -> { h = aluInc(h); 4 }
            0x25 -> { h = aluDec(h); 4 }
            0x26 -> { h = fetchByte(); 8 }
            0x27 -> { // DAA
                var corr = 0
                var setC = flagC
                if (flagH || (!flagN && (a and 0x0F) > 9)) corr = corr or 0x06
                if (flagC || (!flagN && a > 0x99)) { corr = corr or 0x60; setC = true }
                a = (if (flagN) (a - corr) else (a + corr)) and 0xFF
                flagZ = a == 0
                flagH = false
                flagC = setC
                4
            }
            0x28 -> { // JR Z, r8
                val offset = fetchSignedByte()
                if (flagZ) { pc = (pc + offset) and 0xFFFF; 12 } else 8
            }
            0x29 -> { aluAddHl(hl); 8 }
            0x2A -> { a = mmu.readByte(hl); hl = (hl + 1) and 0xFFFF; 8 }
            0x2B -> { hl = (hl - 1) and 0xFFFF; 8 }
            0x2C -> { l = aluInc(l); 4 }
            0x2D -> { l = aluDec(l); 4 }
            0x2E -> { l = fetchByte(); 8 }
            0x2F -> { // CPL
                a = a.inv() and 0xFF
                flagN = true; flagH = true
                4
            }
            0x30 -> { // JR NC, r8
                val offset = fetchSignedByte()
                if (!flagC) { pc = (pc + offset) and 0xFFFF; 12 } else 8
            }
            0x31 -> { sp = fetchWord(); 12 }
            0x32 -> { mmu.writeByte(hl, a); hl = (hl - 1) and 0xFFFF; 8 }
            0x33 -> { sp = (sp + 1) and 0xFFFF; 8 }
            0x34 -> { mmu.writeByte(hl, aluInc(mmu.readByte(hl))); 12 }
            0x35 -> { mmu.writeByte(hl, aluDec(mmu.readByte(hl))); 12 }
            0x36 -> { mmu.writeByte(hl, fetchByte()); 12 }
            0x37 -> { // SCF
                flagN = false; flagH = false; flagC = true
                4
            }
            0x38 -> { // JR C, r8
                val offset = fetchSignedByte()
                if (flagC) { pc = (pc + offset) and 0xFFFF; 12 } else 8
            }
            0x39 -> { aluAddHl(sp); 8 }
            0x3A -> { a = mmu.readByte(hl); hl = (hl - 1) and 0xFFFF; 8 }
            0x3B -> { sp = (sp - 1) and 0xFFFF; 8 }
            0x3C -> { a = aluInc(a); 4 }
            0x3D -> { a = aluDec(a); 4 }
            0x3E -> { a = fetchByte(); 8 }
            0x3F -> { // CCF
                flagN = false; flagH = false; flagC = !flagC
                4
            }
            0xC0 -> if (!flagZ) { pc = popWord(); 20 } else 8
            0xC1 -> { bc = popWord(); 12 }
            0xC2 -> { val addr = fetchWord(); if (!flagZ) { pc = addr; 16 } else 12 }
            0xC3 -> { pc = fetchWord(); 16 }
            0xC4 -> { val addr = fetchWord(); if (!flagZ) { pushWord(pc); pc = addr; 24 } else 12 }
            0xC5 -> { pushWord(bc); 16 }
            0xC6 -> { aluAdd(fetchByte(), false); 8 }
            0xC7 -> { pushWord(pc); pc = 0x0000; 16 }
            0xC8 -> if (flagZ) { pc = popWord(); 20 } else 8
            0xC9 -> { pc = popWord(); 16 }
            0xCA -> { val addr = fetchWord(); if (flagZ) { pc = addr; 16 } else 12 }
            0xCB -> executeCbOpcode(fetchByte())
            0xCC -> { val addr = fetchWord(); if (flagZ) { pushWord(pc); pc = addr; 24 } else 12 }
            0xCD -> { val addr = fetchWord(); pushWord(pc); pc = addr; 24 }
            0xCE -> { aluAdd(fetchByte(), true); 8 }
            0xCF -> { pushWord(pc); pc = 0x0008; 16 }
            0xD0 -> if (!flagC) { pc = popWord(); 20 } else 8
            0xD1 -> { de = popWord(); 12 }
            0xD2 -> { val addr = fetchWord(); if (!flagC) { pc = addr; 16 } else 12 }
            0xD4 -> { val addr = fetchWord(); if (!flagC) { pushWord(pc); pc = addr; 24 } else 12 }
            0xD5 -> { pushWord(de); 16 }
            0xD6 -> { aluSub(fetchByte(), false); 8 }
            0xD7 -> { pushWord(pc); pc = 0x0010; 16 }
            0xD8 -> if (flagC) { pc = popWord(); 20 } else 8
            0xD9 -> { pc = popWord(); ime = true; 16 } // RETI
            0xDA -> { val addr = fetchWord(); if (flagC) { pc = addr; 16 } else 12 }
            0xDC -> { val addr = fetchWord(); if (flagC) { pushWord(pc); pc = addr; 24 } else 12 }
            0xDE -> { aluSub(fetchByte(), true); 8 }
            0xDF -> { pushWord(pc); pc = 0x0018; 16 }
            0xE0 -> { mmu.writeByte(0xFF00 + fetchByte(), a); 12 }
            0xE1 -> { hl = popWord(); 12 }
            0xE2 -> { mmu.writeByte(0xFF00 + c, a); 8 }
            0xE5 -> { pushWord(hl); 16 }
            0xE6 -> { aluAnd(fetchByte()); 8 }
            0xE7 -> { pushWord(pc); pc = 0x0020; 16 }
            0xE8 -> { // ADD SP, r8
                val offset = fetchSignedByte()
                val res = (sp + offset) and 0xFFFF
                flagZ = false; flagN = false
                flagH = ((sp xor offset xor res) and 0x10) != 0
                flagC = ((sp xor offset xor res) and 0x100) != 0
                sp = res
                16
            }
            0xE9 -> { pc = hl; 4 }
            0xEA -> { mmu.writeByte(fetchWord(), a); 16 }
            0xEE -> { aluXor(fetchByte()); 8 }
            0xEF -> { pushWord(pc); pc = 0x0028; 16 }
            0xF0 -> { a = mmu.readByte(0xFF00 + fetchByte()); 12 }
            0xF1 -> { af = popWord(); 12 }
            0xF2 -> { a = mmu.readByte(0xFF00 + c); 8 }
            0xF3 -> { ime = false; imePending = false; 4 } // DI
            0xF5 -> { pushWord(af); 16 }
            0xF6 -> { aluOr(fetchByte()); 8 }
            0xF7 -> { pushWord(pc); pc = 0x0030; 16 }
            0xF8 -> { // LD HL, SP+r8
                val offset = fetchSignedByte()
                val res = (sp + offset) and 0xFFFF
                flagZ = false; flagN = false
                flagH = ((sp xor offset xor res) and 0x10) != 0
                flagC = ((sp xor offset xor res) and 0x100) != 0
                hl = res
                12
            }
            0xF9 -> { sp = hl; 8 }
            0xFA -> { a = mmu.readByte(fetchWord()); 16 }
            0xFB -> { imePending = true; 4 } // EI
            0xFE -> { aluCp(fetchByte()); 8 }
            0xFF -> { pushWord(pc); pc = 0x0038; 16 }
            else -> 4
        }
    }

    private fun executeCbOpcode(cb: Int): Int {
        val regIdx = cb and 7
        val v = getReg8(regIdx)
        val group = (cb shr 6) and 3
        val sub = (cb shr 3) and 7

        if (group == 1) {
            // BIT b, r
            flagZ = (v and (1 shl sub)) == 0
            flagN = false
            flagH = true
            return if (regIdx == 6) 12 else 8
        }
        if (group == 2) {
            // RES b, r
            setReg8(regIdx, v and (1 shl sub).inv())
            return if (regIdx == 6) 16 else 8
        }
        if (group == 3) {
            // SET b, r
            setReg8(regIdx, v or (1 shl sub))
            return if (regIdx == 6) 16 else 8
        }

        // group == 0: Rotates and shifts
        val res = when (sub) {
            0 -> { // RLC
                val cBit = (v shr 7) and 1
                val r = ((v shl 1) or cBit) and 0xFF
                flagZ = r == 0; flagN = false; flagH = false; flagC = cBit != 0
                r
            }
            1 -> { // RRC
                val cBit = v and 1
                val r = ((v shr 1) or (cBit shl 7)) and 0xFF
                flagZ = r == 0; flagN = false; flagH = false; flagC = cBit != 0
                r
            }
            2 -> { // RL
                val oldC = if (flagC) 1 else 0
                val newC = (v shr 7) and 1
                val r = ((v shl 1) or oldC) and 0xFF
                flagZ = r == 0; flagN = false; flagH = false; flagC = newC != 0
                r
            }
            3 -> { // RR
                val oldC = if (flagC) 1 else 0
                val newC = v and 1
                val r = ((v shr 1) or (oldC shl 7)) and 0xFF
                flagZ = r == 0; flagN = false; flagH = false; flagC = newC != 0
                r
            }
            4 -> { // SLA
                val newC = (v shr 7) and 1
                val r = (v shl 1) and 0xFF
                flagZ = r == 0; flagN = false; flagH = false; flagC = newC != 0
                r
            }
            5 -> { // SRA
                val newC = v and 1
                val r = ((v shr 1) or (v and 0x80)) and 0xFF
                flagZ = r == 0; flagN = false; flagH = false; flagC = newC != 0
                r
            }
            6 -> { // SWAP
                val r = (((v and 0x0F) shl 4) or ((v shr 4) and 0x0F)) and 0xFF
                flagZ = r == 0; flagN = false; flagH = false; flagC = false
                r
            }
            else -> { // SRL
                val newC = v and 1
                val r = (v shr 1) and 0xFF
                flagZ = r == 0; flagN = false; flagH = false; flagC = newC != 0
                r
            }
        }
        setReg8(regIdx, res)
        return if (regIdx == 6) 16 else 8
    }

    fun saveState(out: DataOutputStream) {
        out.writeInt(a)
        out.writeInt(f)
        out.writeInt(b)
        out.writeInt(c)
        out.writeInt(d)
        out.writeInt(e)
        out.writeInt(h)
        out.writeInt(l)
        out.writeInt(sp)
        out.writeInt(pc)
        out.writeBoolean(ime)
        out.writeBoolean(imePending)
        out.writeBoolean(halted)
        out.writeBoolean(haltBug)
        out.writeLong(totalCycles)
    }

    fun loadState(input: DataInputStream) {
        a = input.readInt()
        f = input.readInt()
        b = input.readInt()
        c = input.readInt()
        d = input.readInt()
        e = input.readInt()
        h = input.readInt()
        l = input.readInt()
        sp = input.readInt()
        pc = input.readInt()
        ime = input.readBoolean()
        imePending = input.readBoolean()
        halted = input.readBoolean()
        haltBug = input.readBoolean()
        totalCycles = input.readLong()
    }
}
