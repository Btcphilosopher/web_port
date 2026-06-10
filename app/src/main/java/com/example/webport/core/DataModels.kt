package com.example.webport.core

sealed interface AccessPolicy {
    object Public : AccessPolicy {
        override fun toString(): String = "Public"
    }
    
    data class Private(val invitedKeys: List<String>) : AccessPolicy {
        override fun toString(): String = "Private(keys=${invitedKeys.joinToString(",")})"
    }
    
    data class TokenGated(val tokenId: String, val minimumBalance: Long) : AccessPolicy {
        override fun toString(): String = "TokenGated(tokenId=$tokenId,min=$minimumBalance)"
    }
    
    data class PaidAccess(val price: Long, val currency: String) : AccessPolicy {
        override fun toString(): String = "PaidAccess(price=$price,currency=$currency)"
    }

    companion object {
        fun parse(value: String): AccessPolicy {
            return when {
                value == "Public" -> Public
                value.startsWith("Private") -> {
                    val keysStr = value.substringAfter("keys=").substringBefore(")")
                    val keys = if (keysStr.isBlank()) emptyList() else keysStr.split(",")
                    Private(keys)
                }
                value.startsWith("TokenGated") -> {
                    val tokenId = value.substringAfter("tokenId=").substringBefore(",")
                    val min = value.substringAfter("min=").substringBefore(")").toLongOrNull() ?: 1L
                    TokenGated(tokenId, min)
                }
                value.startsWith("PaidAccess") -> {
                    val price = value.substringAfter("price=").substringBefore(",").toLongOrNull() ?: 0L
                    val currency = value.substringAfter("currency=").substringBefore(")")
                    PaidAccess(price, currency)
                }
                else -> Public
            }
        }
    }
}

data class SiteManifest(
    val id: String,              // web://<id>
    val ownerPublicKey: String,
    val title: String,
    val description: String,
    val contentHash: String,
    val assets: List<String>,    // Paths like index.html, styles.css
    val permissions: AccessPolicy,
    val version: String,
    val signature: String
)

data class HostingStatus(
    val active: Boolean,
    val storageUsed: Long,       // Bytes
    val bandwidthUsed: Long,     // Bytes
    val peersConnected: Int,
    val visitorsOnline: Int
)

data class WalletState(
    val bitcoinBalance: Double,    // BTC
    val lightningSats: Long,       // Sats
    val resourceCredits: Long,     // Credits
    val tokenBalances: Map<String, Long> // TokenId -> Balance
)

data class PeerNode(
    val nodeId: String,
    val name: String,
    val address: String,
    val active: Boolean,
    val latencyMs: Int,
    val reputation: Double,       // 0.0 to 1.0
    val replicatedSitesCount: Int
)

data class MarketItem(
    val id: String,
    val title: String,
    val category: String,         // "Templates", "Plugins", "Storage", "Compute"
    val description: String,
    val cost: Long,               // in Satoshis or Resource Credits
    val currency: String,         // "SATS", "CREDITS"
    val purchased: Boolean,
    val resourcePath: String? = null
)

data class LogEntry(
    val timestamp: Long,
    val level: String,            // "INFO", "WARN", "ERROR", "PAYMENT", "P2P"
    val message: String
)
