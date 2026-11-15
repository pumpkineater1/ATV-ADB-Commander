package com.atv.adbcommander.util

import com.atv.adbcommander.model.AppSafetyLevel

data class AppCatalogEntry(
    val friendlyName: String,
    val category: String,
    val purpose: String,
    val safetyLevel: AppSafetyLevel,
    val safetyNote: String,
    val brandColorHex: Long = 0xFF004F50
)

object AppCatalog {

    private val catalog = mapOf(
        // Core OS & Critical System UI
        "android" to AppCatalogEntry(
            friendlyName = "Android OS Core",
            category = "Core System",
            purpose = "The foundational Android operating system kernel and framework runtime that manages all hardware, memory, and services.",
            safetyLevel = AppSafetyLevel.CRITICAL,
            safetyNote = "DO NOT DISABLE! Disabling core Android OS will cause an immediate unrecoverable bootloop or brick.",
            brandColorHex = 0xFF3DDC84
        ),
        "com.android.systemui" to AppCatalogEntry(
            friendlyName = "System UI",
            category = "Core System",
            purpose = "Manages the on-screen navigation, volume overlay HUD, status bar, notifications, and visual dialogs.",
            safetyLevel = AppSafetyLevel.CRITICAL,
            safetyNote = "DO NOT DISABLE! Disabling System UI will cause black screen crashes and remote input failure.",
            brandColorHex = 0xFF3DDC84
        ),
        "com.google.android.packageinstaller" to AppCatalogEntry(
            friendlyName = "Package Installer",
            category = "Core System",
            purpose = "Installs, parses, and uninstalls APK files and app updates.",
            safetyLevel = AppSafetyLevel.CRITICAL,
            safetyNote = "Required for sideloading and installing new apps.",
            brandColorHex = 0xFF4285F4
        ),
        "com.android.packageinstaller" to AppCatalogEntry(
            friendlyName = "Package Installer",
            category = "Core System",
            purpose = "Standard Android package installation service.",
            safetyLevel = AppSafetyLevel.CRITICAL,
            safetyNote = "Required for sideloading and app management.",
            brandColorHex = 0xFF4285F4
        ),
        "com.google.android.gms" to AppCatalogEntry(
            friendlyName = "Google Play Services",
            category = "Core Services",
            purpose = "Provides Google account authentication, cloud sync, in-app billing, Cast protocol, and background notifications.",
            safetyLevel = AppSafetyLevel.CAUTION,
            safetyNote = "Disabling may break Google login in YouTube, Play Store, and Chromecast.",
            brandColorHex = 0xFF4285F4
        ),
        "com.google.android.gsf" to AppCatalogEntry(
            friendlyName = "Google Services Framework",
            category = "Core Services",
            purpose = "Connects TV to Google cloud messaging and push notifications.",
            safetyLevel = AppSafetyLevel.CAUTION,
            safetyNote = "Used by Google apps for background sync.",
            brandColorHex = 0xFF4285F4
        ),
        "com.android.vending" to AppCatalogEntry(
            friendlyName = "Google Play Store",
            category = "App Store",
            purpose = "Official store for searching, downloading, and automatically updating TV apps.",
            safetyLevel = AppSafetyLevel.CAUTION,
            safetyNote = "Disabling prevents Play Store downloads and automatic app updates.",
            brandColorHex = 0xFF00C3FF
        ),
        "com.android.bluetooth" to AppCatalogEntry(
            friendlyName = "Bluetooth Service",
            category = "Hardware & Wireless",
            purpose = "Powers wireless Bluetooth connections for your voice remote control, gamepads, and soundbars.",
            safetyLevel = AppSafetyLevel.CRITICAL,
            safetyNote = "DO NOT DISABLE! Disabling will disconnect your Bluetooth TV remote control.",
            brandColorHex = 0xFF0078D7
        ),
        "com.google.android.inputmethod.latin" to AppCatalogEntry(
            friendlyName = "Gboard (TV Keyboard)",
            category = "Input & Accessibility",
            purpose = "On-screen virtual keyboard for typing text, passwords, and search queries.",
            safetyLevel = AppSafetyLevel.CAUTION,
            safetyNote = "Ensure an alternate keyboard is active before disabling.",
            brandColorHex = 0xFF4285F4
        ),
        "com.google.android.tts" to AppCatalogEntry(
            friendlyName = "Speech Services by Google",
            category = "Input & Voice",
            purpose = "Text-to-speech engine for screen readers and voice synthesis.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Safe to disable if you do not use TalkBack or accessibility voice reading.",
            brandColorHex = 0xFFEA4335
        ),

        // Launchers
        "com.google.android.apps.tv.launcherx" to AppCatalogEntry(
            friendlyName = "Google TV Home",
            category = "Launcher",
            purpose = "The standard Google TV home screen launcher featuring streaming rows, continue watching, and sponsored recommendations.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Safe to disable when using custom launchers like Projectivy or FLauncher.",
            brandColorHex = 0xFF4285F4
        ),
        "com.google.android.tvlauncher" to AppCatalogEntry(
            friendlyName = "Android TV Home",
            category = "Launcher",
            purpose = "Default leanback launcher for stock Android TV devices.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Can be safely disabled if replacing with a custom ad-free launcher.",
            brandColorHex = 0xFF4285F4
        ),
        "com.amazon.tv.launcher" to AppCatalogEntry(
            friendlyName = "Fire TV Home",
            category = "Launcher",
            purpose = "Amazon Fire TV's proprietary home launcher with Amazon Prime video rows and sponsored ads.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Can be disabled to free memory and switch to clean launchers like Projectivy.",
            brandColorHex = 0xFFFF9900
        ),
        "com.spocky.projengmenu" to AppCatalogEntry(
            friendlyName = "Projectivy Launcher",
            category = "Launcher",
            purpose = "Clean, ultra-fast, premium ad-free custom TV launcher with custom shortcuts, parental controls, and wallpaper engines.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Highly recommended alternative to bloat-heavy stock TV launchers.",
            brandColorHex = 0xFF9047FF
        ),
        "me.efesser.flauncher" to AppCatalogEntry(
            friendlyName = "FLauncher",
            category = "Launcher",
            purpose = "Open-source, lightweight, minimalist Android TV launcher with zero ads.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Great lightweight launcher for low-RAM TVs.",
            brandColorHex = 0xFF10B981
        ),
        "tv.dumbtv.home" to AppCatalogEntry(
            friendlyName = "Dumb TV Launcher",
            category = "Launcher",
            purpose = "Ultra-minimalist home screen designed to make smart TVs behave like fast dumb displays.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Extremely lightweight and fast.",
            brandColorHex = 0xFF6366F1
        ),

        // Streaming & Media Apps
        "com.google.android.youtube.tv" to AppCatalogEntry(
            friendlyName = "YouTube",
            category = "Streaming & Media",
            purpose = "Stream YouTube videos, 4K clips, music, and subscriptions on the big screen.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Official YouTube client.",
            brandColorHex = 0xFFFF0000
        ),
        "com.teamsmart.videomanager.tv" to AppCatalogEntry(
            friendlyName = "SmartTube",
            category = "Streaming & Media",
            purpose = "Ad-free, privacy-oriented open-source YouTube client for Android TV with SponsorBlock.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Popular third-party YouTube alternative.",
            brandColorHex = 0xFF0F766E
        ),
        "com.netflix.ninja" to AppCatalogEntry(
            friendlyName = "Netflix",
            category = "Streaming & Media",
            purpose = "Stream award-winning movies, TV series, anime, and Netflix originals in 4K HDR.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Safe to disable if you do not have a Netflix subscription.",
            brandColorHex = 0xFFE50914
        ),
        "com.amazon.amazonvideo.livingroom" to AppCatalogEntry(
            friendlyName = "Prime Video",
            category = "Streaming & Media",
            purpose = "Watch Amazon Prime Video movies, TV series, live sports, and rentals.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Official Amazon video streaming app.",
            brandColorHex = 0xFF00A8E1
        ),
        "in.startv.hotstar" to AppCatalogEntry(
            friendlyName = "JioHotstar",
            category = "Streaming & Media",
            purpose = "Disney+ movies, live cricket, HBO originals, and Indian entertainment.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Major Indian streaming platform.",
            brandColorHex = 0xFF0C2053
        ),
        "com.disney.disneyplus" to AppCatalogEntry(
            friendlyName = "Disney+",
            category = "Streaming & Media",
            purpose = "Stream Disney, Pixar, Marvel, Star Wars, and National Geographic content.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Official Disney streaming app.",
            brandColorHex = 0xFF113CCF
        ),
        "com.spotify.tv.android" to AppCatalogEntry(
            friendlyName = "Spotify",
            category = "Music & Podcasts",
            purpose = "Stream millions of songs, playlists, podcasts, and background music on your TV.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Great for background music playback.",
            brandColorHex = 0xFF1DB954
        ),
        "org.videolan.vlc" to AppCatalogEntry(
            friendlyName = "VLC Media Player",
            category = "Media Player",
            purpose = "Universal media player that plays any video codec (MKV, MP4, AVI, ISO, network streams).",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Highly versatile open-source video player.",
            brandColorHex = 0xFFFF8800
        ),
        "org.xbmc.kodi" to AppCatalogEntry(
            friendlyName = "Kodi",
            category = "Media Center",
            purpose = "Open-source entertainment hub for organizing local media, network shares, and add-ons.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Comprehensive home theater media center.",
            brandColorHex = 0xFF17B2E7
        ),
        "com.plexapp.android" to AppCatalogEntry(
            friendlyName = "Plex",
            category = "Media Center",
            purpose = "Stream movies, shows, and personal media collections from your personal Plex server.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Client for personal Plex home servers.",
            brandColorHex = 0xFFE5A00D
        ),
        "org.jellyfin.androidtv" to AppCatalogEntry(
            friendlyName = "Jellyfin",
            category = "Media Center",
            purpose = "100% free and open-source media system that connects to personal Jellyfin servers.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Privacy-friendly alternative to Plex.",
            brandColorHex = 0xFF9047FF
        ),

        // Bloatware & Telemetry (Safe to Debloat)
        "com.google.android.tvrecommendations" to AppCatalogEntry(
            friendlyName = "TV Recommendations",
            category = "Telemetry & Ads",
            purpose = "Injects promotional and sponsored show recommendations into the default launcher rows.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Disabling removes sponsored rows and noticeably speeds up older TVs.",
            brandColorHex = 0xFFEA4335
        ),
        "com.amazon.device.sale.service" to AppCatalogEntry(
            friendlyName = "Amazon Sale Service",
            category = "Telemetry & Ads",
            purpose = "Background service for Amazon flash sales, deal banners, and promotion tracking.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Safe to disable. Frees background RAM and reduces Wi-Fi polling.",
            brandColorHex = 0xFFFF9900
        ),
        "com.amazon.device.software.ota" to AppCatalogEntry(
            friendlyName = "Amazon System Updates (OTA)",
            category = "System Updates",
            purpose = "Downloads and applies forced over-the-air firmware updates from Amazon.",
            safetyLevel = AppSafetyLevel.CAUTION,
            safetyNote = "Some users disable to block automatic Amazon updates that restrict custom launchers.",
            brandColorHex = 0xFFFF9900
        ),
        "com.amazon.avod" to AppCatalogEntry(
            friendlyName = "Amazon Video on Demand",
            category = "Streaming",
            purpose = "Background components for Amazon Video catalog and trailer preloading.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Safe to disable if you do not use Amazon Video rentals.",
            brandColorHex = 0xFF00A8E1
        ),
        "com.xiaomi.mitv.upgrade" to AppCatalogEntry(
            friendlyName = "Xiaomi TV Upgrade",
            category = "OEM Updates",
            purpose = "Checks and delivers Xiaomi PatchWall updates and promotions.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Safe to disable to prevent aggressive PatchWall ads.",
            brandColorHex = 0xFFFF6700
        ),
        "com.phlox.tvwebbrowser" to AppCatalogEntry(
            friendlyName = "TV Bro",
            category = "Web Browser",
            purpose = "Open-source web browser designed specifically for Android TV remotes with ad-blocker.",
            safetyLevel = AppSafetyLevel.SAFE,
            safetyNote = "Best web browser for navigating websites on TV remotes.",
            brandColorHex = 0xFF3B82F6
        )
    )

