# ==============================================================================
# Money Manager Production R8 / ProGuard Rules
# ==============================================================================

# Preserve line numbers for stack traces while hiding original source file names
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations

# Keep Room Database, DAOs, and generated implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.moneymanager.app.data.local.** { *; }

# Keep Room Entities & Relation data classes (fields, constructors, getters/setters)
-keep @androidx.room.Entity class * { *; }
-keep class com.moneymanager.app.data.local.entity.** { *; }

# Keep Enum methods required by Room TypeConverters and Kotlin serialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Android entry points (Activity and BroadcastReceiver)
-keep public class com.moneymanager.app.MainActivity
-keep public class com.moneymanager.app.notifications.QuickAddNotificationReceiver

# Strip debug and verbose logging in release builds to prevent logcat leakage
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
