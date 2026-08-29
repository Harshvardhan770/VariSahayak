# VARI Sahayak R8 rules.
#
# supabase-kt ships no consumer ProGuard rules and upstream's sample app disables
# minification entirely (plans/00-api-contract.md §0.11 item 5), so release-build
# behaviour is not covered upstream. These rules exist to close that gap.
#
# If a release build fails with SerializationException or ClassNotFoundException,
# add a keep rule here. Do not disable minification.

# ---- kotlinx.serialization ----
# The runtime ships its own rules, but generated serializers on our own DTOs are
# reached reflectively and are worth keeping explicitly.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Our own serializable DTOs and domain models.
-keep @kotlinx.serialization.Serializable class com.varisahayak.** { *; }
-keep class com.varisahayak.data.remote.dto.** { *; }
-keep class com.varisahayak.domain.model.** { *; }

# ---- supabase-kt / ktor ----
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
# Ktor's OkHttp engine reaches OkHttp reflectively in places.
-dontwarn okhttp3.**
-dontwarn okio.**

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---- Hilt / Dagger ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# ---- WorkManager ----
-keep class * extends androidx.work.ListenableWorker { *; }

# ---- ML Kit barcode ----
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ---- Google Maps ----
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.**

# ---- Firebase ----
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ---- Crashlytics: keep line numbers and source files for readable stack traces ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Coroutines ----
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
