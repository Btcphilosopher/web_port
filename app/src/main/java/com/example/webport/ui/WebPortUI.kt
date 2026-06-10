package com.example.webport.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.webport.core.AccessPolicy
import com.example.webport.database.DBLog
import com.example.webport.database.DBPeer
import com.example.webport.database.DBSite
import com.example.webport.identity.CryptoEngine
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WebPortMainScreen(viewModel: WebPortViewModel) {
    val activeScreen by viewModel.activeScreen.collectAsState()
    val identity by viewModel.activeIdentity.collectAsState()
    val wallet by viewModel.walletState.collectAsState()
    val sites by viewModel.hostedSites.collectAsState()
    val peers by viewModel.peers.collectAsState()
    
    val activeHostingCount = remember(sites) { sites.count { it.isHosting } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkSovereignDb,
        bottomBar = {
            BottomNavigationBar(
                activeScreen = activeScreen,
                onScreenSelected = { viewModel.changeScreen(it) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // Header Info Bar
            HeaderBar(
                nickname = identity?.nickname ?: "sovereign",
                pubKey = identity?.publicKey ?: "",
                activeHostingCount = activeHostingCount,
                peersOnline = peers.count { it.isActive },
                walletBalanceSats = wallet.lightningSats
            )

            HorizontalDivider(color = SoftBorderColor, thickness = 1.dp)

            // Dynamic Panel Switching
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeScreen) {
                    "dashboard" -> BrowserTabScreen(viewModel)
                    "publisher" -> PublisherTabScreen(viewModel)
                    "wallet" -> WalletTabScreen(viewModel)
                    "peers" -> PeersTabScreen(viewModel)
                    "market" -> MarketplaceTabScreen(viewModel)
                    "logs" -> DiagnosticsTabScreen(viewModel)
                }
            }
        }

        // Web3 Storefront Payment Authorization Modal
        val pendingPayment by viewModel.pendingPaymentRequest.collectAsState()
        if (pendingPayment.active) {
            Web3PaymentAuthorizationDialog(
                request = pendingPayment,
                onConfirm = { viewModel.confirmWeb3StorefrontPayment() },
                onDismiss = { viewModel.dismissPaymentRequest() }
            )
        }
    }
}

// --- Top Header Bar ---

@Composable
fun HeaderBar(
    nickname: String,
    pubKey: String,
    activeHostingCount: Int,
    peersOnline: Int,
    walletBalanceSats: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSovereignDb)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // High Density custom WP identity avatar
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(CyberCyan),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "WP",
                color = Color(0xFF381E72),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SOVEREIGN WEBPORT",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MatrixGreen)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "web://$nickname [${CryptoEngine.getCompactAddress(pubKey)}]",
                    color = CyberCyan,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Status Indicators
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Hosting status light
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateAccentDark)
                    .border(1.dp, SoftBorderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (activeHostingCount > 0) MatrixGreen else Color.Gray)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$activeHostingCount host",
                    color = if (activeHostingCount > 0) MatrixGreen else Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Peers Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateAccentDark)
                    .border(1.dp, SoftBorderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Peers Connected",
                    tint = CyberCyan,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "$peersOnline P2P",
                    color = CyberCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Wallet Quick Counter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateAccentDark)
                    .border(1.dp, SoftBorderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CurrencyBitcoin,
                    contentDescription = "Wallet Balance",
                    tint = BitcoinGold,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "${walletBalanceSats} Sats",
                    color = BitcoinGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// --- Bottom Navigation Menu ---

@Composable
fun BottomNavigationBar(
    activeScreen: String,
    onScreenSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = SlateAccentDark,
        tonalElevation = 8.dp,
        modifier = Modifier.height(68.dp)
    ) {
        val menuItems = listOf(
            Triple("dashboard", "Browser", Icons.Filled.Language),
            Triple("publisher", "Host Studio", Icons.Filled.Backup),
            Triple("wallet", "Wallet", Icons.Filled.Wallet),
            Triple("peers", "P2P Mesh", Icons.Filled.Hub),
            Triple("market", "Storefront", Icons.Filled.Storefront),
            Triple("logs", "Diagnostics", Icons.Filled.Terminal)
        )

        menuItems.forEach { (route, label, icon) ->
            val isSelected = activeScreen == route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onScreenSelected(route) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DarkSovereignDb,
                    selectedTextColor = CyberCyan,
                    indicatorColor = CyberCyan,
                    unselectedIconColor = DarkGreyText,
                    unselectedTextColor = DarkGreyText
                ),
                modifier = Modifier.testTag("nav_item_$route")
            )
        }
    }
}

