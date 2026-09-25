-keep class com.mrzgaming.ezbox.** { *; }
-keep class androidx.** { *; }
-keep class kotlin.** { *; }
-keepattributes Signature
-keepattributes InnerClasses

# RFB protocol - keep all reflection-based classes
-keepclassmembers class com.mrzgaming.ezbox.RfbClient {
    *;
}

# Coroutines
-keepattributes SourceFile,LineNumberTable
-keepclassmembers class * extends kotlinx.coroutines.internal.MainDispatcherFactory {
    *;
}

# VNC client
-keepclassmembers class com.mrzgaming.ezbox.VncActivity {
    *;
}

# Disable obfuscation for AndroidX
-keep class android.** { *; }
-dontwarn android.**
