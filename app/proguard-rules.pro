# Add project specific ProGuard rules here.
# Keep Room entities/DAOs (annotation-based, generally safe by default, but explicit is safer).
-keep class com.example.licenseexpiry.data.** { *; }

# Retrofit / Gson - keep model classes used for JSON (de)serialization.
-keep class com.example.licenseexpiry.network.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
