<div align="center">

# aweauto

**Android Auto で YouTube と TVer を、Google TV のような画面で見るアプリ**

root 化も Android Auto の改造も要りません。アプリを手動でインストールできるスマホなら動きます。

[English](README.md) | 日本語

![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Android Auto](https://img.shields.io/badge/Android%20Auto-Car%20App%20Library%201.4-1A73E8)

<img src="docs/screenshots/home.png" width="720" alt="ホーム画面">

</div>

> [!WARNING]
> 同乗者向け、運転手は**停車中のみ**を想定したアプリです。運転中の動画視聴は危険で、道路交通法違反にもなります。

## 特長

- 📺 **Google TV 風のホーム**：大きなバナー、横スクロールの棚、タブ、時計、再生履歴。車の画面でタッチ操作しやすいように作っています。
- ▶️ **YouTube と TVer を車の画面向けに調整**：サイトごとに CSS / JS を差し込み、次のように見た目と動きを変えます。サイトごとに設定で OFF にできます。
  - ダークテーマ
  - 検索結果を3列のグリッドで表示
  - プレーヤーを画面いっぱいに表示
  - ミュートの自動解除
  - TVer の再生前アンケートに自動で回答
  - ショート動画、アプリへの誘導バナー、下のタブバーを非表示
- 🕶️ **再生中は動画だけを表示**：再生中は左の操作レールを隠します。左端の小さなつまみを押すと、また出てきます。
- ⏳ **読み込み中の画面**：再生が始まるまでは、WebView の灰色の枠の代わりに、サムネイルと読み込み表示を出します。
- 📲 **スマホから車の画面に送る**
  - YouTube / TVer アプリの「共有 → 車の画面で開く」
  - 公式 YouTube アプリの**キャストボタン**。「テレビコードでリンク」（Lounge API）を使うので、同じ Wi-Fi にいなくても、モバイル回線のままキャストできます。
- 🛡️ **広告ブロック**
  - AdGuard DNS フィルタ、EasyList、AdGuard 日本語フィルタから、ドメイン単位のルールを使います。リストは1日1回自動で更新します。
  - YouTube の動画広告は、プレーヤーに渡る設定から広告の予定を取り除いて消します。
- 📶 **電波が弱いとき向けの設定**：画質の上限（自動 / 720p / 480p / 360p）を選べます。先読みを増やす設定もありますが、実験的な機能です。

## スクリーンショット

| | |
|:-:|:-:|
| <img src="docs/screenshots/home.png" alt="ホーム"> ホーム | <img src="docs/screenshots/library.png" alt="ライブラリ"> ライブラリ（再生履歴） |
| <img src="docs/screenshots/youtube-search.png" alt="YouTube 検索"> YouTube 検索（3列グリッド） | <img src="docs/screenshots/youtube-player.png" alt="YouTube 再生"> YouTube（再生中は動画だけ表示） |
| <img src="docs/screenshots/loading.png" alt="読み込み中"> 読み込み中の画面 | <img src="docs/screenshots/settings.png" alt="設定"> 設定 |
| <img src="docs/screenshots/tver-home.png" alt="TVer ホーム"> TVer（ダークテーマ） | <img src="docs/screenshots/tver-player.png" alt="TVer 再生"> TVer（画面いっぱいに再生） |

<sub>Android Auto の Desktop Head Unit（1280×720）で撮影しました。動画は [ダイアン公式チャンネル](https://www.youtube.com/@daian_youandtube) と TVer のものです。</sub>

## 仕組み

```mermaid
flowchart LR
    subgraph スマホ
        CAS[CarAppService<br/>NAVIGATION カテゴリ] -- Surface --> VD[VirtualDisplay]
        VD --> PR[Presentation<br/>Jetpack Compose の UI]
        PR --> WV[WebView<br/>YouTube / TVer]
        WV -. 注入 .-> JS[CSS / JS<br/>広告ブロック・画質・キャスト連携]
        LR[Lounge 受信] <-- long-poll --> YT[(YouTube Lounge API)]
        LR --> WV
    end
    CAS <== Android Auto ==> HU[車の画面]
    APP[YouTube アプリ] -- テレビコードでリンク --> YT
```

- **車の画面への描画**：Car App Library では、ナビアプリが地図を自前で描くための描画領域（`Surface`）を受け取れます。aweauto はこれを `VirtualDisplay` にして、その上に Compose の `Presentation` を出しています。こうすることで、車の画面に好きな UI を描けます。
- **タッチ操作**：Android Auto からはタップの座標とスクロール量しか届きません。`TouchInjector` がそれを `MotionEvent` に組み立て直して、UI に渡しています。
- **サイトの調整**：CSS / JS は [`app/src/main/assets`](app/src/main/assets) に置いています。`WebViewCompat.addDocumentStartJavaScript` を使い、ページが読み込まれる前に差し込みます。
- **キャスト**：YouTube Lounge（MDX）の受信側の仕組みを Kotlin で実装したものです。受信側の ID を保存し、bind の long-poll をつなぎ続けて、再生状態をスマホに送り返します。

## 使い方

### 必要なもの

- Android 9 以上で、Android Auto が入っているスマホ
- JDK 17 と Android SDK（platform 35）

### ビルドとインストール

Android Auto は、Play ストア以外から入れたナビアプリを一覧に出しません。そのため、インストール元を Play ストアに指定して入れます。

```bash
./gradlew :app:assembleDebug
adb install -r -i com.android.vending app/build/outputs/apk/debug/app-debug.apk
```

続いて、Android Auto 側で次の設定をします。

1. Android Auto の設定で「バージョン」を10回タップして、開発者モードにします。
2. ⋮ → 「デベロッパー向けの設定」→「提供元不明のアプリ」を ON にします。
3. 車につなぎ直すと、ランチャーに **aweauto** が出ます。

### YouTube アプリからキャストする

1. 車の画面で「設定 → スマホからキャスト」を開くと、12桁のテレビコードが出ます。
2. YouTube アプリの「設定 → テレビで見る → テレビコードでリンク」で、そのコードを入力します。
3. これ以降は、キャストボタンに **aweauto (車)** が出ます。

## 開発

- **車なしで動作確認する**：[Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu) を使います。
  1. `sdkmanager "extras;google;auto"` でインストールします。
  2. Android Auto の開発者メニューで「ヘッドユニットサーバーを起動」を押します。
  3. `adb forward tcp:5277 tcp:5277 && ./desktop-head-unit` を実行します。
- **CSS を調整する**：debug ビルドは WebView のリモートデバッグが有効です。`chrome://inspect` で実際の DOM を見ながら `assets/css/*.css` を編集できます。
- **adb から URL を送る**：

  ```bash
  adb shell am start -a android.intent.action.SEND -t text/plain \
    --es android.intent.extra.TEXT "https://youtu.be/VIDEO_ID" \
    -n com.h1rose.aweauto/.ShareActivity
  ```
- **テストを実行する**：`./gradlew :app:testDebugUnitTest`

## 制限

- **Android Auto 自身の表示は消せない**：下のシステムバーや、右上に重なる小さな戻るボタンは、アプリからは隠せません。
- **サイト側の変更で動かなくなることがある**：サイトごとの調整は、YouTube / TVer のページ構造やプレーヤーの内部に依存しています。
- **先読みを増やす設定は実験的**：
  - YouTube は約2分で頭打ちになります。
  - TVer は、ブラウザが動画データを溜めておける量（MSE の容量）を超えて、読み込みが止まることがあります。
- **TVer は日本国内からのみ**：日本の IP アドレスが必要です。

## 免責事項

- 個人の非公式プロジェクトで、Google / YouTube / TVer とは関係ありません。
- 非公開の API を使い、他社の Web ページに手を加えているため、各サービスの利用規約に反する可能性があります。
- 利用は自己責任でお願いします。

## 謝辞

- [Fermata Auto](https://github.com/AndreyPavlenko/Fermata)：Android Auto に独自の UI を出せることを示してくれました。
- [yt-cast-receiver](https://github.com/patrickkfkan/yt-cast-receiver) と [plaincast](https://github.com/aykevl/plaincast)：Lounge API の仕組みを参考にしました。
- [AdGuard](https://github.com/AdguardTeam/AdGuardSDNSFilter) と [EasyList](https://easylist.to/)：フィルタリストを使わせてもらっています。
