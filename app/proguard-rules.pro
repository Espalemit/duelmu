# kotlinx.serialization: aturan konsumen sudah disertakan library. Pertahankan model protokol/asset.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class id.eduduel.app.**$$serializer { *; }
-keepclassmembers class id.eduduel.app.** {
    *** Companion;
}
-keepclasseswithmembers class id.eduduel.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
