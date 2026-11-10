# Proguard / R8 rules for McMurdo Map

# Preserve WebView JavaScript interface methods across minification
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
