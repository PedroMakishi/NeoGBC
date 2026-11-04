package com.example.emulator

import java.io.DataInputStream
import java.io.DataOutputStream

enum class DmgPalettePreset(
    val displayName: String,
    val colors: IntArray
) {
    CLASSIC_GREEN("DMG Verde Clássico", intArrayOf(0xFFE0F8D0.toInt(), 0xFF88C070.toInt(), 0xFF346856.toInt(), 0xFF081820.toInt())),
    POCKET_SILVER("GB Pocket Prata", intArrayOf(0xFFF0F4F0.toInt(), 0xFFB0B8B0.toInt(), 0xFF687068.toInt(), 0xFF182018.toInt())),
    CYBER_NEON("Cyber OLED Neon", intArrayOf(0xFF00F5D4.toInt(), 0xFF00BBF9.toInt(), 0xFF9B5DE5.toInt(), 0xFF07080C.toInt())),
    BERRY_SUNSET("GBC Berry Sunset", intArrayOf(0xFFFFF1E6.toInt(), 0xFFF15BB5.toInt(), 0xFF7209B7.toInt(), 0xFF10002B.toInt())),
    AMBER_CRT("Fósforo Âmbar", intArrayOf(0xFFFFE599.toInt(), 0xFFFFB703.toInt(), 0xFFFB8500.toInt(), 0xFF1A0F00.toInt()))
}

