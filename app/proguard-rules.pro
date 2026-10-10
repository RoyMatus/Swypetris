# Add project specific ProGuard rules here.
# Preserve the existing game/state/update owners and RuStore SDK API/reflective
# implementation. Shrink unused dependency code and resources without renaming
# these integration boundaries in the first optimized distribution release.
-keep class ru.itoltec.swypetris.** { *; }
-keep class ru.rustore.** { *; }
-keep class ru.vk.** { *; }
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
