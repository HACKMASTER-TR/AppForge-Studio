# v1.8 — Ad Privacy Fixes

Generated AdMob projects now use UMP SDK 4.0.0.

When UMP is enabled:
- consent information is refreshed at launch
- a required form is shown
- ads initialize only if `canRequestAds()` is true
- a consent-update error no longer blindly initializes ads

This avoids the earlier fallback that could start ad requests even after consent information failed to update.
