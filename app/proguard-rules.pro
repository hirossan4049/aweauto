# release ビルドの R8 (不要なコードの削除・難読化) で消されては困るもの

# Shizuku の UserService はクラス名から作られる (Context を受け取るコンストラクタも使う)
-keep class com.h1rose.aweauto.map.NativeMapInputUserService { <init>(...); }

# WebView から JS で呼ばれるメソッド (addJavascriptInterface)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 落ちたときのスタックトレースで行番号を追えるようにする
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
