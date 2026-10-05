# ParLauncher aggressive R8 rules

# Keep launcher activities and receivers
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Preserve standard serialization / parcelable if any
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Strip all debug logging
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