// ==========================================
// 1. Tab: Browser Preview & Resolutions
// ==========================================

@Composable
fun BrowserTabScreen(viewModel: WebPortViewModel) {
    val currentUrl by viewModel.currentUrl.collectAsState()
    val previewState by viewModel.previewState.collectAsState()
    
    var urlInput by remember { mutableStateOf(currentUrl) }
    
    // Automatically keep input field updated if changed out-of-band
    LaunchedEffect(currentUrl) {
        urlInput = currentUrl
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Modular Address Bar Routing
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateAccentDark)
                .border(1.dp, SoftBorderColor, RoundedCornerShape(8.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, end = 4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (previewState) {
                            is BrowserPreviewState.Success -> {
                                if ((previewState as BrowserPreviewState.Success).isVerified) MatrixGreen else BroadcastOrange
                            }
                            is BrowserPreviewState.Loading -> BitcoinGold
                            else -> Color.Red
                        }
                    )
            )

            TextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text("web://site-address", color = DarkGreyText, fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .weight(1f)
                    .testTag("browser_address_input")
            )

            IconButton(
                onClick = { viewModel.navigateToUrl(urlInput) },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("browser_go_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Navigate to Address",
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Fast resolution bookmark chips
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val bookmarks = listOf(
                "web://cryptocafe" to "☕ Cafe Store",
                "web://daofund" to "🏛️ DAO Gated",
                "web://p2pacademy" to "🎓 Paid Masterclass"
            )
            bookmarks.forEach { (address, name) ->
                val active = currentUrl == address
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Color(0xFF142435) else SlateAccentDark)
                        .border(
                            1.dp,
                            if (active) CyberCyan else SoftBorderColor,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.navigateToUrl(address) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = name,
                        color = if (active) CyberCyan else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Simulated Virtual Container Rendering Frame
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SlateAccentDark)
                .border(1.dp, SoftBorderColor, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            when (val state = previewState) {
                is BrowserPreviewState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = CyberCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Locating and resolving content-addressed trackers...",
                            color = DarkGreyText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is BrowserPreviewState.Success -> {
                    BrowserPreviewRenderer(
                        successState = state,
                        onPaymentRequestTriggered = { name, fee ->
                            viewModel.triggerWeb3StorefrontPaymentRequest(name, fee, state.manifest.id)
                        }
                    )
                }
                is BrowserPreviewState.BlockedTokenGated -> {
                    TokenGatedBlockedPage(
                        blockedState = state,
                        onClaimTokenFaucetClick = {
                            viewModel.claimFaucet(100L, "WPX")
                        }
                    )
                }
                is BrowserPreviewState.BlockedPaidGated -> {
                    PaidAccessBlockedPage(
                        blockedState = state,
                        onPayAccessClick = {
                            viewModel.paySiteAccessFee(state.siteId, state.price)
                        }
                    )
                }
                is BrowserPreviewState.Error -> {
                    ErrorPage(errorMessage = state.message)
                }
                else -> {
                    EmptyBrowserPlaceholder()
                }
            }
        }
    }
}

// Render of compiled/downloaded HTML layout Simulator
@Composable
fun BrowserPreviewRenderer(
    successState: BrowserPreviewState.Success,
    onPaymentRequestTriggered: (String, Long) -> Unit
) {
    val manifest = successState.manifest
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Cryptographic Signer / Security Verification Ribbon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (successState.isVerified) Color(0xFF132A1C) else Color(0xFF2E1C1A))
                .border(
                    1.dp,
                    if (successState.isVerified) MatrixGreen else BroadcastOrange,
                    RoundedCornerShape(8.dp)
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (successState.isVerified) Icons.Default.VerifiedUser else Icons.Default.ReportProblem,
                contentDescription = "Signature Status",
                tint = if (successState.isVerified) MatrixGreen else BroadcastOrange,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (successState.isVerified) "ECDSA Decent-Signed & Verified" else "Signature Unverified/Modified",
                    color = if (successState.isVerified) MatrixGreen else BroadcastOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Signer Hash: ${CryptoEngine.getCompactAddress(manifest.ownerPublicKey)} | Swarm Version: V${manifest.version}",
                    color = DarkGreyText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Inner Simulated HTML / Client Render Frame
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = manifest.title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Text(
                    text = manifest.description,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                HorizontalDivider(color = SoftBorderColor, thickness = 1.dp)
            }

            // Simplistic parsing render mapping HTML template elements directly
            item {
                val rawHtml = successState.indexHtml
                
                if (rawHtml.contains("☕ Sovereign Crypto Cafe")) {
                    CoffeeStoreTemplateDemo(onBuyClick = onPaymentRequestTriggered)
                } else if (rawHtml.contains("🏛️ Genesis DAO Vault")) {
                    DaoVaultTemplateDemo()
                } else if (rawHtml.contains("🛠️ WebPort Network Architecture Masterclass")) {
                    CourseMasterclassDemo()
                } else if (rawHtml.contains("PUBLISHED VIA WEBPORT")) {
                    // Custom newly compiled items
                    CustomNewlyPublishedRender(rawHtml = rawHtml)
                } else {
                    // Generic elegant fallback layout rendering HTML tags as neat Text cards
                    GenericWebPortRender(rawHtmlContent = rawHtml)
                }
            }
        }
    }
}

