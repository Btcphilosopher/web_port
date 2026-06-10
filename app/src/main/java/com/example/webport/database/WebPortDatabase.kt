package com.example.webport.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "identities")
data class DBIdentity(
    @PrimaryKey val publicKey: String,
    val privateKey: String,
    val nickname: String,
    val trustScore: Double = 1.0,
    val addressName: String = "" // web://nickname
)

@Entity(tableName = "sites")
data class DBSite(
    @PrimaryKey val id: String, // web://hash or web://domain
    val ownerPublicKey: String,
    val title: String,
    val description: String,
    val contentHash: String,
    val version: String,
    val signature: String,
    val accessPolicy: String, // Public, Private, TokenGated, PaidAccess
    val isHosting: Boolean = true,
    val storageBytes: Long = 0,
    val bandwidthBytes: Long = 0
)

@Entity(tableName = "assets")
data class DBAsset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: String,
    val path: String, // e.g., index.html
    val mimeType: String,
    val content: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class DBTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "PAY_FOR_HOST", "VISITOR_EARN", "MARKETPLACE_BUY", "CREDIT_FUND"
    val amount: Long,
    val currency: String, // "SATS" or "CREDITS"
    val memo: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "peers")
data class DBPeer(
    @PrimaryKey val nodeId: String,
    val name: String,
    val address: String,
    val isActive: Boolean,
    val latencyMs: Int,
    val reputation: Double,
    val replicatedSitesCount: Int
)

@Entity(tableName = "logs")
data class DBLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // "INFO", "WARN", "ERROR", "PAYMENT", "P2P"
    val message: String
)

@Entity(tableName = "marketplace")
data class DBMarket(
    @PrimaryKey val id: String,
    val title: String,
    val category: String, // "Template", "Plugin", "Storage", "Compute"
    val description: String,
    val cost: Long,
    val currency: String,
    val purchased: Boolean = false,
    val resourcePath: String? = null
)

// --- DAOs ---

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities LIMIT 1")
    fun getActiveIdentityFlow(): Flow<DBIdentity?>

    @Query("SELECT * FROM identities LIMIT 1")
    suspend fun getActiveIdentity(): DBIdentity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdentity(identity: DBIdentity)

    @Query("DELETE FROM identities")
    suspend fun clearIdentities()
}

@Dao
interface SiteDao {
    @Query("SELECT * FROM sites")
    fun getAllSitesFlow(): Flow<List<DBSite>>

    @Query("SELECT * FROM sites WHERE id = :siteId LIMIT 1")
    suspend fun getSiteById(siteId: String): DBSite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSite(site: DBSite)

    @Query("UPDATE sites SET isHosting = :hosting WHERE id = :siteId")
    suspend fun updateHostingState(siteId: String, hosting: Boolean)

    @Query("UPDATE sites SET version = :version, contentHash = :hash, signature = :sig WHERE id = :siteId")
    suspend fun updateSiteVersion(siteId: String, version: String, hash: String, sig: String)

    @Query("DELETE FROM sites WHERE id = :siteId")
    suspend fun deleteSite(siteId: String)
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets WHERE siteId = :siteId")
    fun getAssetsForSiteFlow(siteId: String): Flow<List<DBAsset>>

    @Query("SELECT * FROM assets WHERE siteId = :siteId")
    suspend fun getAssetsForSite(siteId: String): List<DBAsset>

    @Query("SELECT * FROM assets WHERE siteId = :siteId AND path = :path LIMIT 1")
    suspend fun getAsset(siteId: String, path: String): DBAsset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: DBAsset)

    @Query("DELETE FROM assets WHERE siteId = :siteId")
    suspend fun clearSiteAssets(siteId: String)

    @Query("DELETE FROM assets WHERE siteId = :siteId AND path = :path")
    suspend fun deleteAsset(siteId: String, path: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<DBTransaction>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE currency = 'SATS' AND type IN ('CREDIT_FUND', 'VISITOR_EARN')")
    suspend fun getTotalSatsEarned(): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE currency = 'SATS' AND type IN ('PAY_FOR_HOST', 'MARKETPLACE_BUY')")
    suspend fun getTotalSatsSpent(): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE currency = 'CREDITS' AND type = 'CREDIT_FUND'")
    suspend fun getTotalCreditsFunded(): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE currency = 'CREDITS' AND type = 'PAY_FOR_HOST'")
    suspend fun getTotalCreditsSpent(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: DBTransaction)
}

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers")
    fun getAllPeersFlow(): Flow<List<DBPeer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: DBPeer)

    @Query("UPDATE peers SET isActive = :active WHERE nodeId = :nodeId")
    suspend fun updatePeerActive(nodeId: String, active: Boolean)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT 50")
    fun getLatestLogsFlow(): Flow<List<DBLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DBLog)

    @Query("DELETE FROM logs")
    suspend fun clearLogs()
}

@Dao
interface MarketDao {
    @Query("SELECT * FROM marketplace")
    fun getAllMarketItemsFlow(): Flow<List<DBMarket>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarketItem(item: DBMarket)

    @Query("UPDATE marketplace SET purchased = 1 WHERE id = :itemId")
    suspend fun markAsPurchased(itemId: String)
}

// --- Main Database Abstract Class ---

@Database(
    entities = [
        DBIdentity::class,
        DBSite::class,
        DBAsset::class,
        DBTransaction::class,
        DBPeer::class,
        DBLog::class,
        DBMarket::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WebPortDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
    abstract fun siteDao(): SiteDao
    abstract fun assetDao(): AssetDao
    abstract fun transactionDao(): TransactionDao
    abstract fun peerDao(): PeerDao
    abstract fun logDao(): LogDao
    abstract fun marketDao(): MarketDao
}
