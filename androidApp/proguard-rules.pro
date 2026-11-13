# R8 rules for the release build. Libraries (Compose, CameraX, WorkManager, kotlinx.serialization)
# ship most of their own rules; these cover what remains.

# kotlinx.serialization: keep generated serializers of our @Serializable classes.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class id.tirtapatriot.activitycamera.** {
    *** Companion;
    static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class id.tirtapatriot.activitycamera.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class id.tirtapatriot.activitycamera.**$$serializer { *; }

# WorkManager instantiates the sync worker by class name.
-keep class id.tirtapatriot.activitycamera.sync.SyncWorker { <init>(...); }

# Ktor client (OkHttp/Android engine) references optional JVM-only classes.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn io.ktor.util.debug.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# zxing (QR scanner) is used directly; nothing reflective, but keep warnings quiet.
-dontwarn com.google.zxing.**
