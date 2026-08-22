# CastOptionsProvider is instantiated from AndroidManifest metadata.
-keep class com.app.castall.CastOptionsProvider { *; }

# Google Cast SDK relies on reflection/JSON (de)serialization for its model
# classes (MediaInfo, MediaMetadata, CastDevice, etc). Stripping or renaming
# them under R8/ProGuard causes silent runtime crashes in release builds
# that a debug build would never reveal.
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.cast.framework.** { *; }
-keep class com.google.android.gms.cast.framework.media.** { *; }
-keep class com.google.android.gms.common.images.WebImage { *; }
-dontwarn com.google.android.gms.cast.**

# NanoHTTPD (local video web server) uses reflection for MIME/type handling
# in some code paths; keep the whole library intact to be safe.
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**
