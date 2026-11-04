package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.emulator.Cartridge
import com.example.emulator.DmgPalettePreset
import com.example.emulator.GbcPpu
import com.example.emulator.VideoFilterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NeoGbcRepository(
    private val context: Context,
    private val dao: NeoGbcDao
) {
    val allRoms: Flow<List<RomEntity>> = dao.observeAllRoms()
    val allSaveStates: Flow<List<SaveStateEntity>> = dao.observeAllSaveStates()
    val scannedDirectories: Flow<List<ScannedDirectoryEntity>> = dao.observeDirectories()

    fun observeSaveStates(romId: Long): Flow<List<SaveStateEntity>> = dao.observeSaveStatesForRom(romId)
    fun observeCheats(romId: Long): Flow<List<CheatEntity>> = dao.observeCheatsForRom(romId)

    private val romsInternalDir = File(context.filesDir, "roms").apply { mkdirs() }
    private val savesInternalDir = File(context.filesDir, "saves").apply { mkdirs() }
    private val statesInternalDir = File(context.filesDir, "states").apply { mkdirs() }
    private val coversInternalDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val screenshotsInternalDir = File(context.filesDir, "screenshots").apply { mkdirs() }

    /**
     * Imports raw ROM or ZIP bytes with the given fileName directly into internal library storage.
     */
    suspend fun importRomBytes(fileName: String, rawBytes: ByteArray): RomEntity? = withContext(Dispatchers.IO) {
        try {
            val romBytes = Cartridge.extractRomIfZip(rawBytes)
            if (romBytes.size < 0x150) return@withContext null

            val header = Cartridge.parseHeader(romBytes)
            val title = CoverArtProvider.formatDisplayTitle(fileName, header.title)

            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val localFile = File(romsInternalDir, "${header.crc32Hex}_$safeName")
            localFile.writeBytes(romBytes)

            val existing = dao.getAllRomsOnce().firstOrNull {
                it.crc32Hex.equals(header.crc32Hex, ignoreCase = true) ||
                    it.fileName.equals(fileName, ignoreCase = true)
            }
            if (existing != null) {
                val repaired = if (!File(existing.uriOrAssetPath).exists()) {
                    existing.copy(uriOrAssetPath = localFile.absolutePath, isBundledAsset = false)
                        .also { dao.updateRom(it) }
                } else {
                    existing
                }
                reconcileSaveStatesForRom(repaired)
                return@withContext repaired
            }

            val autoCover = CoverArtProvider.buildAutomaticCoverUrl(fileName, header.title, header.isCgbSupported)
            val entity = RomEntity(
                title = title,
                headerTitle = header.title,
                fileName = fileName,
                uriOrAssetPath = localFile.absolutePath,
                isBundledAsset = false,
                isCgb = header.isCgbSupported,
                cartridgeType = header.cartridgeTypeName,
                romSizeBytes = header.romSizeBytes,
                ramSizeBytes = header.ramBytesSize,
                crc32Hex = header.crc32Hex,
                autoCoverUrl = autoCover
            )
            val newId = dao.insertRom(entity)
            val inserted = entity.copy(id = newId)
            reconcileSaveStatesForRom(inserted)
            inserted
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads raw ROM bytes for a RomEntity (from bundled assets, internal file cache, or SAF Uri).
     */
    suspend fun loadRomBytes(rom: RomEntity): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val raw = when {
                rom.isBundledAsset -> {
                    context.assets.open(rom.uriOrAssetPath).use { it.readBytes() }
                }
                rom.uriOrAssetPath.startsWith("/") -> {
                    File(rom.uriOrAssetPath).readBytes()
                }
                else -> {
                    val uri = Uri.parse(rom.uriOrAssetPath)
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
            } ?: return@withContext null
            Cartridge.extractRomIfZip(raw)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Imports a single ROM (.gb, .gbc, .zip) selected by the user via SAF OpenDocument.
     */
    suspend fun importSingleRomUri(uri: Uri): RomEntity? = withContext(Dispatchers.IO) {
        try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val romBytes = Cartridge.extractRomIfZip(rawBytes)
            if (romBytes.size < 0x150) return@withContext null

            val fileName = queryDisplayName(uri) ?: "game.gbc"
            val header = Cartridge.parseHeader(romBytes)
            val title = CoverArtProvider.formatDisplayTitle(fileName, header.title)

            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val localFile = File(romsInternalDir, "${header.crc32Hex}_$safeName")
            localFile.writeBytes(romBytes)

            val existing = dao.getAllRomsOnce().firstOrNull {
                it.crc32Hex.equals(header.crc32Hex, ignoreCase = true) ||
                    it.fileName.equals(fileName, ignoreCase = true)
            }
            if (existing != null) {
                val repaired = if (!File(existing.uriOrAssetPath).exists()) {
                    existing.copy(uriOrAssetPath = localFile.absolutePath, isBundledAsset = false)
                        .also { dao.updateRom(it) }
                } else {
                    existing
                }
                reconcileSaveStatesForRom(repaired)
                return@withContext repaired
            }

            val autoCover = CoverArtProvider.buildAutomaticCoverUrl(fileName, header.title, header.isCgbSupported)

            val entity = RomEntity(
                title = title,
                headerTitle = header.title,
                fileName = fileName,
                uriOrAssetPath = localFile.absolutePath,
                isBundledAsset = false,
                isCgb = header.isCgbSupported,
                cartridgeType = header.cartridgeTypeName,
                romSizeBytes = header.romSizeBytes,
                ramSizeBytes = header.ramBytesSize,
                crc32Hex = header.crc32Hex,
                autoCoverUrl = autoCover
            )
            val newId = dao.insertRom(entity)
            val inserted = entity.copy(id = newId)
            reconcileSaveStatesForRom(inserted)
            inserted
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Recursively scans a directory tree URI selected via OpenDocumentTree and imports all found ROMs.
     */
    suspend fun scanDirectoryTree(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val folderName = rootDocId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Pasta de Jogos" }
            val foundFiles = mutableListOf<Pair<Uri, String>>()

            collectRomDocuments(treeUri, rootDocId, foundFiles, depth = 0)

            for ((fileUri, name) in foundFiles) {
                val imported = importDocumentFromTree(fileUri, name)
                if (imported != null) {
                    importedCount++
                }
            }

            dao.insertDirectory(
                ScannedDirectoryEntity(
                    treeUri = treeUri.toString(),
                    displayName = folderName,
                    romCount = foundFiles.size,
                    lastScannedTimestamp = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {
        }
        importedCount
    }

    private fun collectRomDocuments(
        treeUri: Uri,
        parentDocId: String,
        results: MutableList<Pair<Uri, String>>,
        depth: Int
    ) {
        if (depth > 4) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idCol) ?: continue
                val name = cursor.getString(nameCol) ?: continue
                val mime = cursor.getString(mimeCol) ?: ""

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectRomDocuments(treeUri, docId, results, depth + 1)
                } else {
                    val lower = name.lowercase()
                    if (
                        lower.endsWith(".gbc") ||
                        lower.endsWith(".gb") ||
                        lower.endsWith(".cgb") ||
                        lower.endsWith(".zip") ||
                        lower.endsWith(".sgb") ||
                        lower.endsWith(".bin")
                    ) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        results.add(docUri to name)
                    }
                }
            }
        }
    }

    private suspend fun importDocumentFromTree(docUri: Uri, fileName: String): RomEntity? {
        return try {
            val rawBytes = context.contentResolver.openInputStream(docUri)?.use { it.readBytes() }
                ?: return null
            val romBytes = Cartridge.extractRomIfZip(rawBytes)
            if (romBytes.size < 0x150) return null

            val header = Cartridge.parseHeader(romBytes)
            val title = CoverArtProvider.formatDisplayTitle(fileName, header.title)

            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val localFile = File(romsInternalDir, "${header.crc32Hex}_$safeName")
            localFile.writeBytes(romBytes)

            val existing = dao.getAllRomsOnce().firstOrNull {
                it.crc32Hex.equals(header.crc32Hex, ignoreCase = true) ||
                    it.fileName.equals(fileName, ignoreCase = true)
            }
            if (existing != null) {
                val repaired = if (!File(existing.uriOrAssetPath).exists()) {
                    existing.copy(uriOrAssetPath = localFile.absolutePath, isBundledAsset = false)
                        .also { dao.updateRom(it) }
                } else {
                    existing
                }
                reconcileSaveStatesForRom(repaired)
                return repaired
            }

            val autoCover = CoverArtProvider.buildAutomaticCoverUrl(fileName, header.title, header.isCgbSupported)

            val entity = RomEntity(
                title = title,
                headerTitle = header.title,
                fileName = fileName,
                uriOrAssetPath = localFile.absolutePath,
                isBundledAsset = false,
                isCgb = header.isCgbSupported,
                cartridgeType = header.cartridgeTypeName,
                romSizeBytes = header.romSizeBytes,
                ramSizeBytes = header.ramBytesSize,
                crc32Hex = header.crc32Hex,
                autoCoverUrl = autoCover
            )
            val id = dao.insertRom(entity)
            val inserted = entity.copy(id = id)
            reconcileSaveStatesForRom(inserted)
            inserted
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renames a ROM both in the library database AND on the physical device file system
     * (renaming both the file inside any scanned SAF directory tree and the internal ROM storage file).
     */
    suspend fun renameRom(rom: RomEntity, newTitleInput: String): RomEntity? = withContext(Dispatchers.IO) {
        val trimmed = newTitleInput.trim()
        if (trimmed.isEmpty()) return@withContext null

        val originalExt = rom.fileName.substringAfterLast('.', "").lowercase().let { ext ->
            if (ext in listOf("gbc", "gb", "cgb", "zip", "sgb", "bin")) {
                ext
            } else if (rom.isCgb) {
                "gbc"
            } else {
                "gb"
            }
        }

        val cleanTitle = trimmed
            .removeSuffix(".$originalExt")
            .removeSuffix(".${originalExt.uppercase()}")
            .trim()
            .ifEmpty { trimmed }

        val sanitizedFileBase = cleanTitle
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { "Game" }
        val newFileName = "$sanitizedFileBase.$originalExt"

        // 1. Try renaming the external file inside any scanned SAF directory on the device
        try {
            val directories = dao.getDirectoriesOnce()
            for (dir in directories) {
                val treeUri = Uri.parse(dir.treeUri)
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val found = mutableListOf<Pair<Uri, String>>()
                collectRomDocuments(treeUri, rootDocId, found, depth = 0)
                val matchingDoc = found.firstOrNull { (_, docName) ->
                    docName.equals(rom.fileName, ignoreCase = true)
                }
                if (matchingDoc != null) {
                    try {
                        DocumentsContract.renameDocument(
                            context.contentResolver,
                            matchingDoc.first,
                            newFileName
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Rename (or materialize) the ROM file on internal device storage
        val safeStorageName = newFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetLocalFile = File(romsInternalDir, "${rom.crc32Hex}_$safeStorageName")
        var updatedUriOrPath = rom.uriOrAssetPath
        var updatedIsBundled = rom.isBundledAsset

        try {
            if (rom.isBundledAsset) {
                val romBytes = loadRomBytes(rom)
                if (romBytes != null) {
                    targetLocalFile.writeBytes(romBytes)
                    updatedUriOrPath = targetLocalFile.absolutePath
                    updatedIsBundled = false
                }
            } else if (rom.uriOrAssetPath.startsWith("/")) {
                val oldFile = File(rom.uriOrAssetPath)
                if (oldFile.exists()) {
                    if (oldFile.absolutePath != targetLocalFile.absolutePath) {
                        val renamedOk = oldFile.renameTo(targetLocalFile)
                        if (renamedOk) {
                            updatedUriOrPath = targetLocalFile.absolutePath
                        } else {
                            oldFile.copyTo(targetLocalFile, overwrite = true)
                            oldFile.delete()
                            updatedUriOrPath = targetLocalFile.absolutePath
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val updatedEntity = rom.copy(
            title = cleanTitle,
            fileName = newFileName,
            uriOrAssetPath = updatedUriOrPath,
            isBundledAsset = updatedIsBundled
        )
        dao.updateRom(updatedEntity)
        updatedEntity
    }

    /**
     * Copies a custom cover image chosen via Photo Picker into internal storage and links it to the ROM.
     */
    suspend fun setCustomCoverForRom(rom: RomEntity, imageUri: Uri): RomEntity = withContext(Dispatchers.IO) {
        val destFile = File(coversInternalDir, "cover_${rom.id}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        val updated = rom.copy(customCoverUri = destFile.absolutePath)
        dao.updateRom(updated)
        updated
    }

    suspend fun clearCustomCoverForRom(rom: RomEntity): RomEntity = withContext(Dispatchers.IO) {
        val autoUrl = CoverArtProvider.buildAutomaticCoverUrl(rom.fileName, rom.headerTitle, rom.isCgb)
        val updated = rom.copy(customCoverUri = null, autoCoverUrl = autoUrl)
        dao.updateRom(updated)
        updated
    }

    suspend fun toggleFavorite(rom: RomEntity) = withContext(Dispatchers.IO) {
        dao.updateRom(rom.copy(isFavorite = !rom.isFavorite))
    }

    suspend fun recordPlaySession(romId: Long, additionalSeconds: Long) = withContext(Dispatchers.IO) {
        val current = dao.getRomById(romId) ?: return@withContext
        dao.updateRom(
            current.copy(
                lastPlayedTimestamp = System.currentTimeMillis(),
                totalPlayTimeSeconds = current.totalPlayTimeSeconds + additionalSeconds
            )
        )
    }

    suspend fun deleteRom(rom: RomEntity) = withContext(Dispatchers.IO) {
        try {
            if (!rom.isBundledAsset && rom.uriOrAssetPath.startsWith("/")) {
                File(rom.uriOrAssetPath).delete()
            }
            rom.customCoverUri?.let { File(it).delete() }
            for (slot in 0..8) {
                File(statesInternalDir, "rom_${rom.id}_slot_${slot}.ngbc").delete()
                File(statesInternalDir, "rom_${rom.id}_slot_${slot}.png").delete()
                File(statesInternalDir, "crc_${rom.crc32Hex}_slot_${slot}.ngbc").delete()
                File(statesInternalDir, "crc_${rom.crc32Hex}_slot_${slot}.png").delete()
            }
        } catch (_: Exception) {
        }
        dao.deleteSaveStatesForRom(rom.id)
        dao.deleteRom(rom.id)
    }

    // Battery SRAM (.sav) persistence
    fun loadBatterySram(crc32Hex: String): ByteArray? {
        val file = File(savesInternalDir, "$crc32Hex.sav")
        return if (file.exists()) file.readBytes() else null
    }

    fun saveBatterySram(crc32Hex: String, data: ByteArray) {
        val file = File(savesInternalDir, "$crc32Hex.sav")
        file.writeBytes(data)
    }

    /**
     * Renders the 160x144 game framebuffer into a 640x576 (4x) Bitmap with the active
     * GB Palette (`DmgPalettePreset`) and Screen Filter (`VideoFilterMode`: Pixel Perfect,
     * LCD Grid, CRT Scanlines, or Soft Bilinear) baked directly into the image,
     * with zero emulator UI or virtual controls.
     */
    fun renderFilteredScreenshotBitmap(
        frameBuffer: IntArray,
        videoFilter: VideoFilterMode,
        dmgPalette: DmgPalettePreset,
        applyDmgPaletteFilter: Boolean
    ): Bitmap {
        val srcW = GbcPpu.SCREEN_WIDTH
        val srcH = GbcPpu.SCREEN_HEIGHT
        val processedPixels = IntArray(srcW * srcH)
        val colors = dmgPalette.colors

        for (idx in 0 until (srcW * srcH)) {
            val raw = if (idx < frameBuffer.size) frameBuffer[idx] else 0xFF07080C.toInt()
            processedPixels[idx] = if (applyDmgPaletteFilter) {
                if (raw == colors[0] || raw == colors[1] || raw == colors[2] || raw == colors[3]) {
                    raw
                } else {
                    val r = (raw shr 16) and 0xFF
                    val g = (raw shr 8) and 0xFF
                    val b = raw and 0xFF
                    val luma = (r * 77 + g * 150 + b * 29) shr 8
                    val shade = (3 - (luma shr 6)).coerceIn(0, 3)
                    colors[shade]
                }
            } else {
                raw
            }
        }

        val scale = 4
        val dstW = srcW * scale
        val dstH = srcH * scale
        val outPixels = IntArray(dstW * dstH)

        fun darkenPixel(argb: Int, keepRatioNum: Int, keepRatioDen: Int): Int {
            val r = (((argb shr 16) and 0xFF) * keepRatioNum) / keepRatioDen
            val g = (((argb shr 8) and 0xFF) * keepRatioNum) / keepRatioDen
            val b = ((argb and 0xFF) * keepRatioNum) / keepRatioDen
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        for (y in 0 until dstH) {
            val sy = y / scale
            val rowOffset = y * dstW
            for (x in 0 until dstW) {
                val sx = x / scale
                val baseColor = if (videoFilter == VideoFilterMode.SOFT_BILINEAR) {
                    val fx = (x % scale) / scale.toFloat()
                    val fy = (y % scale) / scale.toFloat()
                    val sx1 = (sx + 1).coerceAtMost(srcW - 1)
                    val sy1 = (sy + 1).coerceAtMost(srcH - 1)
                    val c00 = processedPixels[sy * srcW + sx]
                    val c10 = processedPixels[sy * srcW + sx1]
                    val c01 = processedPixels[sy1 * srcW + sx]
                    val c11 = processedPixels[sy1 * srcW + sx1]
                    val w00 = (1f - fx) * (1f - fy)
                    val w10 = fx * (1f - fy)
                    val w01 = (1f - fx) * fy
                    val w11 = fx * fy
                    val r = (
                        ((c00 shr 16) and 0xFF) * w00 +
                            ((c10 shr 16) and 0xFF) * w10 +
                            ((c01 shr 16) and 0xFF) * w01 +
                            ((c11 shr 16) and 0xFF) * w11
                        ).toInt().coerceIn(0, 255)
                    val g = (
                        ((c00 shr 8) and 0xFF) * w00 +
                            ((c10 shr 8) and 0xFF) * w10 +
                            ((c01 shr 8) and 0xFF) * w01 +
                            ((c11 shr 8) and 0xFF) * w11
                        ).toInt().coerceIn(0, 255)
                    val b = (
                        (c00 and 0xFF) * w00 +
                            (c10 and 0xFF) * w10 +
                            (c01 and 0xFF) * w01 +
                            (c11 and 0xFF) * w11
                        ).toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    processedPixels[sy * srcW + sx]
                }

                outPixels[rowOffset + x] = when (videoFilter) {
                    VideoFilterMode.LCD_GRID -> {
                        if ((x % scale == 0 && sx % 2 == 1) || (y % scale == 0 && sy % 2 == 1)) {
                            darkenPixel(baseColor, 78, 100)
                        } else {
                            baseColor
                        }
                    }
                    VideoFilterMode.CRT_SCANLINES -> {
                        if (y % scale == 0) {
                            darkenPixel(baseColor, 66, 100)
                        } else {
                            baseColor
                        }
                    }
                    else -> baseColor
                }
            }
        }

        return Bitmap.createBitmap(outPixels, dstW, dstH, Bitmap.Config.ARGB_8888)
    }

    /**
     * Captures a pure game-only screenshot from the 160x144 GBC/GB framebuffer (without any emulator UI or controls),
     * including the active Video Filter and GB Palette, upscaled 4x (640x576), and saves it automatically to the
     * device Gallery (`Pictures/NeoGBC` via `MediaStore.Images.Media`).
     */
    suspend fun saveScreenshotToGallery(
        romTitle: String,
        frameBuffer: IntArray,
        videoFilter: VideoFilterMode = VideoFilterMode.LCD_GRID,
        dmgPalette: DmgPalettePreset = DmgPalettePreset.CLASSIC_GREEN,
        applyDmgPaletteFilter: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        try {
            val upscaledBitmap = renderFilteredScreenshotBitmap(
                frameBuffer = frameBuffer,
                videoFilter = videoFilter,
                dmgPalette = dmgPalette,
                applyDmgPaletteFilter = applyDmgPaletteFilter
            )

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val safeTitle = romTitle
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .ifEmpty { "Game" }
            val displayName = "NeoGBC_${safeTitle}_$timestamp.png"

            // Always write to internal screenshots directory for instant verification and fallback
            val internalFile = File(screenshotsInternalDir, displayName)
            FileOutputStream(internalFile).use { out ->
                upscaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Export directly to Android Gallery (MediaStore.Images.Media -> Pictures/NeoGBC)
            try {
                val nowSec = System.currentTimeMillis() / 1000L
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.WIDTH, upscaledBitmap.width)
                    put(MediaStore.Images.Media.HEIGHT, upscaledBitmap.height)
                    put(MediaStore.Images.Media.DATE_ADDED, nowSec)
                    put(MediaStore.Images.Media.DATE_MODIFIED, nowSec)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NeoGBC")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val mediaUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (mediaUri != null) {
                    resolver.openOutputStream(mediaUri)?.use { out ->
                        upscaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val readyValues = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }
                        resolver.update(mediaUri, readyValues, null, null)
                    }
                }
            } catch (_: Exception) {
            }

            internalFile
        } catch (_: Exception) {
            null
        }
    }

    // Save States with PNG screenshot preview (dual-indexed by CRC32 and ROM ID for update resilience)
    suspend fun writeSaveState(
        romId: Long,
        slotIndex: Int,
        label: String,
        stateBytes: ByteArray,
        frameBuffer: IntArray,
        playTimeSeconds: Long
    ): SaveStateEntity = withContext(Dispatchers.IO) {
        val existing = dao.getSaveState(romId, slotIndex)
        val rom = dao.getRomById(romId)
        val baseKey = if (rom != null && rom.crc32Hex.isNotBlank()) {
            "crc_${rom.crc32Hex}_slot_${slotIndex}"
        } else {
            "rom_${romId}_slot_${slotIndex}"
        }

        val stateFile = File(statesInternalDir, "$baseKey.ngbc")
        stateFile.writeBytes(stateBytes)

        val previewFile = File(statesInternalDir, "$baseKey.png")
        try {
            val bmp = Bitmap.createBitmap(
                frameBuffer,
                GbcPpu.SCREEN_WIDTH,
                GbcPpu.SCREEN_HEIGHT,
                Bitmap.Config.ARGB_8888
            )
            FileOutputStream(previewFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Exception) {
        }

        // Remove legacy duplicate file if we wrote to the CRC-keyed file
        if (baseKey.startsWith("crc_")) {
            val legacyState = File(statesInternalDir, "rom_${romId}_slot_${slotIndex}.ngbc")
            val legacyPreview = File(statesInternalDir, "rom_${romId}_slot_${slotIndex}.png")
            if (legacyState.exists() && legacyState.absolutePath != stateFile.absolutePath) {
                legacyState.delete()
            }
            if (legacyPreview.exists() && legacyPreview.absolutePath != previewFile.absolutePath) {
                legacyPreview.delete()
            }
        }

        val entity = SaveStateEntity(
            id = existing?.id ?: 0L,
            romId = romId,
            slotIndex = slotIndex,
            label = label,
            stateFilePath = stateFile.absolutePath,
            previewImagePath = previewFile.absolutePath,
            playTimeSeconds = playTimeSeconds,
            timestamp = System.currentTimeMillis()
        )
        val id = dao.insertSaveState(entity)
        entity.copy(id = id)
    }

    private suspend fun resolveSaveStateFile(saveState: SaveStateEntity): File? {
        val primary = File(saveState.stateFilePath)
        if (primary.exists() && primary.length() > 0L) return primary

        val rom = dao.getRomById(saveState.romId)
        if (rom != null && rom.crc32Hex.isNotBlank()) {
            val crcFile = File(statesInternalDir, "crc_${rom.crc32Hex}_slot_${saveState.slotIndex}.ngbc")
            if (crcFile.exists() && crcFile.length() > 0L) return crcFile
        }

        val legacyFile = File(statesInternalDir, "rom_${saveState.romId}_slot_${saveState.slotIndex}.ngbc")
        if (legacyFile.exists() && legacyFile.length() > 0L) return legacyFile

        return null
    }

    suspend fun getLatestSaveState(romId: Long): SaveStateEntity? = withContext(Dispatchers.IO) {
        val rom = dao.getRomById(romId)
        if (rom != null) {
            reconcileSaveStatesForRom(rom)
        }
        val latest = dao.getLatestSaveStateForRom(romId) ?: return@withContext null
        val resolvedFile = resolveSaveStateFile(latest) ?: return@withContext null
        if (resolvedFile.absolutePath != latest.stateFilePath) {
            val updated = latest.copy(stateFilePath = resolvedFile.absolutePath)
            dao.insertSaveState(updated)
            updated
        } else {
            latest
        }
    }

    suspend fun touchSaveStateTimestamp(saveState: SaveStateEntity) = withContext(Dispatchers.IO) {
        dao.insertSaveState(saveState.copy(timestamp = System.currentTimeMillis()))
    }

    suspend fun readSaveStateBytes(saveState: SaveStateEntity): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolveSaveStateFile(saveState)
        if (file != null && file.exists()) file.readBytes() else null
    }

    suspend fun deleteSaveState(saveState: SaveStateEntity) = withContext(Dispatchers.IO) {
        File(saveState.stateFilePath).delete()
        saveState.previewImagePath?.let { File(it).delete() }
        val rom = dao.getRomById(saveState.romId)
        if (rom != null) {
            File(statesInternalDir, "crc_${rom.crc32Hex}_slot_${saveState.slotIndex}.ngbc").delete()
            File(statesInternalDir, "crc_${rom.crc32Hex}_slot_${saveState.slotIndex}.png").delete()
        }
        File(statesInternalDir, "rom_${saveState.romId}_slot_${saveState.slotIndex}.ngbc").delete()
        File(statesInternalDir, "rom_${saveState.romId}_slot_${saveState.slotIndex}.png").delete()
        dao.deleteSaveState(saveState.id)
    }

    /**
     * Reconciles all persisted ROM files and Save State files in internal storage with the Room database.
     * Guarantees that app updates, database migrations, or ROM re-imports never orphan or lose any user save.
     */
    suspend fun reconcilePersistedData() = withContext(Dispatchers.IO) {
        try {
            // 1. Recover any internal ROM files on disk not currently indexed in Room
            val internalRomFiles = romsInternalDir.listFiles()?.filter { it.isFile && it.length() >= 0x150 } ?: emptyList()
            val currentRoms = dao.getAllRomsOnce().toMutableList()

            for (romFile in internalRomFiles) {
                try {
                    val rawBytes = romFile.readBytes()
                    val romBytes = Cartridge.extractRomIfZip(rawBytes)
                    if (romBytes.size < 0x150) continue
                    val header = Cartridge.parseHeader(romBytes)
                    val matchingRom = currentRoms.firstOrNull {
                        it.crc32Hex.equals(header.crc32Hex, ignoreCase = true)
                    }
                    if (matchingRom == null) {
                        val rawFileName = romFile.name.substringAfter('_', romFile.name)
                        val title = CoverArtProvider.formatDisplayTitle(rawFileName, header.title)
                        val autoCover = CoverArtProvider.buildAutomaticCoverUrl(rawFileName, header.title, header.isCgbSupported)
                        val entity = RomEntity(
                            title = title,
                            headerTitle = header.title,
                            fileName = rawFileName,
                            uriOrAssetPath = romFile.absolutePath,
                            isBundledAsset = false,
                            isCgb = header.isCgbSupported,
                            cartridgeType = header.cartridgeTypeName,
                            romSizeBytes = header.romSizeBytes,
                            ramSizeBytes = header.ramBytesSize,
                            crc32Hex = header.crc32Hex,
                            autoCoverUrl = autoCover,
                            addedTimestamp = romFile.lastModified()
                        )
                        val id = dao.insertRom(entity)
                        currentRoms.add(entity.copy(id = id))
                    } else if (!matchingRom.isBundledAsset && !File(matchingRom.uriOrAssetPath).exists()) {
                        val repaired = matchingRom.copy(uriOrAssetPath = romFile.absolutePath)
                        dao.updateRom(repaired)
                    }
                } catch (_: Exception) {
                }
            }

            // 2. Reconcile Save States for every ROM in the library
            val allRomsNow = dao.getAllRomsOnce()
            for (rom in allRomsNow) {
                reconcileSaveStatesForRom(rom, allRomsNow.size == 1)
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun reconcileSaveStatesForRom(rom: RomEntity, isOnlyRomInLibrary: Boolean = false) {
        try {
            for (slot in 0..8) {
                val crcStateFile = File(statesInternalDir, "crc_${rom.crc32Hex}_slot_${slot}.ngbc")
                val crcPreviewFile = File(statesInternalDir, "crc_${rom.crc32Hex}_slot_${slot}.png")
                val legacyStateFile = File(statesInternalDir, "rom_${rom.id}_slot_${slot}.ngbc")
                val legacyPreviewFile = File(statesInternalDir, "rom_${rom.id}_slot_${slot}.png")

                // If legacy file exists (from Versão 01..11), migrate/copy to CRC-indexed file so it is forever bound to the ROM's CRC32
                if (legacyStateFile.exists() && legacyStateFile.length() > 0L && !crcStateFile.exists()) {
                    try {
                        legacyStateFile.copyTo(crcStateFile, overwrite = false)
                        if (legacyPreviewFile.exists() && !crcPreviewFile.exists()) {
                            legacyPreviewFile.copyTo(crcPreviewFile, overwrite = false)
                        }
                    } catch (_: Exception) {
                    }
                }

                // Also check if there is an orphaned legacy rom_<anyId>_slot_<slot>.ngbc when this is the only ROM in the library
                val singleRomOrphanState = if (!crcStateFile.exists() && !legacyStateFile.exists() && isOnlyRomInLibrary) {
                    statesInternalDir.listFiles()?.firstOrNull {
                        it.isFile && it.name.matches(Regex("rom_\\d+_slot_${slot}\\.ngbc")) && it.length() > 0L
                    }
                } else {
                    null
                }
                if (singleRomOrphanState != null && !crcStateFile.exists()) {
                    try {
                        singleRomOrphanState.copyTo(crcStateFile, overwrite = false)
                        val orphanPreview = File(statesInternalDir, singleRomOrphanState.nameWithoutExtension + ".png")
                        if (orphanPreview.exists() && !crcPreviewFile.exists()) {
                            orphanPreview.copyTo(crcPreviewFile, overwrite = false)
                        }
                    } catch (_: Exception) {
                    }
                }

                val activeStateFile = when {
                    crcStateFile.exists() && crcStateFile.length() > 0L -> crcStateFile
                    legacyStateFile.exists() && legacyStateFile.length() > 0L -> legacyStateFile
                    else -> null
                } ?: continue

                val activePreviewFile = when {
                    crcPreviewFile.exists() -> crcPreviewFile
                    legacyPreviewFile.exists() -> legacyPreviewFile
                    else -> null
                }

                val existingEntity = dao.getSaveState(rom.id, slot)
                if (existingEntity == null) {
                    val defaultLabel = if (slot == 0) "Quick Save" else "Slot $slot"
                    dao.insertSaveState(
                        SaveStateEntity(
                            romId = rom.id,
                            slotIndex = slot,
                            label = defaultLabel,
                            stateFilePath = activeStateFile.absolutePath,
                            previewImagePath = activePreviewFile?.absolutePath,
                            playTimeSeconds = rom.totalPlayTimeSeconds,
                            timestamp = activeStateFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                        )
                    )
                } else if (!File(existingEntity.stateFilePath).exists()) {
                    dao.insertSaveState(
                        existingEntity.copy(
                            stateFilePath = activeStateFile.absolutePath,
                            previewImagePath = activePreviewFile?.absolutePath ?: existingEntity.previewImagePath
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    // Cheats management
    suspend fun addCheat(romId: Long, title: String, code: String) = withContext(Dispatchers.IO) {
        dao.insertCheat(
            CheatEntity(
                romId = romId,
                title = title.trim(),
                code = code.uppercase().trim(),
                isEnabled = true
            )
        )
    }

    suspend fun toggleCheat(cheat: CheatEntity) = withContext(Dispatchers.IO) {
        dao.updateCheat(cheat.copy(isEnabled = !cheat.isEnabled))
    }

    suspend fun deleteCheat(cheat: CheatEntity) = withContext(Dispatchers.IO) {
        dao.deleteCheat(cheat.id)
    }

    // Control Layout Profiles
    suspend fun getOrCreateControlLayout(isLandscape: Boolean): ControlLayoutEntity = withContext(Dispatchers.IO) {
        val profileId = if (isLandscape) "LANDSCAPE_V5" else "PORTRAIT_V5"
        val existing = dao.getControlLayout(profileId)
        if (existing != null) return@withContext existing

        val defaultLayout = defaultControlLayout(isLandscape)
        dao.saveControlLayout(defaultLayout)
        defaultLayout
    }

    suspend fun saveControlLayout(layout: ControlLayoutEntity) = withContext(Dispatchers.IO) {
        dao.saveControlLayout(layout)
    }

    suspend fun resetControlLayout(isLandscape: Boolean): ControlLayoutEntity = withContext(Dispatchers.IO) {
        val def = defaultControlLayout(isLandscape)
        dao.saveControlLayout(def)
        def
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun defaultControlLayout(isLandscape: Boolean): ControlLayoutEntity {
            return if (isLandscape) {
                ControlLayoutEntity(
                    profileId = "LANDSCAPE_V5",
                    dpadX = 0.12f,
                    dpadY = 0.52f,
                    dpadScale = 1.0f,
                    dpadOpacity = 0.88f,
                    abX = 0.88f,
                    abY = 0.62f,
                    abScale = 1.0f,
                    abOpacity = 0.88f,
                    startSelectX = 0.12f,
                    startSelectY = 0.88f,
                    startSelectScale = 0.92f,
                    startSelectOpacity = 0.88f,
                    turboX = 0.88f,
                    turboY = 0.10f,
                    turboScale = 0.95f,
                    turboOpacity = 0.92f,
                    hapticEnabled = true,
                    hapticIntensity = 0.7f,
                    dpadDeadzone = 0.16f
                )
            } else {
                ControlLayoutEntity(
                    profileId = "PORTRAIT_V5",
                    dpadX = 0.24f,
                    dpadY = 0.72f,
                    dpadScale = 1.0f,
                    dpadOpacity = 0.90f,
                    abX = 0.78f,
                    abY = 0.72f,
                    abScale = 1.0f,
                    abOpacity = 0.90f,
                    startSelectX = 0.50f,
                    startSelectY = 0.92f,
                    startSelectScale = 1.0f,
                    startSelectOpacity = 0.90f,
                    turboX = 0.72f,
                    turboY = 0.54f,
                    turboScale = 0.95f,
                    turboOpacity = 0.92f,
                    hapticEnabled = true,
                    hapticIntensity = 0.7f,
                    dpadDeadzone = 0.16f
                )
            }
        }
    }
}
