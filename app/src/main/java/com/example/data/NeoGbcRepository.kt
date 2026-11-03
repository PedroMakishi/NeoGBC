package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.emulator.Cartridge
import com.example.emulator.GbcPpu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class NeoGbcRepository(
    private val context: Context,
    private val dao: NeoGbcDao
) {
    val allRoms: Flow<List<RomEntity>> = dao.observeAllRoms()
    val scannedDirectories: Flow<List<ScannedDirectoryEntity>> = dao.observeDirectories()

    fun observeSaveStates(romId: Long): Flow<List<SaveStateEntity>> = dao.observeSaveStatesForRom(romId)
    fun observeCheats(romId: Long): Flow<List<CheatEntity>> = dao.observeCheatsForRom(romId)

    private val romsInternalDir = File(context.filesDir, "roms").apply { mkdirs() }
    private val savesInternalDir = File(context.filesDir, "saves").apply { mkdirs() }
    private val statesInternalDir = File(context.filesDir, "states").apply { mkdirs() }
    private val coversInternalDir = File(context.filesDir, "covers").apply { mkdirs() }

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
                it.fileName.equals(fileName, ignoreCase = true) ||
                    (it.crc32Hex == header.crc32Hex && it.title.equals(title, ignoreCase = true))
            }
            if (existing != null) {
                return@withContext existing
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
            entity.copy(id = newId)
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
                it.fileName.equals(fileName, ignoreCase = true) ||
                    (!it.isBundledAsset && it.crc32Hex == header.crc32Hex && it.title.equals(title, ignoreCase = true))
            }
            if (existing != null) {
                return@withContext existing
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
            entity.copy(id = newId)
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

            val existing = dao.getAllRomsOnce().firstOrNull {
                it.fileName.equals(fileName, ignoreCase = true) ||
                    (!it.isBundledAsset && it.crc32Hex == header.crc32Hex && it.title.equals(title, ignoreCase = true))
            }
            if (existing != null) return existing

            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val localFile = File(romsInternalDir, "${header.crc32Hex}_$safeName")
            localFile.writeBytes(romBytes)

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
            entity.copy(id = id)
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
        } catch (_: Exception) {
        }
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

    // Save States with PNG screenshot preview
    suspend fun writeSaveState(
        romId: Long,
        slotIndex: Int,
        label: String,
        stateBytes: ByteArray,
        frameBuffer: IntArray,
        playTimeSeconds: Long
    ): SaveStateEntity = withContext(Dispatchers.IO) {
        val existing = dao.getSaveState(romId, slotIndex)
        val stateFile = File(statesInternalDir, "rom_${romId}_slot_${slotIndex}.ngbc")
        stateFile.writeBytes(stateBytes)

        val previewFile = File(statesInternalDir, "rom_${romId}_slot_${slotIndex}.png")
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

    suspend fun readSaveStateBytes(saveState: SaveStateEntity): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(saveState.stateFilePath)
        if (file.exists()) file.readBytes() else null
    }

    suspend fun deleteSaveState(saveState: SaveStateEntity) = withContext(Dispatchers.IO) {
        File(saveState.stateFilePath).delete()
        saveState.previewImagePath?.let { File(it).delete() }
        dao.deleteSaveState(saveState.id)
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
