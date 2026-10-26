# GlucoNote R8 keep rules for startup CompositionLocals
-keepattributes *Annotation*
-keep class com.gluconote.** { *; }
-keep class androidx.lifecycle.compose.** { *; }
-keepclassmembers class androidx.lifecycle.compose.** { *; }
-dontwarn androidx.**
