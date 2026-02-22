# Project-specific R8/ProGuard rules for MedLog Android app
# These supplement the consumer rules bundled with each library's AAR.

# ── Keep line numbers for crash stack traces ──────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Annotations (required by many frameworks) ─────────────────────────────────
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# ── Room: preserve entity field names (used as SQLite column names) ────────────
# Room's consumer rules handle DAOs/generated code, but entity fields without
# explicit @ColumnInfo(name=...) must be preserved to match generated SQL.
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
}
-keepclassmembers @androidx.room.Embedded class * {
    <fields>;
}

# ── kotlinx.serialization (Navigation type-safe routes) ──────────────────────
# The Kotlin Serialization plugin adds its own consumer rules for @Serializable,
# but explicitly keeping them here documents intent and provides a safety net.
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class kotlinx.serialization.json.** { *; }

# ── Hilt / Dagger: entry points must be kept ─────────────────────────────────
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @javax.inject.Singleton class * { *; }

# ── WorkManager: Worker subclasses need public constructor ────────────────────
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Glance AppWidget: widget receivers and widgets must be found by the system ─
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends android.appwidget.AppWidgetProvider { *; }

# ── BroadcastReceivers used for alarms/boot ───────────────────────────────────
-keep class * extends android.content.BroadcastReceiver { *; }

# ── Kotlin coroutines ────────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.debug.**
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Suppress warnings for optional dependencies ───────────────────────────────
-dontwarn org.slf4j.**
-dontwarn java.lang.instrument.**
