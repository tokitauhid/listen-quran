# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android-optimize.txt.

# Keep Moshi adapters
-keep class com.listenquran.data.remote.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Keep Room entities
-keep class com.listenquran.data.local.** { *; }
