package com.example.webport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webport.core.*
import com.example.webport.database.*
import com.example.webport.identity.CryptoEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface BrowserPreviewState {
    object Idle : BrowserPreviewState
    object Loading : BrowserPreviewState
    data class Success(
        val manifest: SiteManifest,
        val indexHtml: String,
        val cssContent: String,
        val isVerified: Boolean
    ) : BrowserPreviewState
    
    data class BlockedTokenGated(
        val siteId: String,
        val title: String,
        val requiredToken: String,
        val minimumBalance: Long,
        val userBalance: Long
    ) : BrowserPreviewState

    data class BlockedPaidGated(
        val siteId: String,
        val title: String,
        val price: Long,
        val currency: String
    ) : BrowserPreviewState

    data class Error(val message: String) : BrowserPreviewState
}

class WebPortViewModel(private val repository: WebPortRepository) : ViewModel() {

    // Main flows from Repository
    val activeIdentity: StateFlow<DBIdentity?> = repository.activeIdentityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hostedSites: StateFlow<List<DBSite>> = repository.allSitesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val walletState: StateFlow<WalletState> = repository.walletStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WalletState(0.05, 150000, 1000, emptyMap()))

    val peers: StateFlow<List<DBPeer>> = repository.peersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<DBTransaction>> = repository.transactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<DBLog>> = repository.allLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val marketItems: StateFlow<List<DBMarket>> = repository.marketplaceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state states
    private val _currentUrl = MutableStateFlow("web://cryptocafe")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _previewState = MutableStateFlow<BrowserPreviewState>(BrowserPreviewState.Idle)
    val previewState: StateFlow<BrowserPreviewState> = _previewState.asStateFlow()

    private val _activeScreen = MutableStateFlow("dashboard") // "dashboard", "publisher", "wallet", "peers", "market", "logs"
    val activeScreen: StateFlow<String> = _activeScreen.asStateFlow()

    // Commerce checkout popup model
    data class PaymentRequest(
        val itemId: String,
        val cost: Long,
        val siteId: String,
        val active: Boolean = false
    )
    private val _pendingPaymentRequest = MutableStateFlow(PaymentRequest("", 0L, "", false))
    val pendingPaymentRequest: StateFlow<PaymentRequest> = _pendingPaymentRequest.asStateFlow()

    // Unlock status database state
    private val unlockedPaidSites = MutableStateFlow<Set<String>>(emptySet())

    init {
        // Run initial resolution
        viewModelScope.launch {
            resolveUrl(_currentUrl.value)
        }
    }

    fun changeScreen(screen: String) {
        _activeScreen.value = screen
    }

    fun navigateToUrl(url: String) {
        val formattedUrl = if (url.startsWith("web://")) url else "web://$url"
        _currentUrl.value = formattedUrl
        viewModelScope.launch {
            resolveUrl(formattedUrl)
        }
    }

    // Dynamic resolution layer parsing address and enforcing security rules
    suspend fun resolveUrl(url: String) {
        _previewState.value = BrowserPreviewState.Loading
        
        try {
            val manifest = repository.discover(url)
            if (manifest == null) {
                _previewState.value = BrowserPreviewState.Error("WebPort '$url' could not be resolved on the decentralized routing table.")
                repository.writeLog("WARN", "Discovery lookup missed for address: $url")
                return
            }

            // Check if site hosting is enabled
            val siteDb = repository.database.siteDao().getSiteById(url)
            if (siteDb != null && !siteDb.isHosting) {
                _previewState.value = BrowserPreviewState.Error("WebPort gateway error: Site '$url' is resolved but the host node is currently offline (Stop Hosting enabled).")
                return
            }

            // Perform cryptographic validation of signature & assets
            val signatureOK = repository.verify(manifest)
            
            // Log access attempt
            repository.writeLog("P2P", "Resolved site $url (Signature Validation: ${if (signatureOK) "PASS ✅" else "FAIL ❌"})")

            // Parse assets contents
            val localAssets = repository.getAssetsForSite(url)
            val indexAsset = localAssets.find { it.path == "index.html" }
            val cssAsset = localAssets.find { it.path == "styles.css" }

            val indexHtmlText = indexAsset?.content ?: "<div style='color:red;'>index.html is missing inside the WebPort package!</div>"
            val cssText = cssAsset?.content ?: ""

            // Gating verification check
            when (val policy = manifest.permissions) {
                is AccessPolicy.Public -> {
                    _previewState.value = BrowserPreviewState.Success(manifest, indexHtmlText, cssText, signatureOK)
                }
                is AccessPolicy.TokenGated -> {
                    val wallet = walletState.value
                    val userBal = wallet.tokenBalances[policy.tokenId] ?: 0L
                    if (userBal < policy.minimumBalance) {
                        _previewState.value = BrowserPreviewState.BlockedTokenGated(
                            siteId = manifest.id,
                            title = manifest.title,
                            requiredToken = policy.tokenId,
                            minimumBalance = policy.minimumBalance,
                            userBalance = userBal
                        )
                    } else {
                        _previewState.value = BrowserPreviewState.Success(manifest, indexHtmlText, cssText, signatureOK)
                    }
                }
                is AccessPolicy.PaidAccess -> {
                    // Check if already unlocked locally in-session or through previous transacting history
                    val alreadyUnlocked = unlockedPaidSites.value.contains(manifest.id) || 
                                          repository.transactionsFlow.first().any { it.type == "VISITOR_EARN" && it.memo.contains(manifest.id) }
                    
                    if (!alreadyUnlocked) {
                        _previewState.value = BrowserPreviewState.BlockedPaidGated(
                            siteId = manifest.id,
                            title = manifest.title,
                            price = policy.price,
                            currency = policy.currency
                        )
                    } else {
                        _previewState.value = BrowserPreviewState.Success(manifest, indexHtmlText, cssText, signatureOK)
                    }
                }
                is AccessPolicy.Private -> {
                    // Check if owner pubkey matches active user pubkey (owner can always view)
                    val myPub = activeIdentity.value?.publicKey ?: ""
                    if (myPub == manifest.ownerPublicKey || policy.invitedKeys.contains(myPub)) {
                        _previewState.value = BrowserPreviewState.Success(manifest, indexHtmlText, cssText, signatureOK)
                    } else {
                        _previewState.value = BrowserPreviewState.Error("Cryptographic Permission Gated: Your public key is not present on the authorized manifest whitelist.")
                    }
                }
            }
        } catch (e: Exception) {
            _previewState.value = BrowserPreviewState.Error("Resolution Exception: ${e.localizedMessage}")
        }
    }

    // Triggered when dynamic JS click requests web3 payment hooks inside the simulated browser
    fun triggerWeb3StorefrontPaymentRequest(itemName: String, cost: Long, siteId: String) {
        _pendingPaymentRequest.value = PaymentRequest(itemName, cost, siteId, true)
    }

    fun dismissPaymentRequest() {
        _pendingPaymentRequest.value = PaymentRequest("", 0L, "", false)
    }

    fun confirmWeb3StorefrontPayment() {
        val req = _pendingPaymentRequest.value
        if (!req.active) return
        
        viewModelScope.launch {
            val wallet = walletState.value
            if (wallet.lightningSats < req.cost) {
                repository.writeLog("ERROR", "Web3 payment failed: Insufficient Lightning balance. Required ${req.cost} SATS.")
                dismissPaymentRequest()
                return@launch
            }

            // Spend Satoshis
            repository.database.transactionDao().insertTransaction(
                DBTransaction(
                    type = "MARKETPLACE_BUY", // debit
                    amount = req.cost,
                    currency = "SATS",
                    memo = "Web3 Store Buy: '${req.itemId}' from ${req.siteId}"
                )
            )

            repository.writeLog("PAYMENT", "Payment of ${req.cost} SATS processed successfully! Digital asset '${req.itemId}' unlocked.")
            dismissPaymentRequest()
            
            // Re-resolve
            resolveUrl(_currentUrl.value)
        }
    }

    fun paySiteAccessFee(siteId: String, price: Long) {
        viewModelScope.launch {
            val wallet = walletState.value
            if (wallet.lightningSats < price) {
                repository.writeLog("ERROR", "Paid-Access paywall: Insufficient balance.")
                return@launch
            }

            // Insert transaction debiting wallet
            repository.database.transactionDao().insertTransaction(
                DBTransaction(
                    type = "MARKETPLACE_BUY", // debit
                    amount = price,
                    currency = "SATS",
                    memo = "Access fee unlock for $siteId"
                )
            )

            // Grant entry
            unlockedPaidSites.value = unlockedPaidSites.value + siteId
            repository.writeLog("PAYMENT", "Access unlocked cryptographically for $siteId. Transferring transaction to host ledger.")
            
            // Re-resolve
            resolveUrl(siteId)
        }
    }

    // Faucets to instantly fuel tokens for interaction
    fun claimFaucet(amount: Long, currency: String) {
        viewModelScope.launch {
            repository.fundWalletCredits(amount, currency, "Faucet Airdrop topup")
        }
    }

    // Creating/deploying a brand-new custom user webport
    fun createAndPublishCustomWebPort(
        addressName: String, // e.g. "my-blog" (automatically prefixed web://)
        title: String,
        description: String,
        htmlContent: String,
        gatingSelection: String, // "Public", "TokenGated", "PaidAccess"
        tokenId: String = "WPX",
        minimumBalance: Long = 100L,
        price: Long = 1000L
    ) {
        viewModelScope.launch {
            val formattedAddress = if (addressName.startsWith("web://")) addressName else "web://$addressName"
            
            val policy = when (gatingSelection) {
                "Public" -> AccessPolicy.Public
                "TokenGated" -> AccessPolicy.TokenGated(tokenId, minimumBalance)
                "PaidAccess" -> AccessPolicy.PaidAccess(price, "SATS")
                else -> AccessPolicy.Public
            }

            val newPackage = SitePackage(
                id = formattedAddress,
                title = title,
                description = description,
                files = mapOf(
                    "index.html" to htmlContent,
                    "styles.css" to "body { background: #121212; color: #FFF; font-family: sans-serif; padding: 20px; }"
                ),
                permissions = policy,
                version = "1.0.0"
            )

            val result = repository.publish(newPackage)
            if (result is PublishResult.Success) {
                repository.writeLog("INFO", "WebPort package compiled and published. Address: ${result.siteId}. Signed Content Hash: ${result.contentHash.take(16)}")
                // View the newly published site inside the browser!
                navigateToUrl(formattedAddress)
            } else if (result is PublishResult.Failure) {
                repository.writeLog("ERROR", "Publish compiler failed: ${result.error}")
            }
        }
    }

    // Stop / Start broadcasting site
    fun toggleHostingState(siteId: String, currentHosting: Boolean) {
        viewModelScope.launch {
            repository.updateHostingState(siteId, !currentHosting)
        }
    }

    // Rollback / upgrade mechanism
    fun triggerIncrementalUpdate(siteId: String, title: String, description: String, htmlContent: String, activeVersion: String) {
        viewModelScope.launch {
            val nextVersion = incrementVersion(activeVersion)
            val policy = resolveAccessPolicyForSite(siteId)

            val packageUpdate = SitePackage(
                id = siteId,
                title = title,
                description = description,
                files = mapOf(
                    "index.html" to htmlContent,
                    "styles.css" to "body { background: #121212; color: #FFF; font-family: sans-serif; padding: 20px; }"
                ),
                permissions = policy,
                version = nextVersion
            )

            val result = repository.update(siteId, packageUpdate)
            if (result is PublishResult.Success) {
                repository.writeLog("INFO", "Incremental deployment complete. Upgraded $siteId to Version $nextVersion.")
                if (_currentUrl.value == siteId) {
                    resolveUrl(siteId)
                }
            }
        }
    }

    fun triggerRollback(siteId: String, targetVersion: String) {
        viewModelScope.launch {
            val siteDb = repository.database.siteDao().getSiteById(siteId) ?: return@launch
            val assets = repository.database.assetDao().getAssetsForSite(siteId)
            val indexHtml = assets.find { it.path == "index.html" }?.content ?: ""
            
            // Re-publish under rolled back version string
            val policy = AccessPolicy.parse(siteDb.accessPolicy)
            val rolledPackage = SitePackage(
                id = siteId,
                title = siteDb.title,
                description = siteDb.description,
                files = mapOf(
                    "index.html" to indexHtml + "<!-- Rolled back to $targetVersion -->",
                    "styles.css" to "body { background: #121212; color: #FFF; }"
                ),
                permissions = policy,
                version = targetVersion
            )

            val result = repository.update(siteId, rolledPackage)
            if (result is PublishResult.Success) {
                repository.writeLog("WARN", "Rollback execution successful. Reverted cryptographics on $siteId to $targetVersion.")
                if (_currentUrl.value == siteId) {
                    resolveUrl(siteId)
                }
            }
        }
    }

    fun deleteHostedSite(siteId: String) {
        viewModelScope.launch {
            repository.deleteSiteInstance(siteId)
            if (_currentUrl.value == siteId) {
                navigateToUrl("web://cryptocafe")
            }
        }
    }

    fun purchaseMarketplaceItem(itemId: String) {
        viewModelScope.launch {
            repository.buyMarketplaceItem(itemId)
        }
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun updateIdentityNickname(nickname: String, actualAddress: String) {
        viewModelScope.launch {
            repository.createOrUpdateUserIdentity(nickname, actualAddress)
        }
    }

    private fun incrementVersion(ver: String): String {
        val parts = ver.split(".").map { it.toIntOrNull() ?: 0 }
        if (parts.size < 3) return "1.0.1"
        return "${parts[0]}.${parts[1]}.${parts[2] + 1}"
    }

    private suspend fun resolveAccessPolicyForSite(siteId: String): AccessPolicy {
        val siteDb = repository.database.siteDao().getSiteById(siteId) ?: return AccessPolicy.Public
        return AccessPolicy.parse(siteDb.accessPolicy)
    }
}
