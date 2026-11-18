# kotlinx.serialization: keep generated serializers for our @Serializable models.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class app.parallax.opencode.**$$serializer { *; }
-keepclassmembers class app.parallax.opencode.** { *** Companion; }
-keepclasseswithmembers class app.parallax.opencode.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**

# Google code scanner (ML Kit via Play services) uses reflection / dynamite modules; R8 stripping
# them makes startScan() fail with an NPE in release builds.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.dynamite.** { *; }
-dontwarn com.google.mlkit.**
