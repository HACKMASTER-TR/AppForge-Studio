package com.appforge.studio.model

const val DEFAULT_BUILD_SERVICE_URL =
    "https://appforge-studio-production.up.railway.app"

data class ProjectDraft(
    var appName: String = "",
    var packageName: String = "com.example.myapp",
    var sourceMode: SourceMode = SourceMode.LOCAL,
    var sourceLabel: String = "",
    var sourceUri: String? = null,
    var importedFolder: String? = null,
    var startPage: String? = null,
    var webUrl: String = "",
    var versionName: String = "1.0.0",
    var versionCode: Int = 1,
    var autoVersionCode: Boolean = false,
    var buildOutput: String = "both",

    var orientation: String = "unspecified",
    var primaryColor: String = "#6B7CFF",
    var backgroundColor: String = "#07101F",
    var statusBarColor: String = "#07101F",
    var navigationBarColor: String = "#07101F",
    var splashEnabled: Boolean = true,
    var splashText: String = "",
    var iconUri: String? = null,
    var iconName: String = "",

    var signingMode: SigningMode = SigningMode.DEBUG,
    var keystoreUri: String? = null,
    var keystoreName: String = "",
    var keyAlias: String = "",
    var storePassword: String = "",
    var keyPassword: String = "",

    var fileUpload: Boolean = true,
    var downloads: Boolean = true,
    var fullscreen: Boolean = false,
    var notifications: Boolean = false,
    var camera: Boolean = false,
    var location: Boolean = false,
    var offlineCache: Boolean = true,

    var deepLinkEnabled: Boolean = false,
    var deepLinkScheme: String = "https",
    var deepLinkHost: String = "",
    var deepLinkPathPrefix: String = "/",

    var javascriptBridge: Boolean = true,
    var remoteBridgeAllowed: Boolean = false,
    var shareBridge: Boolean = true,
    var clipboardBridge: Boolean = true,
    var vibrationBridge: Boolean = true,
    var mediaPlayerBridge: Boolean = false,
    var qrScanner: Boolean = false,

    var admobEnabled: Boolean = false,
    var admobAppId: String = "",
    var admobBannerUnitId: String = "",
    var admobInterstitialUnitId: String = "",
    var admobRewardedUnitId: String = "",
    var umpConsentEnabled: Boolean = false,

    var billingEnabled: Boolean = false,
    var billingProductIds: String = "",
    var billingSubscriptionIds: String = "",
    var consumableProductIds: String = "",
    var removeAdsProductId: String = "",
    var purchaseVerificationUrl: String = "",

    var firebaseAnalyticsEnabled: Boolean = false,
    var firebaseCrashlyticsEnabled: Boolean = false,
    var firebaseConfigUri: String? = null,
    var firebaseConfigName: String = "",

    var buildServiceUrl: String = DEFAULT_BUILD_SERVICE_URL,
    var buildApiKey: String = ""
)

enum class SourceMode { LOCAL, URL }
enum class SigningMode { DEBUG, CUSTOM }
