package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.DisplayCaptureMode
import com.example.model.TabletResolutionPreset
import com.example.model.VideoCodecProfile
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "host_config")
data class HostConfigEntity(
    @PrimaryKey val id: Int = 1,
    val hostName: String = "SolisHost-Phone",
    val captureModeName: String = DisplayCaptureMode.VIRTUAL_EXTENDED.name,
    val resolutionPresetName: String = TabletResolutionPreset.WUXGA_1920_1200.name,
    val refreshRateHz: Int = 90,
    val virtualDpi: Int = 240,
    val codecProfileName: String = VideoCodecProfile.HEVC_H265.name,
    val targetBitrateMbps: Int = 45,
    val fecPercentage: Int = 20,
    val rootModeEnabled: Boolean = true,
    val rootDirectSurfaceControl: Boolean = true,
    val rootUinputInjection: Boolean = true,
    val rootAutoBlankOled: Boolean = false,
    val rootLockCpuGovernor: Boolean = true,
    val lowLatencyCbrMode: Boolean = true
)

@Entity(tableName = "paired_clients")
data class PairedClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientName: String,
    val clientIpAddress: String,
    val tabletModelSpec: String,
    val certFingerprint: String,
    val pairedTimestampMs: Long,
    val lastConnectedMs: Long,
    val preferredDisplayMode: String,
    val isTrusted: Boolean = true
)

@Dao
interface SolisDao {
    @Query("SELECT * FROM host_config WHERE id = 1 LIMIT 1")
    fun observeHostConfig(): Flow<HostConfigEntity?>

    @Query("SELECT * FROM host_config WHERE id = 1 LIMIT 1")
    suspend fun getHostConfigOnce(): HostConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHostConfig(config: HostConfigEntity)

    @Query("SELECT * FROM paired_clients ORDER BY lastConnectedMs DESC")
    fun observePairedClients(): Flow<List<PairedClientEntity>>

    @Query("SELECT COUNT(*) FROM paired_clients")
    suspend fun getPairedClientCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPairedClient(client: PairedClientEntity)

    @Query("DELETE FROM paired_clients WHERE id = :clientId")
    suspend fun deletePairedClient(clientId: Int)

    @Query("UPDATE paired_clients SET lastConnectedMs = :timestampMs WHERE id = :clientId")
    suspend fun updateClientLastConnected(clientId: Int, timestampMs: Long)
}

@Database(
    entities = [HostConfigEntity::class, PairedClientEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SolisDatabase : RoomDatabase() {
    abstract fun solisDao(): SolisDao

    companion object {
        @Volatile
        private var INSTANCE: SolisDatabase? = null

        fun getInstance(context: Context): SolisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SolisDatabase::class.java,
                    "solishost_sunshine.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class SolisRepository(private val dao: SolisDao) {
    val hostConfigFlow: Flow<HostConfigEntity?> = dao.observeHostConfig()
    val pairedClientsFlow: Flow<List<PairedClientEntity>> = dao.observePairedClients()

    suspend fun ensureDefaultData() {
        val existingConfig = dao.getHostConfigOnce()
        if (existingConfig == null) {
            dao.saveHostConfig(HostConfigEntity())
        }
        if (dao.getPairedClientCount() == 0) {
            val now = System.currentTimeMillis()
            dao.insertPairedClient(
                PairedClientEntity(
                    clientName = "Moonlight • 10\" Tablet (WQXGA)",
                    clientIpAddress = "192.168.1.142",
                    tabletModelSpec = "10.4\" 16:10 • 2560×1600 @ 120Hz • Stylus & Multi-Touch",
                    certFingerprint = "SHA256:9F:4A:C8:11:0E:7B:33:D2:8A:5F:19:C0",
                    pairedTimestampMs = now - 86_400_000L,
                    lastConnectedMs = now - 3_600_000L,
                    preferredDisplayMode = DisplayCaptureMode.VIRTUAL_EXTENDED.name,
                    isTrusted = true
                )
            )
        }
    }

    suspend fun getOrCreateConfig(): HostConfigEntity {
        return dao.getHostConfigOnce() ?: HostConfigEntity().also { dao.saveHostConfig(it) }
    }

    suspend fun updateConfig(transform: (HostConfigEntity) -> HostConfigEntity) {
        val current = getOrCreateConfig()
        dao.saveHostConfig(transform(current))
    }

    suspend fun addPairedClient(client: PairedClientEntity) {
        dao.insertPairedClient(client)
    }

    suspend fun removePairedClient(clientId: Int) {
        dao.deletePairedClient(clientId)
    }

    suspend fun markClientConnected(clientId: Int) {
        dao.updateClientLastConnected(clientId, System.currentTimeMillis())
    }
}