class GbcPpu(
    private val isCgbMode: Boolean
) {
    companion object {
        const val SCREEN_WIDTH = 160
        const val SCREEN_HEIGHT = 144
    }

    // 2 banks of 8KB VRAM (Bank 0 = Tile Data + Tile Maps, Bank 1 = CGB Tile Data + Tile Attributes)
    val vram = ByteArray(0x4000)
    var vramBank: Int = 0

    // 160 bytes Object Attribute Memory (0xFE00..0xFE9F)
    val oam = ByteArray(0xA0)

    // Framebuffers (double-buffered so UI thread always reads a completed frame)
    val workingBuffer = IntArray(SCREEN_WIDTH * SCREEN_HEIGHT) { 0xFF07080C.toInt() }
    val frontBuffer = IntArray(SCREEN_WIDTH * SCREEN_HEIGHT) { 0xFF07080C.toInt() }
    var frameCompleted: Boolean = false

    // LCD Control & Status Registers
    var lcdc: Int = 0x91
    var stat: Int = 0x85
    var scy: Int = 0
    var scx: Int = 0
    var ly: Int = 0
    var lyc: Int = 0
    var bgp: Int = 0xFC
    var obp0: Int = 0xFF
    var obp1: Int = 0xFF
    var wy: Int = 0
    var wx: Int = 0

    // CGB Palette RAM (64 bytes BG, 64 bytes OBJ)
    val bgPaletteRam = ByteArray(64) { 0xFF.toByte() }
    val objPaletteRam = ByteArray(64) { 0xFF.toByte() }
    var bcps: Int = 0
    var ocps: Int = 0
    var opri: Int = 0

    var dmgPalettePreset: DmgPalettePreset = DmgPalettePreset.CLASSIC_GREEN
    var forceDmgPaletteOnCgb: Boolean = false

    private var dotCounter: Int = 0
    private var mode: Int = 2 // 0=HBlank, 1=VBlank, 2=OAM, 3=Transfer
    private var windowLineCounter: Int = 0

    // Line priority buffer for Sprite vs BG priority
    private val bgColorIndices = IntArray(SCREEN_WIDTH)
    private val bgHasPriority = BooleanArray(SCREEN_WIDTH)

    // Pre-computed CGB 32-bit ARGB colors (8 palettes x 4 colors)
    private val cgbBgColors = IntArray(32) { 0xFFFFFFFF.toInt() }
    private val cgbObjColors = IntArray(32) { 0xFFFFFFFF.toInt() }

    var requestVBlankInterrupt: Boolean = false
    var requestStatInterrupt: Boolean = false
    var enteredHBlankThisStep: Boolean = false

    init {
        rebuildAllCgbPalettes()
    }

    fun reset() {
        vram.fill(0)
        oam.fill(0)
        vramBank = 0
        lcdc = 0x91
        stat = 0x85
        scy = 0
        scx = 0
        ly = 0
        lyc = 0
        bgp = 0xFC
        obp0 = 0xFF
        obp1 = 0xFF
        wy = 0
        wx = 0
        bcps = 0
        ocps = 0
        dotCounter = 0
        mode = 2
        windowLineCounter = 0
        bgPaletteRam.fill(0xFF.toByte())
        objPaletteRam.fill(0xFF.toByte())
        rebuildAllCgbPalettes()
    }

    fun readVram(address: Int): Int {
        val offset = (vramBank * 0x2000) + ((address - 0x8000) and 0x1FFF)
        return vram[offset].toInt() and 0xFF
    }

    fun writeVram(address: Int, value: Int) {
        val offset = (vramBank * 0x2000) + ((address - 0x8000) and 0x1FFF)
        vram[offset] = (value and 0xFF).toByte()
    }

    fun readOam(address: Int): Int {
        val idx = address - 0xFE00
        return if (idx in 0 until 0xA0) oam[idx].toInt() and 0xFF else 0xFF
    }

    fun writeOam(address: Int, value: Int) {
        val idx = address - 0xFE00
        if (idx in 0 until 0xA0) {
            oam[idx] = (value and 0xFF).toByte()
        }
    }

    fun readBcpd(): Int {
        val index = bcps and 0x3F
        return bgPaletteRam[index].toInt() and 0xFF
    }

    fun writeBcpd(value: Int) {
        val index = bcps and 0x3F
        bgPaletteRam[index] = (value and 0xFF).toByte()
        updateCgbColor(isBg = true, byteIndex = index)
        if ((bcps and 0x80) != 0) {
            bcps = (bcps and 0x80) or ((index + 1) and 0x3F)
        }
    }

    fun readOcpd(): Int {
        val index = ocps and 0x3F
        return objPaletteRam[index].toInt() and 0xFF
    }

    fun writeOcpd(value: Int) {
        val index = ocps and 0x3F
        objPaletteRam[index] = (value and 0xFF).toByte()
        updateCgbColor(isBg = false, byteIndex = index)
        if ((ocps and 0x80) != 0) {
            ocps = (ocps and 0x80) or ((index + 1) and 0x3F)
        }
    }

    private fun updateCgbColor(isBg: Boolean, byteIndex: Int) {
        val aligned = byteIndex and 0x3E
        val ram = if (isBg) bgPaletteRam else objPaletteRam
        val lo = ram[aligned].toInt() and 0xFF
        val hi = ram[aligned + 1].toInt() and 0xFF
        val rgb555 = lo or (hi shl 8)
        val r5 = rgb555 and 0x1F
        val g5 = (rgb555 shr 5) and 0x1F
        val b5 = (rgb555 shr 10) and 0x1F
        val r8 = (r5 shl 3) or (r5 shr 2)
        val g8 = (g5 shl 3) or (g5 shr 2)
        val b8 = (b5 shl 3) or (b5 shr 2)
        val argb = (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
        if (isBg) {
            cgbBgColors[aligned shr 1] = argb
        } else {
            cgbObjColors[aligned shr 1] = argb
        }
    }

    private fun rebuildAllCgbPalettes() {
        for (i in 0 until 64 step 2) {
            updateCgbColor(true, i)
            updateCgbColor(false, i)
        }
    }

    private fun mapArgbToDmgPalette(argb: Int): Int {
        val colors = dmgPalettePreset.colors
        if (argb == colors[0] || argb == colors[1] || argb == colors[2] || argb == colors[3]) {
            return argb
        }
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val luma = (r * 77 + g * 150 + b * 29) shr 8
        val shade = (3 - (luma shr 6)).coerceIn(0, 3)
        return colors[shade]
    }

    /**
     * Re-renders all 144 scanlines from current VRAM/OAM state into `frontBuffer` immediately,
     * so changing the GB palette updates both the live screen and screenshots even while paused.
     */
    fun reRenderCurrentFrame() {
        val savedWindowLine = windowLineCounter
        windowLineCounter = 0
        for (line in 0 until SCREEN_HEIGHT) {
            renderScanline(line)
        }
        windowLineCounter = savedWindowLine
        System.arraycopy(workingBuffer, 0, frontBuffer, 0, workingBuffer.size)
    }

    fun writeLcdc(value: Int) {
        val wasEnabled = (lcdc and 0x80) != 0
        lcdc = value and 0xFF
        val isEnabled = (lcdc and 0x80) != 0
        if (wasEnabled && !isEnabled) {
            ly = 0
            dotCounter = 0
            mode = 0
            stat = (stat and 0xFC)
        } else if (!wasEnabled && isEnabled) {
            dotCounter = 0
            mode = 2
            updateLycComparison()
        }
    }

    fun step(tCycles: Int) {
        enteredHBlankThisStep = false
        if ((lcdc and 0x80) == 0) {
            return
        }

        dotCounter += tCycles
        when (mode) {
            2 -> { // OAM Search (80 dots)
                if (dotCounter >= 80) {
                    dotCounter -= 80
                    setMode(3)
                }
            }
            3 -> { // Pixel Transfer (172 dots)
                if (dotCounter >= 172) {
                    dotCounter -= 172
                    renderScanline(ly)
                    enteredHBlankThisStep = true
                    setMode(0)
                }
            }
            0 -> { // H-Blank (204 dots)
                if (dotCounter >= 204) {
                    dotCounter -= 204
                    ly++
                    updateLycComparison()
                    if (ly == 144) {
                        setMode(1)
                        requestVBlankInterrupt = true
                        System.arraycopy(workingBuffer, 0, frontBuffer, 0, workingBuffer.size)
                        frameCompleted = true
                    } else {
                        setMode(2)
                    }
                }
            }
            1 -> { // V-Blank (10 lines * 456 dots)
                if (dotCounter >= 456) {
                    dotCounter -= 456
                    ly++
                    if (ly > 153) {
                        ly = 0
                        windowLineCounter = 0
                        updateLycComparison()
                        setMode(2)
                    } else {
                        updateLycComparison()
                    }
                }
            }
        }
    }

    private fun setMode(newMode: Int) {
        mode = newMode and 0x03
        stat = (stat and 0xFC) or mode
        val statIrq = when (mode) {
            0 -> (stat and 0x08) != 0
            1 -> (stat and 0x10) != 0
            2 -> (stat and 0x20) != 0
            else -> false
        }
        if (statIrq) {
            requestStatInterrupt = true
        }
    }

    fun updateLycComparison() {
        if (ly == lyc) {
            stat = stat or 0x04
            if ((stat and 0x40) != 0) {
                requestStatInterrupt = true
            }
        } else {
            stat = stat and 0xFB
        }
    }

    private fun renderScanline(line: Int) {
        if (line !in 0 until SCREEN_HEIGHT) return
        val rowOffset = line * SCREEN_WIDTH

        bgColorIndices.fill(0)
        bgHasPriority.fill(false)

        val bgAndWindowEnabled = isCgbMode || ((lcdc and 0x01) != 0)
        val windowEnabled = ((lcdc and 0x20) != 0) && (wy <= line) && (wx in 0..166)
        val unsignedAddressing = (lcdc and 0x10) != 0
        val bgTileMapBase = if ((lcdc and 0x08) != 0) 0x1C00 else 0x1800
        val winTileMapBase = if ((lcdc and 0x40) != 0) 0x1C00 else 0x1800

        var windowRenderedOnLine = false

        if (bgAndWindowEnabled) {
            for (x in 0 until SCREEN_WIDTH) {
                val usingWindow = windowEnabled && (x >= (wx - 7))
                if (usingWindow) windowRenderedOnLine = true

                val mapX = if (usingWindow) (x - (wx - 7)) and 0xFF else (x + scx) and 0xFF
                val mapY = if (usingWindow) windowLineCounter and 0xFF else (line + scy) and 0xFF

                val tileCol = mapX shr 3
                val tileRow = mapY shr 3
                val mapOffset = (if (usingWindow) winTileMapBase else bgTileMapBase) + (tileRow * 32) + tileCol

                val tileIndex = vram[mapOffset].toInt() and 0xFF
                val attr = if (isCgbMode) vram[0x2000 + mapOffset].toInt() and 0xFF else 0

                val cgbPaletteIdx = attr and 0x07
                val tileVramBank = if (isCgbMode && (attr and 0x08) != 0) 1 else 0
                val flipX = isCgbMode && (attr and 0x20) != 0
                val flipY = isCgbMode && (attr and 0x40) != 0
                val bgOamPriority = isCgbMode && (attr and 0x80) != 0

                val tileDataAddr = if (unsignedAddressing) {
                    tileIndex * 16
                } else {
                    0x1000 + (tileIndex.toByte().toInt() * 16)
                }

                val pixelY = if (flipY) 7 - (mapY and 7) else (mapY and 7)
                val pixelX = if (flipX) 7 - (mapX and 7) else (mapX and 7)

                val bankOffset = (tileVramBank * 0x2000) + tileDataAddr + (pixelY * 2)
                val lo = vram[bankOffset].toInt() and 0xFF
                val hi = vram[bankOffset + 1].toInt() and 0xFF
                val bit = 7 - pixelX
                val colorNum = ((hi shr bit) and 1 shl 1) or ((lo shr bit) and 1)

                bgColorIndices[x] = colorNum
                bgHasPriority[x] = bgOamPriority

                workingBuffer[rowOffset + x] = if (isCgbMode) {
                    val rawCgb = cgbBgColors[(cgbPaletteIdx * 4) + colorNum]
                    if (forceDmgPaletteOnCgb) mapArgbToDmgPalette(rawCgb) else rawCgb
                } else {
                    val shade = (bgp shr (colorNum * 2)) and 0x03
                    dmgPalettePreset.colors[shade]
                }
            }
        } else {
            val blankColor = if (isCgbMode) {
                val rawCgb = cgbBgColors[0]
                if (forceDmgPaletteOnCgb) mapArgbToDmgPalette(rawCgb) else rawCgb
            } else {
                dmgPalettePreset.colors[0]
            }
            for (x in 0 until SCREEN_WIDTH) {
                workingBuffer[rowOffset + x] = blankColor
            }
        }

        if (windowRenderedOnLine) {
            windowLineCounter++
        }

        // Render Sprites (Objects)
        if ((lcdc and 0x02) != 0) {
            val spriteHeight = if ((lcdc and 0x04) != 0) 16 else 8
            var spritesOnLine = 0
            val activeSprites = IntArray(10)

            for (i in 0 until 40) {
                val oamOffset = i * 4
                val spriteY = (oam[oamOffset].toInt() and 0xFF) - 16
                if (line >= spriteY && line < spriteY + spriteHeight) {
                    activeSprites[spritesOnLine++] = i
                    if (spritesOnLine == 10) break
                }
            }

            // Draw back-to-front so lower OAM index (or lower X in DMG) wins on top
            for (s in (spritesOnLine - 1) downTo 0) {
                val spriteIdx = activeSprites[s]
                val oamOffset = spriteIdx * 4
                val spriteY = (oam[oamOffset].toInt() and 0xFF) - 16
                val spriteX = (oam[oamOffset + 1].toInt() and 0xFF) - 8
                var tileIndex = oam[oamOffset + 2].toInt() and 0xFF
                val flags = oam[oamOffset + 3].toInt() and 0xFF

                if (spriteHeight == 16) {
                    tileIndex = tileIndex and 0xFE
                }

                val flipY = (flags and 0x40) != 0
                val flipX = (flags and 0x20) != 0
                val objBehindBg = (flags and 0x80) != 0
                val dmgPalette = if ((flags and 0x10) != 0) obp1 else obp0
                val cgbPaletteIdx = flags and 0x07
                val tileVramBank = if (isCgbMode && (flags and 0x08) != 0) 1 else 0

                val lineInSprite = if (flipY) (spriteHeight - 1 - (line - spriteY)) else (line - spriteY)
                val bankOffset = (tileVramBank * 0x2000) + (tileIndex * 16) + (lineInSprite * 2)
                if (bankOffset + 1 !in vram.indices) continue

                val lo = vram[bankOffset].toInt() and 0xFF
                val hi = vram[bankOffset + 1].toInt() and 0xFF

                for (px in 0..7) {
                    val screenX = spriteX + px
                    if (screenX !in 0 until SCREEN_WIDTH) continue

                    val bit = if (flipX) px else (7 - px)
                    val colorNum = ((hi shr bit) and 1 shl 1) or ((lo shr bit) and 1)
                    if (colorNum == 0) continue // Color 0 is transparent for sprites

                    val masterBgPriority = !isCgbMode || ((lcdc and 0x01) != 0)
                    val hiddenByBg = masterBgPriority &&
                        (bgColorIndices[screenX] != 0) &&
                        (objBehindBg || bgHasPriority[screenX])

                    if (!hiddenByBg) {
                        workingBuffer[rowOffset + screenX] = if (isCgbMode) {
                            val rawCgb = cgbObjColors[(cgbPaletteIdx * 4) + colorNum]
                            if (forceDmgPaletteOnCgb) mapArgbToDmgPalette(rawCgb) else rawCgb
                        } else {
                            val shade = (dmgPalette shr (colorNum * 2)) and 0x03
                            dmgPalettePreset.colors[shade]
                        }
                    }
                }
            }
        }
    }

    fun saveState(out: DataOutputStream) {
        out.writeInt(vramBank)
        out.writeInt(lcdc)
        out.writeInt(stat)
        out.writeInt(scy)
        out.writeInt(scx)
        out.writeInt(ly)
        out.writeInt(lyc)
        out.writeInt(bgp)
        out.writeInt(obp0)
        out.writeInt(obp1)
        out.writeInt(wy)
        out.writeInt(wx)
        out.writeInt(bcps)
        out.writeInt(ocps)
        out.writeInt(dotCounter)
        out.writeInt(mode)
        out.writeInt(windowLineCounter)
        out.write(vram)
        out.write(oam)
        out.write(bgPaletteRam)
        out.write(objPaletteRam)
    }

    fun loadState(input: DataInputStream) {
        vramBank = input.readInt()
        lcdc = input.readInt()
        stat = input.readInt()
        scy = input.readInt()
        scx = input.readInt()
        ly = input.readInt()
        lyc = input.readInt()
        bgp = input.readInt()
        obp0 = input.readInt()
        obp1 = input.readInt()
        wy = input.readInt()
        wx = input.readInt()
        bcps = input.readInt()
        ocps = input.readInt()
        dotCounter = input.readInt()
        mode = input.readInt()
        windowLineCounter = input.readInt()
        input.readFully(vram)
        input.readFully(oam)
        input.readFully(bgPaletteRam)
        input.readFully(objPaletteRam)
        rebuildAllCgbPalettes()
    }
}
