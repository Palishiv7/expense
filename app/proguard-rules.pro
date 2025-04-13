# ======= MONEYPULSE SECURITY-ENHANCED PROGUARD RULES =======
# These rules ensure maximum security and code protection

# ---------- General optimization and obfuscation settings ----------
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-keepattributes *Annotation*,EnclosingMethod,Signature,Exceptions,InnerClasses

# Obfuscation settings - aggressive mode
-obfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt
-classobfuscationdictionary proguard-dictionary.txt
-repackageclasses 'com.moneypulse.app.obf'
-allowaccessmodification

# ---------- Keep app-specific code structure ----------
# Keep Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View
-keep public class * extends androidx.fragment.app.Fragment

# Keep model classes - we use Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    static final android.os.Parcelable$Creator *;
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---------- Database and encryption security ----------
# Keep Room database classes
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep SQLCipher classes
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Keep encryption related classes
-keep class javax.crypto.** { *; }
-keep class javax.crypto.spec.** { *; }
-keep class java.security.** { *; }
-keep class java.security.cert.** { *; }

# Keep AndroidKeystore related classes
-keep class android.security.keystore.** { *; }

# ---------- Enhanced security: Strip logging in release builds ----------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ---------- Dependency-specific rules ----------
# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class com.moneypulse.app.di.** { *; }

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Encrypted SharedPreferences
-keep class androidx.security.crypto.** { *; }

# ---------- Security features: Hide class names ----------
# Hide sensitive class names that might leak security info
-keep,allowshrinking,allowoptimization class com.moneypulse.app.util.SecurityHelper
-keep,allowshrinking,allowoptimization class com.moneypulse.app.util.PreferenceHelper
-keep,allowshrinking,allowoptimization class com.moneypulse.app.data.local.MoneyPulseDatabase

# ---------- Security warning ----------
# WARNING: Do not modify these rules unless you understand the security implications
# These rules are designed to maximize security and minimize data leakage 