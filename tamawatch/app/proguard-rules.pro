# Room, WorkManager, Tiles and Complication services are referenced from the
# manifest / reflection; keep their entry points.
-keep class com.tamawatch.tile.** { *; }
-keep class com.tamawatch.complication.** { *; }
-keep class com.tamawatch.background.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepclassmembers class * { @androidx.room.* <methods>; }
-dontwarn org.jetbrains.annotations.**