// --- Specific Site Render Demos ---

@Composable
fun CoffeeStoreTemplateDemo(onBuyClick: (String, Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateAccentDark),
        border = BorderStroke(1.dp, SoftBorderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "☕ Cafe Sovereign Items",
                color = BitcoinGold,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            
            // Product A
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2129))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ethical Ethiopian Dark Roast Roast", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Flavors: Blueberry, cocoa, dynamic berry body.", color = DarkGreyText, fontSize = 11.sp)
                }
                Button(
                    onClick = { onBuyClick("Ethiopian Roast Bag", 12000L) },
                    colors = ButtonDefaults.buttonColors(containerColor = BroadcastOrange),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("buy_ethiopian_btn")
                ) {
                    Text("Buy (12,000 Sat)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Product B
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2129))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sovereign Espresso Ticket Voucher", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Digital receipt token redeemable at physical kiosks.", color = DarkGreyText, fontSize = 11.sp)
                }
                Button(
                    onClick = { onBuyClick("Espresso Ticket", 2500L) },
                    colors = ButtonDefaults.buttonColors(containerColor = BroadcastOrange),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("buy_espresso_btn")
                ) {
                    Text("Mint (2,500 Sat)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DaoVaultTemplateDemo() {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF16132D))
                .border(2.dp, Purple80, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "🏛️ Sovereign DAO Archival Core",
                    color = Purple80,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Gating passed. Private ledger manifests decrypted successfully for authorized session:",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                val bullets = listOf(
                    "🔑 Proposal #104 (Interchain Bridges) approved with 84% quorum.",
                    "🗳️ Next consensus locking epoch scheduled: June 25th 2026 UTC.",
                    "📈 Allocated 40,000 USDT equivalents for WebPort local file replication systems research."
                )
                bullets.forEach { text ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•", color = Purple80, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                        Text(text = text, color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CourseMasterclassDemo() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F1E14))
            .border(1.dp, MatrixGreen, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text("🎓 Distributed Software Architecture Masterclass", color = MatrixGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Lifetime subscription active.", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        
        Text("Lessons Catalog:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        listOf(
            "1. Cryptographics & Key Ring structures in Android Kotlin" to "34 min",
            "2. Designing Kademlia DHT Local Routing Models" to "48 min",
            "3. Broadcasting site directories and Tracker-less swarms" to "28 min"
        ).forEach { (title, len) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, color = Color.LightGray, fontSize = 11.sp)
                Text(len, color = MatrixGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun CustomNewlyPublishedRender(rawHtml: String) {
    // Basic extractor of key text inside our customized dashboard creations
    val titleText = remember(rawHtml) {
        rawHtml.substringAfter("<h1>").substringBefore("</h1>")
            .ifBlank { "Newly Synthesized WebPort" }
            .replace("<span[^>]*>[^<]*</span>".toRegex(), "")
            .trim()
    }
    
    val tagline = remember(rawHtml) {
        rawHtml.substringAfter("<p style=\"font-size:16px; color:#aaa;\">").substringBefore("</p>")
            .ifBlank { rawHtml.substringAfter("<p>").substringBefore("</p>") }
            .trim()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateAccentDark)
            .border(1.dp, SoftBorderColor, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(text = "🔥 LIVE INSTALLED SYSTEM", color = MatrixGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = titleText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = tagline, color = DarkGreyText, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { /* Simulated secondary purchase custom script */ },
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text("Simulate Live Checkouts", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
fun GenericWebPortRender(rawHtmlContent: String) {
    // Elegant text wrapper strip HTML tags roughly
    val cleanText = remember(rawHtmlContent) {
        rawHtmlContent
            .replace("<[^>]*>".toRegex(), "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SoftBorderColor, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "📄 RAW TEXT WEBPORT PACK",
            color = CyberCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = cleanText,
            color = Color.LightGray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// Block screens for restricted gateways

@Composable
fun TokenGatedBlockedPage(
    blockedState: BrowserPreviewState.BlockedTokenGated,
    onClaimTokenFaucetClick: () -> Unit
) {
    var showedFaucetResult by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Token Gate Lock",
            tint = Purple80,
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sovereign Token Gate Gated",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Accessing '${blockedState.title}' requires possessing native cryptographic tokens of: ${blockedState.requiredToken}.",
            color = DarkGreyText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Balance specifications
        Card(
            modifier = Modifier.width(260.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2C)),
            border = BorderStroke(1.dp, Purple80)
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Required Min: ${blockedState.minimumBalance} ${blockedState.requiredToken}", color = Purple80, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Your Wallet Balance: ${blockedState.userBalance} ${blockedState.requiredToken}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                onClaimTokenFaucetClick()
                showedFaucetResult = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Purple80),
            modifier = Modifier.testTag("token_gate_claim_btn")
        ) {
            Text("Mint Faucet Tokens [Claims ${blockedState.requiredToken}]", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        if (showedFaucetResult) {
            Text(
                "Airdrop processing... Re-navigating browser to activate gateway check shortly.",
                color = MatrixGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
fun PaidAccessBlockedPage(
    blockedState: BrowserPreviewState.BlockedPaidGated,
    onPayAccessClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MonetizationOn,
            contentDescription = "Pay Portal Lock",
            tint = BitcoinGold,
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Paid Access Gateway",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "To acquire decentralized download logs and access '${blockedState.title}', submit a cryptographic entry fee.",
            color = DarkGreyText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onPayAccessClick,
            colors = ButtonDefaults.buttonColors(containerColor = BitcoinGold),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .width(240.dp)
                .testTag("pay_site_access_btn")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CurrencyBitcoin, contentDescription = "Bitcoin", tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Unlock with ${blockedState.price} SATS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ErrorPage(errorMessage: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.NetworkWifi3Bar,
            contentDescription = "Resolution Missed",
            tint = BroadcastOrange,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Resolution Failure",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = errorMessage,
            color = DarkGreyText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun EmptyBrowserPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.TravelExplore, contentDescription = "Explore", tint = DarkGreyText, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text("Enter a web:// address to parse local decentral layouts.", color = DarkGreyText, fontSize = 12.sp)
    }
}

// ==========================================
// 2. Tab: Publisher & Control Panel
// ==========================================

@Composable
fun PublisherTabScreen(viewModel: WebPortViewModel) {
    val sites by viewModel.hostedSites.collectAsState()
    
    var showCompiler by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WebPort Host Server Studio",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            
            Button(
                onClick = { showCompiler = !showCompiler },
                colors = ButtonDefaults.buttonColors(containerColor = if (showCompiler) Color.Gray else CyberCyan),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("toggle_compiler_btn")
            ) {
                Text(
                    text = if (showCompiler) "Active Studio List" else "Create WebPort Workspace",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (showCompiler) {
            CompilerWorkspace(
                onCompilePublish = { addr, title, desc, rawHtml, policyChoice, tokenNm, tokenBal, price ->
                    viewModel.createAndPublishCustomWebPort(
                        addressName = addr,
                        title = title,
                        description = desc,
                        htmlContent = rawHtml,
                        gatingSelection = policyChoice,
                        tokenId = tokenNm,
                        minimumBalance = tokenBal,
                        price = price
                    )
                    showCompiler = false
                }
            )
        } else {
            if (sites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No local WebPorts active. Create a new site package workspace!", color = DarkGreyText, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sites) { site ->
                        HostedSiteControlCard(
                            site = site,
                            onToggleHosting = { viewModel.toggleHostingState(site.id, site.isHosting) },
                            onUpgradeIncremental = { title, desc, content, ver ->
                                viewModel.triggerIncrementalUpdate(site.id, title, desc, content, ver)
                            },
                            onRollback = { targetVer ->
                                viewModel.triggerRollback(site.id, targetVer)
                            },
                            onDelete = { viewModel.deleteHostedSite(site.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HostedSiteControlCard(
    site: DBSite,
    onToggleHosting: () -> Unit,
    onUpgradeIncremental: (String, String, String, String) -> Unit,
    onRollback: (String) -> Unit,
    onDelete: () -> Unit
) {
    var expandEditor by remember { mutableStateOf(false) }
    var editHtmlText by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateAccentDark),
        border = BorderStroke(1.dp, if (site.isHosting) MatrixGreen else SoftBorderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = site.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = site.id,
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (site.isHosting) Color(0xFF14241B) else Color(0xFF281312))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (site.isHosting) "Broadcasting" else "Suspended",
                        color = if (site.isHosting) MatrixGreen else Color.Red,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = SoftBorderColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Stats info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Version: ${site.version}", color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("Content Hash: ${site.contentHash.take(8)}...", color = DarkGreyText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("Size: ${site.storageBytes} B", color = DarkGreyText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Operations Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleHosting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (site.isHosting) Color(0xFF331513) else Color(0xFF13351C)
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (site.isHosting) "Stop Hosting" else "Start Host",
                        color = if (site.isHosting) Color.Red else MatrixGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        expandEditor = !expandEditor
                        editHtmlText = "<h1>${site.title}</h1>\n<p>V${site.version} newly upgraded code content...</p>"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232B3F)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Partial Update", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .background(Color(0xFF231414), RoundedCornerShape(20.dp))
                        .size(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = "Delete site", tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }

            // Expanded Editor for incremental updates & instant rollbacks
            if (expandEditor) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF13161F))
                        .padding(10.dp)
                ) {
                    Text("Incremental WebPort Compiler", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    OutlinedTextField(
                        value = editHtmlText,
                        onValueChange = { editHtmlText = it },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onRollback("1.0.0")
                                expandEditor = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BroadcastOrange)
                        ) {
                            Text("Rollback to V1.0.0", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onUpgradeIncremental(site.title, site.description, editHtmlText, site.version)
                                expandEditor = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
                        ) {
                            Text("Publish V${site.version.split(".").firstOrNull() ?: 1}.${site.version.split(".").getOrNull(1) ?: 0}.${(site.version.split(".").lastOrNull()?.toIntOrNull() ?: 0) + 1}", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompilerWorkspace(
    onCompilePublish: (String, String, String, String, String, String, Long, Long) -> Unit
) {
    var webAddress by remember { mutableStateOf("my-sovereign-bio") }
    var title by remember { mutableStateOf("My WebPort Bio Cards") }
    var description by remember { mutableStateOf("Responsive peer-to-peer bio information hosted natively from cell.") }
    
    var indexHtml by remember {
        mutableStateOf(
            """
                <div style="background:#111; color:#fff; padding:20px; border-radius:12px; font-family:sans-serif; border: 1px solid #00E5FF;">
                    <span style="background:#13353F; color:#00E5FF; padding:4px 8px; border-radius:4px; font-size:12px;">PUBLISHED VIA WEBPORT Studio</span>
                    <h1 style="color:#00E5FF; margin-top:12px;">🪐 My Decentralized Cosmos</h1>
                    <p>Welcome! This webpage was cryptographically signed and compiles seamlessly from mobile host storage.</p>
                    
                    <div style="margin-top:20px; border:1px solid #33); padding:10px; border-radius:8px;">
                        <h3>⛓️ Encoded Signature Ledger</h3>
                        <p style="font-size:11sp; color:#00E676;">Verified Trust: Pass ✅</p>
                    </div>
                </div>
            """.trimIndent()
        )
    }

    var gatingSelection by remember { mutableStateOf("Public") } // "Public", "TokenGated", "PaidAccess"
    var tokenId by remember { mutableStateOf("WPX") }
    var minBalance by remember { mutableStateOf("500") }
    var priceFeeSats by remember { mutableStateOf("1000") }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SlateAccentDark)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Create WebPort Package", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            OutlinedTextField(
                value = webAddress,
                onValueChange = { webAddress = it },
                label = { Text("Web3 Route Address (e.g., custom-store)", color = DarkGreyText) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compiler_address_field"),
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Page Title Name", color = DarkGreyText) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Short Description Tracker Metadata", color = DarkGreyText) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = indexHtml,
                onValueChange = { indexHtml = it },
                label = { Text("HTML Source Layout code", color = DarkGreyText) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray, focusedContainerColor = DarkSovereignDb),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .testTag("compiler_html_field")
            )
        }

        // Access Policy selectors
        item {
            Text("Cryptographic Access Control Policy:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val settings = listOf("Public", "TokenGated", "PaidAccess")
                settings.forEach { selection ->
                    val active = gatingSelection == selection
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) Color(0xFF142435) else Color(0xFF13151D))
                            .border(1.dp, if (active) CyberCyan else SoftBorderColor, RoundedCornerShape(8.dp))
                            .clickable { gatingSelection = selection }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(selection, color = if (active) CyberCyan else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (gatingSelection == "TokenGated") {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tokenId,
                        onValueChange = { tokenId = it },
                        label = { Text("Token Symbol", color = DarkGreyText) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minBalance,
                        onValueChange = { minBalance = it },
                        label = { Text("Min Balance", color = DarkGreyText) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        }

        if (gatingSelection == "PaidAccess") {
            item {
                OutlinedTextField(
                    value = priceFeeSats,
                    onValueChange = { priceFeeSats = it },
                    label = { Text("Entry Unlock Price (SATS)", color = DarkGreyText) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    onCompilePublish(
                        webAddress,
                        title,
                        description,
                        indexHtml,
                        gatingSelection,
                        tokenId,
                        minBalance.toLongOrNull() ?: 500L,
                        priceFeeSats.toLongOrNull() ?: 1000L
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("compiler_submit_publish_btn")
            ) {
                Text("Verify, Sign, and Deploy WebPort", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 3. Tab: Wallet & Ledger
// ==========================================

@Composable
fun WalletTabScreen(viewModel: WebPortViewModel) {
    val wallet by viewModel.walletState.collectAsState()
    val txs by viewModel.transactions.collectAsState()

    var showSendDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Balance Card Display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateAccentDark),
            border = BorderStroke(1.dp, SoftBorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SOVEREIGN LEDGER COIN", color = DarkGreyText, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFB300).copy(0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Active Node Wallet", color = BitcoinGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                // Lightning Sats Balance
                Text(
                    text = "${wallet.lightningSats} SATS",
                    color = BitcoinGold,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                
                // BTC Value equivalences
                Text(
                    text = "≈ ${"%.6f".format(wallet.bitcoinBalance)} BTC",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SoftBorderColor, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Credits statistics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("System Credits", color = DarkGreyText, fontSize = 11.sp)
                        Text("${wallet.resourceCredits} COMP", color = CyberCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Simulated Token: WPX", color = DarkGreyText, fontSize = 11.sp)
                        Text("${wallet.tokenBalances["WPX"] ?: 0} WPX", color = Purple80, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trigger faucets / topups so users can buy marketplace templates easily
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.claimFaucet(150000L, "SATS") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262010)),
                border = BorderStroke(1.dp, BitcoinGold),
                modifier = Modifier
                    .weight(1f)
                    .testTag("faucet_claim_sats"),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CurrencyBitcoin, contentDescription = "Sats Faucet", tint = BitcoinGold, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Fund 150K Sat", color = BitcoinGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.claimFaucet(500L, "CREDITS") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10262F)),
                border = BorderStroke(1.dp, CyberCyan),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = "Credits Faucet", tint = CyberCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Fund Credits", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transaction Ledger lists
        Text("Transaction Ledger Ledger", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (txs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No ledger records tracked. Execute storefront checkouts to record transactions.", color = DarkGreyText, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(txs) { tx ->
                    TransactionItemRow(tx = tx)
                }
            }
        }
    }
}

@Composable
fun TransactionItemRow(tx: com.example.webport.database.DBTransaction) {
    val isDebit = tx.type in listOf("PAY_FOR_HOST", "MARKETPLACE_BUY")
    val dateText = remember(tx.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(tx.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateAccentDark)
            .border(1.dp, SoftBorderColor, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDebit) Icons.Default.VerticalAlignBottom else Icons.Default.VerticalAlignTop,
            contentDescription = if (isDebit) "Debit Out" else "Credit Faucet",
            tint = if (isDebit) BroadcastOrange else MatrixGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(tx.memo, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Ledger ID: #${tx.id} | Timestamp: $dateText", color = DarkGreyText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }

        Text(
            text = "${if (isDebit) "-" else "+"}${tx.amount} ${tx.currency}",
            color = if (isDebit) BroadcastOrange else MatrixGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ==========================================
// 4. Tab: Peers Swarm & DHT P2P Directory
// ==========================================

@Composable
fun PeersTabScreen(viewModel: WebPortViewModel) {
    val peerList by viewModel.peers.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Active P2P Host Node Tracker Finder", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Your node replicates cryptographically signed static packages with active seeds.", color = DarkGreyText, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(peerList) { peer ->
                PeerSwarmCardRow(peer = peer)
            }
        }
    }
}

@Composable
fun PeerSwarmCardRow(peer: DBPeer) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateAccentDark),
        border = BorderStroke(1.dp, SoftBorderColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (peer.isActive) MatrixGreen else Color.Gray)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(peer.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(peer.address, color = DarkGreyText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("${peer.latencyMs} ms", color = if (peer.latencyMs < 100) MatrixGreen else BroadcastOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("Reputation: ${"%.2f".format(peer.reputation)}", color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("Replicating: ${peer.replicatedSitesCount} sites", color = CyberCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ==========================================
// 5. Tab: Marketplace
// ==========================================

@Composable
fun MarketplaceTabScreen(viewModel: WebPortViewModel) {
    val catalog by viewModel.marketItems.collectAsState()
    val wallet by viewModel.walletState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Web3 Dashboard Hub Marketplace", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Install sovereign themes & plugins automatically into your browser host.", color = DarkGreyText, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(catalog) { item ->
                MarketCatalogItemCard(
                    item = item,
                    canAfford = if (item.currency == "SATS") wallet.lightningSats >= item.cost else wallet.resourceCredits >= item.cost,
                    onBuyClick = { viewModel.purchaseMarketplaceItem(item.id) }
                )
            }
        }
    }
}

@Composable
fun MarketCatalogItemCard(
    item: com.example.webport.database.DBMarket,
    canAfford: Boolean,
    onBuyClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateAccentDark),
        border = BorderStroke(1.dp, if (item.purchased) MatrixGreen else SoftBorderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFF5722).copy(0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(item.category, color = BroadcastOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                if (item.purchased) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF14241B))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("INSTALLED ✅", color = MatrixGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    Text(
                        text = "${item.cost} ${item.currency}",
                        color = if (item.currency == "SATS") BitcoinGold else CyberCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(item.description, color = Color.LightGray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(12.dp))

            if (!item.purchased) {
                Button(
                    onClick = onBuyClick,
                    enabled = canAfford,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (item.currency == "SATS") BitcoinGold else CyberCyan,
                        disabledContainerColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("buy_item_${item.id}"),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (canAfford) "Deploy & Purchase Template" else "Insufficient Funds Faucet available",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            } else {
                Text(
                    text = "This item has been cryptographically published into your host list. Resolve it directly above!",
                    color = MatrixGreen,
                    fontSize = 10.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ==========================================
// 6. Tab: Diagnostics & Logs Terminal
// ==========================================

@Composable
fun DiagnosticsTabScreen(viewModel: WebPortViewModel) {
    val logList by viewModel.logs.collectAsState()
    val activeIdentity by viewModel.activeIdentity.collectAsState()

    var usernameInput by remember { mutableStateOf("") }
    var domainInput by remember { mutableStateOf("") }

    // Init inputs
    LaunchedEffect(activeIdentity) {
        activeIdentity?.let {
            usernameInput = it.nickname
            domainInput = it.addressName
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Mapped Address and Key management
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateAccentDark),
            border = BorderStroke(1.dp, SoftBorderColor)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Sovereign Node Protocol Keys", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text("Local Alias Nickname", color = DarkGreyText) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        label = { Text("Decentral Address web://", color = DarkGreyText) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.updateIdentityNickname(usernameInput, domainInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Regenerate Cryptographic Identifiers String", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Real-Time Broadcasting Logs Console", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            IconButton(
                onClick = { viewModel.clearLogHistory() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF2E1C1A))
                    .size(32.dp)
            ) {
                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear Console", tint = Color.Red, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Simulated Command Terminal Output
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF04060A))
                .border(1.dp, Color(0xFF132030), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            if (logList.isEmpty()) {
                Text("Waiting for network signals...", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logList) { log ->
                        TerminalLogLine(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalLogLine(log: DBLog) {
    val timestampText = remember(log.timestamp) {
        SimpleDateFormat("HH:mm:ss.S", Locale.getDefault()).format(Date(log.timestamp))
    }
    
    val color = when (log.level) {
        "ERROR" -> Color.Red
        "WARN" -> BroadcastOrange
        "PAYMENT" -> BitcoinGold
        "P2P" -> CyberCyan
        else -> MatrixGreen
    }

    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "[$timestampText] ",
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "${log.level} ",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = log.message,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// --- Checkout Payment Approval Alert Dialog ---

@Composable
fun Web3PaymentAuthorizationDialog(
    request: WebPortViewModel.PaymentRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = "Security Alert", tint = BitcoinGold, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Web3 Wallet Payment Authorization Required", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "A webpage layout at address ${request.siteId} is triggering a native browser payment transaction.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateAccentDark),
                    border = BorderStroke(1.dp, SoftBorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Item / Digital Hook: ${request.itemId}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Transaction Amount: ${request.cost} Satoshis (SATS)", color = BitcoinGold, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Confirming transfers digital credits out of your browser-integrated Lightning vault.", color = DarkGreyText, fontSize = 10.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = BitcoinGold),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.testTag("modal_pay_confirm_btn")
            ) {
                Text("Approve and Sign Transaction", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("modal_pay_cancel_btn")
            ) {
                Text("Decline Request", color = Color.LightGray, fontSize = 12.sp)
            }
        },
        containerColor = SlateAccentDark,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("checkout_modal_parent")
    )
}