    fun getEntry(packageName: String): AppCatalogEntry {
        catalog[packageName]?.let { return it }

        // Smart heuristics for unknown packages
        val lower = packageName.lowercase()
        val derivedName = generateFriendlyName(packageName)

        return when {
            lower.contains("systemui") || lower.contains("inputmethod") || lower.contains("bluetooth") -> {
                AppCatalogEntry(
                    friendlyName = derivedName,
                    category = "System Component",
                    purpose = "Core hardware or interface component for Android TV.",
                    safetyLevel = AppSafetyLevel.CAUTION,
                    safetyNote = "Caution: Disabling may affect TV remote, audio, or input.",
                    brandColorHex = 0xFF64748B
                )
            }
            lower.contains("telemetry") || lower.contains("analytics") || lower.contains("advert") || lower.contains("recommendation") -> {
                AppCatalogEntry(
                    friendlyName = derivedName,
                    category = "Telemetry & Ads",
                    purpose = "Background tracking or sponsored content suggestions.",
                    safetyLevel = AppSafetyLevel.SAFE,
                    safetyNote = "Safe to disable to speed up TV and reduce background telemetry.",
                    brandColorHex = 0xFFEF4444
                )
            }
            lower.contains("launcher") || lower.contains("home") -> {
                AppCatalogEntry(
                    friendlyName = derivedName,
                    category = "Home Launcher",
                    purpose = "Home screen interface for launching TV apps and channels.",
                    safetyLevel = AppSafetyLevel.SAFE,
                    safetyNote = "Can be switched or configured in the Tweaks tab.",
                    brandColorHex = 0xFF8B5CF6
                )
            }
            else -> {
                AppCatalogEntry(
                    friendlyName = derivedName,
                    category = "Application",
                    purpose = "Installed TV application or system service ($packageName).",
                    safetyLevel = AppSafetyLevel.SAFE,
                    safetyNote = "Check if you actively use this app before disabling or uninstalling.",
                    brandColorHex = deriveColor(packageName)
                )
            }
        }
    }

    private fun generateFriendlyName(packageName: String): String {
        val parts = packageName.split(".")
        val raw = if (parts.size >= 2) parts.last() else packageName
        return raw.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun deriveColor(text: String): Long {
        val colors = listOf(
            0xFF004F50, 0xFF1E3A8A, 0xFF4C1D95, 0xFF14532D, 0xFF701A75,
            0xFF7C2D12, 0xFF0E7490, 0xFF155E75, 0xFF312E81, 0xFF065F46
        )
        val hash = kotlin.math.abs(text.hashCode())
        return colors[hash % colors.size]
    }
}
