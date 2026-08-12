# ProGuard / R8 rules for LocalStream release build

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Retrofit + kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.Serializable <methods>;
}
-keep class com.localstream.app.data.remote.** { *; }
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
