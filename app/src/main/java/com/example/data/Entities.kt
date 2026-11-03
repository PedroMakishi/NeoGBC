package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "roms")
data class RomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val headerTitle: String,
    val fileName: String,
    val uriOrAssetPath: String,
    val isBundledAsset: Boolean,
    val isCgb: Boolean,
    val cartridgeType: String,
    val romSizeBytes: Int,
    val ramSizeBytes: Int,
    val crc32Hex: String,
    val customCoverUri: String? = null,
    val autoCoverUrl: String? = null,
    val isFavorite: Boolean = false,
    val lastPlayedTimestamp: Long = 0L,
    val totalPlayTimeSeconds: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "save_states")
data class SaveStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val romId: Long,
    val slotIndex: Int, // 0 = Quick Save / Auto, 1..8 = Manual Slots
    val label: String,
    val stateFilePath: String,
    val previewImagePath: String?,
    val playTimeSeconds: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "cheats")
data class CheatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val romId: Long,
    val title: String,
    val code: String, // GameShark (01XXYYZZ) or Game Genie (XXX-YYY-ZZZ)
    val isEnabled: Boolean = true,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "scanned_directories")
data class ScannedDirectoryEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val romCount: Int,
    val lastScannedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "control_layouts")
data class ControlLayoutEntity(
    @PrimaryKey val profileId: String,
    val dpadX: Float,
    val dpadY: Float,
    val dpadScale: Float,
    val dpadOpacity: Float = 0.88f,
    val abX: Float,
    val abY: Float,
    val abScale: Float,
    val abOpacity: Float = 0.88f,
    val startSelectX: Float,
    val startSelectY: Float,
    val startSelectScale: Float,
    val startSelectOpacity: Float = 0.88f,
    val turboX: Float,
    val turboY: Float,
    val turboScale: Float,
    val turboOpacity: Float = 0.88f,
    val hapticEnabled: Boolean,
    val hapticIntensity: Float,
    val dpadDeadzone: Float
)
