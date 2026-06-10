package com.example.webport.core

import android.content.Context
import androidx.room.Room
import com.example.webport.database.*
import com.example.webport.identity.CryptoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebPortRepository(private val context: Context) : WebPortService {

    val database: WebPortDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            WebPortDatabase::class.java,
            "webport_secure_storage.db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    private val identityDao by lazy { database.identityDao() }
    private val siteDao by lazy { database.siteDao() }
    private val assetDao by lazy { database.assetDao() }
    private val transactionDao by lazy { database.transactionDao() }
    private val peerDao by lazy { database.peerDao() }
    private val logDao by lazy { database.logDao() }
    private val marketDao by lazy { database.marketDao() }

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // Flows
    val activeIdentityFlow: Flow<DBIdentity?> = identityDao.getActiveIdentityFlow()
    val allSitesFlow: Flow<List<DBSite>> = siteDao.getAllSitesFlow()
    val transactionsFlow: Flow<List<DBTransaction>> = transactionDao.getAllTransactionsFlow()
    val latestLogsFlow: Flow<DBLog?> = logDao.getLatestLogsFlow().map { it.firstOrNull() }
    val allLogsFlow: Flow<List<DBLog>> = logDao.getLatestLogsFlow()
    val peersFlow: Flow<List<DBPeer>> = peerDao.getAllPeersFlow()
    val marketplaceFlow: Flow<List<DBMarket>> = marketDao.getAllMarketItemsFlow()

    // Dynamically calculate wallet state from state modifications & transacting ledgers
    val walletStateFlow: Flow<WalletState> = transactionsFlow.map { txList ->
        var btc = 0.05 // Initial bootstrap BTC
        var sats = 150000L // Bootstrap Satoshi
        var credits = 1000L // Bootstrap bandwidth storage credits
        
        txList.forEach { tx ->
            when (tx.currency) {
                "SATS" -> {
                    if (tx.type in listOf("CREDIT_FUND", "VISITOR_EARN")) {
                        sats += tx.amount
                    } else {
                        sats -= tx.amount
                    }
                }
                "CREDITS" -> {
                    if (tx.type == "CREDIT_FUND") {
                        credits += tx.amount
                    } else if (tx.type == "PAY_FOR_HOST" || tx.type == "MARKETPLACE_BUY") {
                        credits -= tx.amount
                    }
                }
            }
        }
        WalletState(
            bitcoinBalance = btc + (sats / 100000000.0),
            lightningSats = sats,
            resourceCredits = credits,
            tokenBalances = mapOf(
                "WPX" to 750L,
                "MEMOR" to 2200L,
                "COMP" to 140L
            )
        )
    }.flowOn(Dispatchers.Default)

    init {
        // Prepopulate standard data asynchronously on initialization
        repositoryScope.launch {
            prepopulateDatabase()
        }
    }

    private suspend fun prepopulateDatabase() = withContext(Dispatchers.IO) {
        // 1. Initial Identity if none present
        if (identityDao.getActiveIdentity() == null) {
            val keyPair = CryptoEngine.generateIdentityKeyPair()
            val defaultIdentity = DBIdentity(
                publicKey = keyPair.publicKeyBase64,
                privateKey = keyPair.privateKeyBase64,
                nickname = "sovereign",
                trustScore = 0.98,
                addressName = "web://sovereign"
            )
            identityDao.insertIdentity(defaultIdentity)
            addLogInternal("INFO", "New cryptographic keypair generated and saved to Secure Storage Room.")
        }

        // 2. Prepopulate Peers directory if empty
        val currentPeers = peerDao.getAllPeersFlow().first()
        if (currentPeers.isEmpty()) {
            val nodes = listOf(
                DBPeer("peer_alpha_1", "Node Alpha (Replicator)", "ipv4://192.168.4.15:2026", true, 42, 0.99, 4),
                DBPeer("peer_omega_9", "Node Omega (Genesis Gateway)", "ipv4://203.0.113.12:9000", true, 84, 0.95, 12),
                DBPeer("peer_bridge_sol", "Solana-Storefront-Bridge", "ipv4://198.51.100.41:8080", true, 110, 0.92, 2),
                DBPeer("peer_cache_edge_3", "Zero-Cache Edge Router", "ipv4://103.22.201.7:5001", false, 999, 0.15, 0)
            )
            nodes.forEach { peerDao.insertPeer(it) }
            addLogInternal("P2P", "Discovered 4 active sovereign publishing peers from seed trackers.")
        }

        // 3. Prepopulate Marketplace Catalog
        val currentMarket = marketDao.getAllMarketItemsFlow().first()
        if (currentMarket.isEmpty()) {
            val templates = listOf(
                DBMarket(
                    id = "template_portfolio",
                    title = "Minimal Portfolio Hub",
                    category = "Templates",
                    description = "A responsive high-contrast developer resume & decentralized project hub. Clean dark theme.",
                    cost = 5000,
                    currency = "SATS",
                    purchased = false,
                    resourcePath = "templates/portfolio"
                ),
                DBMarket(
                    id = "template_store",
                    title = "Web3 Storefront & Shop",
                    category = "Templates",
                    description = "Sovereign e-commerce store with native cart checkout mechanism, digital goods delivery system & wallet integration.",
                    cost = 25000,
                    currency = "SATS",
                    purchased = false,
                    resourcePath = "templates/store"
                ),
                DBMarket(
                    id = "plugin_sub_gater",
                    title = "Access Gater Pro",
                    category = "Plugins",
                    description = "Subscription routing controller allowing publishers to gate folders by wallet balances or paid access.",
                    cost = 1500,
                    currency = "SATS",
                    purchased = false,
                    resourcePath = "plugins/gater"
                ),
                DBMarket(
                    id = "storage_10gb",
                    title = "10 GB Pinned Replication Block",
                    category = "Storage",
                    description = "Pins your published site packages to 10 reliable nodes in Northern Europe for 60 days.",
                    cost = 300,
                    currency = "CREDITS",
                    purchased = false,
                    resourcePath = null
                )
            )
            templates.forEach { marketDao.insertMarketItem(it) }
            addLogInternal("INFO", "Marketplace templates verified and synchronized.")
        }

        // 4. Create default mock local site packages
        val sites = siteDao.getAllSitesFlow().first()
        if (sites.isEmpty()) {
            // Install standard template sites so visitors can search them
            val myIdentity = identityDao.getActiveIdentity() ?: return@withContext
            
            // Site A: Sovereign Cafe E-Commerce storefront
            val cafePackage = SitePackage(
                id = "web://cryptocafe",
                title = "Sovereign Crypto Cafe",
                description = "Interactive blockchain fueled digital coffee shop and membership site.",
                files = mapOf(
                    "index.html" to """
                        <div class="card">
                            <h1 style="color:#FFB300;">☕ Sovereign Crypto Cafe</h1>
                            <p>Democratizing caffeination. Purchase artisanal beans, secure brew filters, or buy digital cafe tickets using Lightning payments directly.</p>
                            
                            <div class="grid" style="margin-top: 16px;">
                                <div class="item">
                                    <h3>Ethical Ethiopian Dark Roast</h3>
                                    <p>Flavor: Blueberry, Chocolate, Nutty profile.</p>
                                    <button class="action-btn" onclick="addToCart('Ethiopian Roast', '12000')">Buy Bag (12,000 SATS)</button>
                                </div>
                                <div class="item" style="margin-top: 12px;">
                                    <h3>Sovereign Espresso Ticket</h3>
                                    <p>Gives 1 Espresso shot in the Berlin Crypt Bar.</p>
                                    <button class="action-btn" onclick="addToCart('Espresso Ticket', '2500')">Mint Ticket (2,500 SATS)</button>
                                </div>
                            </div>
                        </div>
                    """.trimIndent(),
                    "styles.css" to """
                        body { background: #121212; color: #E0E0E0; font-family: sans-serif; }
                        .card { border: 1px solid #FF3D00; padding: 16px; border-radius: 8px; }
                        .action-btn { background: #FF3D00; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; }
                    """.trimIndent(),
                    "app.js" to "function addToCart(item, cost) { Web3Wallet.requestPayment(item, cost); }"
                ),
                permissions = AccessPolicy.Public,
                version = "1.0.0"
            )
            publishInternal(cafePackage, myIdentity.publicKey, myIdentity.privateKey, broadcast = false)

            // Site B: Sovereign DAO Gated Notes
            val daoPackage = SitePackage(
                id = "web://daofund",
                title = "Genesis DAO Confidential Files",
                description = "Strictly gated notes regarding project allocation and voting schedules.",
                files = mapOf(
                    "index.html" to """
                        <div class="card" style="border-left: 4px solid #BB86FC;">
                            <h1 style="color: #BB86FC;">🏛️ Genesis DAO Vault</h1>
                            <p>You have successfully passed the cryptographic Token-Gated verification! Below are the meeting schedules:</p>
                            <ul style="line-height: 1.8;">
                                <li>🔑 <strong>Proposal #104:</strong> Dynamic Liquidity Provision protocol - Approved</li>
                                <li>🗳️ <strong>Next Voting Session:</strong> June 25th 2026 UTC</li>
                                <li>📈 <strong>Allocations:</strong> 40,000 USDT to Developer WebPort infrastructure funding.</li>
                            </ul>
                        </div>
                    """.trimIndent()
                ),
                permissions = AccessPolicy.TokenGated("WPX", 500L),
                version = "1.0.0"
            )
            publishInternal(daoPackage, myIdentity.publicKey, myIdentity.privateKey, broadcast = false)

            // Site C: Paid Masterclass Portal
            val premiumPackage = SitePackage(
                id = "web://p2pacademy",
                title = "P2P Web Protocol Masterclass",
                description = "Comprehensive guides to cryptography, distributed DHTs, and WebPort hosting.",
                files = mapOf(
                    "index.html" to """
                        <div class="card">
                            <h1 style="color: #4CAF50;">🛠️ WebPort Network Architecture Masterclass</h1>
                            <h3>Course Syllabus Included:</h3>
                            <ol>
                                <li>Symmetric Key Generation & Elliptic Curves in Android</li>
                                <li>Designing Resilient Kademlia Host Routings in Kotlin</li>
                                <li>Broadcasting Hash Indexes via Trackerless Swarms</li>
                            </ol>
                            <p style="background: #1B5E20; padding: 12px; border-radius: 4px;">🎯 Bonus: Offline-first data replication patterns complete zip package attached.</p>
                        </div>
                    """.trimIndent()
                ),
                permissions = AccessPolicy.PaidAccess(1000L, "SATS"),
                version = "2.1.0"
            )
            publishInternal(premiumPackage, myIdentity.publicKey, myIdentity.privateKey, broadcast = false)
        }
    }

    // --- Core Operations Implementations ---

    suspend fun createOrUpdateUserIdentity(nickname: String, mappedAddress: String) = withContext(Dispatchers.IO) {
        val existing = identityDao.getActiveIdentity()
        val identity = if (existing != null) {
            existing.copy(nickname = nickname, addressName = mappedAddress)
        } else {
            val keyPair = CryptoEngine.generateIdentityKeyPair()
            DBIdentity(
                publicKey = keyPair.publicKeyBase64,
                privateKey = keyPair.privateKeyBase64,
                nickname = nickname,
                trustScore = 0.99,
                addressName = mappedAddress
            )
        }
        identityDao.insertIdentity(identity)
        addLogInternal("INFO", "Identity profile created/updated. Registered WebPort address at $mappedAddress")
    }

    override suspend fun publish(site: SitePackage): PublishResult {
        val activeIdentity = identityDao.getActiveIdentity() ?: return PublishResult.Failure("No active cryptographic identity configured on this browser.")
        return publishInternal(site, activeIdentity.publicKey, activeIdentity.privateKey, broadcast = true)
    }

    override suspend fun update(siteId: String, site: SitePackage): PublishResult {
        val activeIdentity = identityDao.getActiveIdentity() ?: return PublishResult.Failure("No identity available.")
        val existingSite = siteDao.getSiteById(siteId) ?: return PublishResult.Failure("Site not found to update.")
        
        if (existingSite.ownerPublicKey != activeIdentity.publicKey) {
            return PublishResult.Failure("Cryptographic authorization failed. Owner key mismatch.")
        }
        
        return publishInternal(site, activeIdentity.publicKey, activeIdentity.privateKey, broadcast = true)
    }

    private suspend fun publishInternal(
        site: SitePackage,
        ownerPub: String,
        ownerPriv: String,
        broadcast: Boolean
    ): PublishResult = withContext(Dispatchers.IO) {
        try {
            // 1. Calculate and digest files
            val mergedContents = site.files.entries.sortedBy { it.key }
                .joinToString("|") { "${it.key}:${CryptoEngine.hashContent(it.value)}" }
            val packageHash = CryptoEngine.sha256(mergedContents)

            // 2. Cryptographically sign the manifest package
            val signature = CryptoEngine.sign(packageHash + site.version + site.id, ownerPriv)

            // 3. Clear and insert assets
            assetDao.clearSiteAssets(site.id)
            var totalBytes = 0L
            site.files.forEach { (path, content) ->
                val mimeType = when {
                    path.endsWith(".html") -> "text/html"
                    path.endsWith(".css") -> "text/css"
                    path.endsWith(".js") -> "application/javascript"
                    path.endsWith(".json") -> "application/json"
                    else -> "text/plain"
                }
                val contentBytes = content.toByteArray(Charsets.UTF_8)
                totalBytes += contentBytes.size
                
                val asset = DBAsset(
                    siteId = site.id,
                    path = path,
                    mimeType = mimeType,
                    content = content
                )
                assetDao.insertAsset(asset)
            }

            // 4. Save site metadata
            val siteDb = DBSite(
                id = site.id,
                ownerPublicKey = ownerPub,
                title = site.title,
                description = site.description,
                contentHash = packageHash,
                version = site.version,
                signature = signature,
                accessPolicy = site.permissions.toString(),
                isHosting = true,
                storageBytes = totalBytes,
                bandwidthBytes = site.files.size * 314L
            )
            siteDao.insertSite(siteDb)

            // 5. Build logs or simulation records
            if (broadcast) {
                addLogInternal("P2P", "Broadcasting webport catalog to active peers: ${site.id} (Hash: ${packageHash.take(12)})")
                // Debit a small bandwidth hosting credit
                transactionDao.insertTransaction(
                    DBTransaction(
                        type = "PAY_FOR_HOST",
                        amount = 15,
                        currency = "CREDITS",
                        memo = "Bandwidth host broadcast fee for ${site.title}"
                    )
                )
                
                // Simulate P2P replication by assigning metadata updates to active nodes
                val activePeers = peerDao.getAllPeersFlow().first().filter { it.isActive }
                activePeers.forEach { peer ->
                    peerDao.insertPeer(peer.copy(replicatedSitesCount = peer.replicatedSitesCount + 1))
                }
                addLogInternal("INFO", "Replication completed on ${activePeers.size} peer nodes recursively.")
            } else {
                addLogInternal("INFO", "Discovered & cached localized webport: ${site.id}")
            }

            PublishResult.Success(site.id, packageHash, site.version, signature)
        } catch (e: Exception) {
            addLogInternal("ERROR", "Publish block failed for ${site.id}: ${e.localizedMessage}")
            PublishResult.Failure(e.localizedMessage ?: "Unknown compilation error.")
        }
    }

    override suspend fun discover(address: String): SiteManifest? = withContext(Dispatchers.IO) {
        val site = siteDao.getSiteById(address) ?: return@withContext null
        val assetsList = assetDao.getAssetsForSite(address).map { it.path }
        SiteManifest(
            id = site.id,
            ownerPublicKey = site.ownerPublicKey,
            title = site.title,
            description = site.description,
            contentHash = site.contentHash,
            assets = assetsList,
            permissions = AccessPolicy.parse(site.accessPolicy),
            version = site.version,
            signature = site.signature
        )
    }

    override suspend fun verify(manifest: SiteManifest): Boolean {
        // Retrieve local files to rebuild hash
        val assets = assetDao.getAssetsForSite(manifest.id)
        val mergedContents = assets.sortedBy { it.path }
            .joinToString("|") { "${it.path}:${CryptoEngine.hashContent(it.content)}" }
        val packageHash = CryptoEngine.sha256(mergedContents)
        
        return CryptoEngine.verify(
            data = packageHash + manifest.version + manifest.id,
            signatureBase64 = manifest.signature,
            publicKeyBase64 = manifest.ownerPublicKey
        )
    }

    override suspend fun setAccessPolicy(siteId: String, policy: AccessPolicy) = withContext(Dispatchers.IO) {
        val existing = siteDao.getSiteById(siteId) ?: return@withContext
        val updated = existing.copy(accessPolicy = policy.toString())
        siteDao.insertSite(updated)
        addLogInternal("INFO", "Updated access policy for $siteId to ${policy.toString()}")
    }

    suspend fun updateHostingState(siteId: String, startHosting: Boolean) = withContext(Dispatchers.IO) {
        siteDao.updateHostingState(siteId, startHosting)
        val term = if (startHosting) "Enabled and broadcasting peer signals." else "Stopped broadcasting. Content offline."
        addLogInternal("INFO", "Site hosting state for $siteId: $term")
    }

    suspend fun deleteSiteInstance(siteId: String) = withContext(Dispatchers.IO) {
        val existing = siteDao.getSiteById(siteId)
        if (existing != null) {
            siteDao.deleteSite(siteId)
            assetDao.clearSiteAssets(siteId)
            addLogInternal("WARN", "Local files deleted & node broadcasting terminated for $siteId.")
        }
    }

    suspend fun fundWalletCredits(amount: Long, currency: String, memo: String) = withContext(Dispatchers.IO) {
        transactionDao.insertTransaction(
            DBTransaction(
                type = "CREDIT_FUND",
                amount = amount,
                currency = currency,
                memo = memo
            )
        )
        addLogInternal("PAYMENT", "Deposited $amount $currency into browser native system.")
    }

    suspend fun buyMarketplaceItem(itemId: String) = withContext(Dispatchers.IO) {
        val item = marketDao.getAllMarketItemsFlow().first().find { it.id == itemId } ?: return@withContext
        if (item.purchased) {
            addLogInternal("WARN", "Item '${item.title}' is already owned.")
            return@withContext
        }

        // Add fee debit transaction
        transactionDao.insertTransaction(
            DBTransaction(
                type = "MARKETPLACE_BUY",
                amount = item.cost,
                currency = item.currency,
                memo = "Purchased Marketplace asset: ${item.title}"
            )
        )

        // Mark purchased
        marketDao.markAsPurchased(itemId)
        addLogInternal("PAYMENT", "Successfully purchased ${item.title} for ${item.cost} ${item.currency}.")

        // Automate installation of this site template directly into host!
        installTemplateSite(item)
    }

    private suspend fun installTemplateSite(item: DBMarket) {
        val identity = identityDao.getActiveIdentity() ?: return
        
        when (item.id) {
            "template_portfolio" -> {
                val siteName = "web://${identity.nickname}-cv"
                val indexHtmlContent = """
                    <div style="background:#111; color:#fff; padding:20px; border-radius:12px; font-family:sans-serif; border: 1px solid #00E676;">
                        <span style="background:#1B5E20; color:#00E676; padding:4px 8px; border-radius:4px; font-size:12px; font-weight:bold;">PUBLISHED VIA WEBPORT</span>
                        <h1 style="color:#00E676; margin-top:12px;">💻 ${identity.nickname.replaceFirstChar { it.uppercase() }}'s Digital Vault</h1>
                        <p style="font-size:16px; color:#aaa;">Sovereign Developer Portfolio & Decentralized Storefront</p>
                        
                        <div style="border-top:1px solid #333; margin-top:15px; padding-top:15px;">
                            <h3>🚀 Key Skills</h3>
                            <div style="display:flex; gap:8px; flex-wrap:wrap; margin-top:8px;">
                                <span style="background:#222; border:1px solid #333; padding:4px 10px; border-radius:16px; font-size:13px;">Kotlin Browser Engine</span>
                                <span style="background:#222; border:1px solid #333; padding:4px 10px; border-radius:16px; font-size:13px;">ECDSA & Hashes</span>
                                <span style="background:#222; border:1px solid #333; padding:4px 10px; border-radius:16px; font-size:13px;">Sovereign Networks</span>
                            </div>
                        </div>
                        
                        <div style="margin-top:20px;">
                            <h3>🛒 Available Digital Products</h3>
                            <div style="background:#1E1E1E; padding:12px; border-radius:8px; margin-top:10px; display:flex; justify-content:space-between; align-items:center;">
                                <div>
                                    <strong>Private Key Backup Shell Script</strong><br/>
                                    <span style="color:#FFB300; font-size:14px;">500 SATS</span>
                                </div>
                                <button style="background:#00E676; border:none; color:black; font-weight:bold; padding:8px 16px; border-radius:6px; cursor:pointer;" onclick="Web3Wallet.requestPayment('Backup Script', '500')">Purchase</button>
                            </div>
                        </div>
                    </div>
                """.trimIndent()

                val portfolioPackage = SitePackage(
                    id = siteName,
                    title = "${identity.nickname.replaceFirstChar { it.uppercase() }}'s Resilient CV",
                    description = "Personal WebPort detailing project accomplishments and public code verification keys.",
                    files = mapOf(
                        "index.html" to indexHtmlContent,
                        "styles.css" to "body { background: #000; color: #fff; }"
                    ),
                    permissions = AccessPolicy.Public,
                    version = "1.0.0"
                )
                publishInternal(portfolioPackage, identity.publicKey, identity.privateKey, broadcast = true)
                addLogInternal("INFO", "Auto-installed customized profile template to WebPort address: $siteName")
            }
            "template_store" -> {
                val siteName = "web://${identity.nickname}-shop"
                val storeHtml = """
                    <div style="background:#121212; color:#fff; padding:20px; border-radius:12px; font-family:sans-serif; border: 1px solid #2196F3;">
                        <h1 style="color:#2196F3;">🏪 Sovereign Store</h1>
                        <p>Fully ledger-native digital shop broadcasting directly from my device.</p>
                        
                        <div style="display:grid; grid-template-columns: 1fr; gap:16px; margin-top:20px;">
                            <div style="background:#1e1e1e; padding:12px; border-radius:8px; border:1px solid #333;">
                                <h3>🎨 Aesthetic Desktop Wallpaper Pack</h3>
                                <p>Custom cosmic renders optimized for wide displays.</p>
                                <button style="background:#2196F3; border:none; color:white; padding:8px 16px; border-radius:4px; font-weight:bold; cursor:pointer;" onclick="Web3Wallet.requestPayment('Artwork Wallpapers', '1500')">Pay 1500 SATS</button>
                            </div>
                            <div style="background:#1e1e1e; padding:12px; border-radius:8px; border:1px solid #333;">
                                <h3>🛠️ Android Sandbox Plugin Core</h3>
                                <p>Executable compose layers fully pre-packaged.</p>
                                <button style="background:#2196F3; border:none; color:white; padding:8px 16px; border-radius:4px; font-weight:bold; cursor:pointer;" onclick="Web3Wallet.requestPayment('COM Plugin', '4000')">Pay 4000 SATS</button>
                            </div>
                        </div>
                    </div>
                """.trimIndent()

                val storePackage = SitePackage(
                    id = siteName,
                    title = "${identity.nickname.replaceFirstChar { it.uppercase() }}'s Digital Shopfront",
                    description = "Self-hosted store assets with integrated payment hooks.",
                    files = mapOf(
                        "index.html" to storeHtml,
                        "app.js" to "function buyItem(name, cost) { Web3Wallet.requestPayment(name, cost); }"
                    ),
                    permissions = AccessPolicy.Public,
                    version = "1.0.0"
                )
                publishInternal(storePackage, identity.publicKey, identity.privateKey, broadcast = true)
                addLogInternal("INFO", "Auto-installed customized storefront template to WebPort address: $siteName")
            }
            "plugin_sub_gater" -> {
                // Installs a plugin gate controller rule inside the host logger
                addLogInternal("INFO", "Gater mechanism installed. Gating capabilities activated for /private directory pathways.")
            }
            else -> {
                addLogInternal("INFO", "Applied storage or computation credits upgrade directly to host system properties.")
            }
        }
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        logDao.clearLogs()
    }

    private suspend fun addLogInternal(level: String, message: String) {
        logDao.insertLog(DBLog(level = level, message = message))
    }

    suspend fun writeLog(level: String, message: String) {
        addLogInternal(level, message)
    }

    suspend fun getAssetsForSite(siteId: String): List<DBAsset> = withContext(Dispatchers.IO) {
        return@withContext assetDao.getAssetsForSite(siteId)
    }
}
