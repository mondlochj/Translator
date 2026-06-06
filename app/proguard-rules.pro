# ONNX Runtime — keep all ORT classes and JNI bridge intact
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep accelerator classes referenced by name in benchmark cache
-keep class com.arosys.meetingassistant.accelerator.** { *; }

# Gson — keep data classes used for JSON serialization
-keep class com.arosys.meetingassistant.accelerator.BenchmarkResult { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
