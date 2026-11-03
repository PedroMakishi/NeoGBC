package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NeoGbcDao {
    // ROM Library Queries
    @Query("SELECT * FROM roms ORDER BY title COLLATE NOCASE ASC")
    fun observeAllRoms(): Flow<List<RomEntity>>

    @Query("SELECT * FROM roms")
    suspend fun getAllRomsOnce(): List<RomEntity>

    @Query("SELECT * FROM roms WHERE id = :romId LIMIT 1")
    suspend fun getRomById(romId: Long): RomEntity?

    @Query("SELECT * FROM roms WHERE crc32Hex = :crc32 LIMIT 1")
    suspend fun getRomByCrc(crc32: String): RomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRom(rom: RomEntity): Long

    @Update
    suspend fun updateRom(rom: RomEntity)

    @Query("DELETE FROM roms WHERE id = :romId")
    suspend fun deleteRom(romId: Long)

    // Save States Queries
    @Query("SELECT * FROM save_states WHERE romId = :romId ORDER BY slotIndex ASC")
    fun observeSaveStatesForRom(romId: Long): Flow<List<SaveStateEntity>>

    @Query("SELECT * FROM save_states WHERE romId = :romId AND slotIndex = :slotIndex LIMIT 1")
    suspend fun getSaveState(romId: Long, slotIndex: Int): SaveStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaveState(saveState: SaveStateEntity): Long

    @Query("DELETE FROM save_states WHERE id = :id")
    suspend fun deleteSaveState(id: Long)

    // Cheats Queries
    @Query("SELECT * FROM cheats WHERE romId = :romId ORDER BY createdTimestamp DESC")
    fun observeCheatsForRom(romId: Long): Flow<List<CheatEntity>>

    @Query("SELECT * FROM cheats WHERE romId = :romId")
    suspend fun getCheatsForRomOnce(romId: Long): List<CheatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheat(cheat: CheatEntity): Long

    @Update
    suspend fun updateCheat(cheat: CheatEntity)

    @Query("DELETE FROM cheats WHERE id = :cheatId")
    suspend fun deleteCheat(cheatId: Long)

    // Scanned Directories
    @Query("SELECT * FROM scanned_directories ORDER BY lastScannedTimestamp DESC")
    fun observeDirectories(): Flow<List<ScannedDirectoryEntity>>

    @Query("SELECT * FROM scanned_directories")
    suspend fun getDirectoriesOnce(): List<ScannedDirectoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectory(dir: ScannedDirectoryEntity)

    @Query("DELETE FROM scanned_directories WHERE treeUri = :uri")
    suspend fun deleteDirectory(uri: String)

    // Control Layout Profiles
    @Query("SELECT * FROM control_layouts WHERE profileId = :profileId LIMIT 1")
    suspend fun getControlLayout(profileId: String): ControlLayoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveControlLayout(layout: ControlLayoutEntity)
}
