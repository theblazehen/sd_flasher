# Add project specific ProGuard rules here.

# Keep libsu classes
-keep class com.topjohnwu.superuser.** { *; }

# Keep AIDL interfaces
-keep class dev.blazelight.sdflasher.IFlashService { *; }
-keep class dev.blazelight.sdflasher.IFlashCallback { *; }
-keep class dev.blazelight.sdflasher.FlashProgress { *; }

# Keep root service
-keep class dev.blazelight.sdflasher.root.FlashRootService { *; }
