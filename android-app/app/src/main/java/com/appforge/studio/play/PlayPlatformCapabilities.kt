package com.appforge.studio.play

enum class PlayCapabilityState {
    ACTIVE,
    CODE_READY,
    CONSOLE_GATED,
    API_GATED,
    POST_RELEASE_DATA,
    PLANNED
}

data class PlayCapability(
    val id: String,
    val state: PlayCapabilityState
)

/**
 * GOOGLE_PLAY_MASTER_CAPABILITY_REGISTRY_V1
 *
 * This registry is intentionally factual:
 * a capability is never marked ACTIVE merely because a client library exists.
 */
object PlayPlatformCapabilities {

    val all =
        listOf(
            PlayCapability("in_app_updates", PlayCapabilityState.ACTIVE),
            PlayCapability("play_integrity", PlayCapabilityState.ACTIVE),
            PlayCapability("billing", PlayCapabilityState.ACTIVE),
            PlayCapability("android_app_bundle", PlayCapabilityState.ACTIVE),
            PlayCapability("internal_testing_pipeline", PlayCapabilityState.ACTIVE),
            PlayCapability("production_release_pipeline", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("play_oidc_wif", PlayCapabilityState.ACTIVE),
            PlayCapability("release_upload_signing", PlayCapabilityState.ACTIVE),

            PlayCapability("in_app_review", PlayCapabilityState.ACTIVE),
            PlayCapability("install_referrer", PlayCapabilityState.CODE_READY),
            PlayCapability("feature_delivery", PlayCapabilityState.CODE_READY),
            PlayCapability("asset_delivery", PlayCapabilityState.CODE_READY),

            PlayCapability("closed_testing", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("open_testing", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("staged_rollout", PlayCapabilityState.API_GATED),
            PlayCapability("rollout_halt", PlayCapabilityState.API_GATED),
            PlayCapability("rollout_resume", PlayCapabilityState.API_GATED),
            PlayCapability("rollout_complete", PlayCapabilityState.API_GATED),
            PlayCapability("in_app_update_priority", PlayCapabilityState.API_GATED),
            PlayCapability("localized_release_notes", PlayCapabilityState.API_GATED),
            PlayCapability("country_targeting", PlayCapabilityState.API_GATED),
            PlayCapability("managed_publishing", PlayCapabilityState.CONSOLE_GATED),

            PlayCapability("play_app_signing", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("automatic_protection", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("pre_launch_report", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("android_vitals", PlayCapabilityState.POST_RELEASE_DATA),
            PlayCapability("developer_reporting_api", PlayCapabilityState.API_GATED),
            PlayCapability("app_recovery", PlayCapabilityState.API_GATED),

            PlayCapability("custom_store_listings", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("store_listing_experiments", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("store_conversion_analytics", PlayCapabilityState.POST_RELEASE_DATA),
            PlayCapability("reach_and_devices", PlayCapabilityState.POST_RELEASE_DATA),
            PlayCapability("pre_registration", PlayCapabilityState.CONSOLE_GATED),

            PlayCapability("crash_rate", PlayCapabilityState.POST_RELEASE_DATA),
            PlayCapability("anr_rate", PlayCapabilityState.POST_RELEASE_DATA),
            PlayCapability("slow_startup", PlayCapabilityState.POST_RELEASE_DATA),
            PlayCapability("excessive_wakeup", PlayCapabilityState.POST_RELEASE_DATA),
            PlayCapability("stuck_background_wakelock", PlayCapabilityState.POST_RELEASE_DATA),

            PlayCapability("integrity_app_access_risk", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("integrity_play_protect", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("integrity_recent_device_activity", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("integrity_device_attributes", PlayCapabilityState.CONSOLE_GATED),
            PlayCapability("integrity_device_recall", PlayCapabilityState.CONSOLE_GATED),

            PlayCapability("tester_management_api", PlayCapabilityState.API_GATED),
            PlayCapability("track_management_api", PlayCapabilityState.API_GATED),
            PlayCapability("internal_app_sharing", PlayCapabilityState.API_GATED),

            PlayCapability("second_brain_play_health", PlayCapabilityState.PLANNED),
            PlayCapability("release_health_gate", PlayCapabilityState.PLANNED),
            PlayCapability("automatic_rollout_progression", PlayCapabilityState.PLANNED),
            PlayCapability("automatic_rollout_halt", PlayCapabilityState.PLANNED)
        )

    fun state(
        id: String
    ): PlayCapabilityState? =
        all.firstOrNull {
            it.id == id
        }?.state
}
