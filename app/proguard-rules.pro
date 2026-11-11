# kotlinx.serialization: keep generated serializers for the asset/settings models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.abidnafi.pocketdeck.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.abidnafi.pocketdeck.**$$serializer { *; }
