# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep our core models for serialization, state preservation, and diagnostics
-keep class com.example.core.model.** { *; }
-keep class com.example.usb.** { *; }
-keep class com.example.driver.** { *; }
-keep class com.example.raster.** { *; }
-keep class com.example.diagnostics.** { *; }

# Keep PrintService and Application
-keep class com.example.LbpOtgApplication { *; }
-keep class com.example.service.LbpPrintService { *; }

# Keep line numbers for accurate diagnostics stack traces
-keepattributes SourceFile,LineNumberTable
