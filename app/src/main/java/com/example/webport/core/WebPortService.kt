package com.example.webport.core

data class SitePackage(
    val id: String, // e.g., "web://store" or "web://bafybei"
    val title: String,
    val description: String,
    val files: Map<String, String>, // path -> text content
    val permissions: AccessPolicy,
    val version: String
)

sealed class PublishResult {
    data class Success(
        val siteId: String,
        val contentHash: String,
        val version: String,
        val signature: String
    ) : PublishResult()
    
    data class Failure(val error: String) : PublishResult()
}

interface WebPortService {
    /**
     * Installs and publishes a completely new decentralized site package onto this host node.
     */
    suspend fun publish(site: SitePackage): PublishResult

    /**
     * Updates an existing site package on this node, updating version and cryptographic signatures.
     */
    suspend fun update(siteId: String, site: SitePackage): PublishResult

    /**
     * Searches the local state & distributed discovery tracker for a site address.
     */
    suspend fun discover(address: String): SiteManifest?

    /**
     * Validates that the site manifest signed contents haven't been tampered with.
     */
    suspend fun verify(manifest: SiteManifest): Boolean

    /**
     * Updates the local network visibility and gated permissions of a hosted site.
     */
    suspend fun setAccessPolicy(siteId: String, policy: AccessPolicy)
}
