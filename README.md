# aweauto

Android Auto で YouTube / TVer を Google TV 風の UI で見るための個人用アプリ。

## 仕組み

- Car App Library の **NAVIGATION** カテゴリで地図用 Surface を貰い、`VirtualDisplay` + `Presentation` で Compose の画面を丸ごと描画する (`car/`)
- 車側のタッチは「タップ座標」と「スクロール量」でしか来ないので、`TouchInjector` で MotionEvent に組み立て直している
- YouTube / TVer は WebView で開き、設定で ON のときだけ `assets/css/*.css` と `assets/js/*.js` を注入する (`web/SiteTweaks.kt`)

## ビルドとインストール

Play ストア外のナビアプリは Android Auto に隠されるので、インストール元を Play ストアにして入れる。

```bash
./gradlew :app:assembleDebug
adb install -r -i com.android.vending app/build/outputs/apk/debug/app-debug.apk
```

Android Auto の設定 → バージョンを 10 回タップして開発者モード → デベロッパー設定で「提供元不明のアプリ」を ON。

## 車の画面に送る

スマホの YouTube / TVer アプリで「共有 → 車の画面で開く」。adb からは:

```bash
adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "https://youtu.be/VIDEO_ID" \
  -n com.h1rose.aweauto/.ShareActivity
```

## CSS の調整

debug ビルドは WebView のリモートデバッグが有効なので、`chrome://inspect` から実際の DOM を見ながら `assets/css/` を編集する。
Mac 上での動作確認は Android SDK の Desktop Head Unit (`extras;google;auto`) を使う。
