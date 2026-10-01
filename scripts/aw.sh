#!/usr/bin/env bash
# aweauto の開発・運用でよく使う adb 操作をまとめたもの。
#
#   scripts/aw.sh <command> [args]
#
# 端末が複数つながっているときは ANDROID_SERIAL=<serial> を付ける。
set -euo pipefail

# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"
GEARHEAD=com.google.android.projection.gearhead

usage() {
  cat <<'EOF'
使い方: scripts/aw.sh <command> [args]

ビルド・インストール
  build              debug APK をビルド
  install            インストール (Android Auto に隠されないよう Play ストア扱いで入れる)
  deploy             build + install
  release            小さい release ビルド (約 3MB、R8 で圧縮) をビルドしてインストール
                     開発用の機能 (mute・地図枠のテスト表示) と run-as を使うコマンドは debug でだけ動く
  logs [tag...]      aweauto 関係のログを流す (既定: Awe* と NativeAppMapPane)

Shizuku
  shizuku            Shizuku を起動 (Android 9 は再起動のたびに必要)
  shizuku-status     Shizuku サーバーが動いているか
  tcpip              スマホ内の adb を 5555 番で待ち受けさせる (再起動まで有効)
                     aweauto が車につないだときに止まった Shizuku を自分で起動し直せるようになる
                     注意: 同じネットワークの他の機器からも adb 接続を受け付ける状態になる

車の画面 (Desktop Head Unit)
  dhu                Android Auto のヘッドユニットサーバーにつないで DHU を起動
                     (事前にスマホの Android Auto 開発者メニューで「ヘッドユニットサーバーを起動」)
  dhu-720            1280x720 / 240dpi で DHU を起動 (スクショ用)
  readme-shots       README 用スクリーンショットを DHU から半自動撮影

操作
  send <url>         配信サイトの URL を車の画面で開く (共有と同じ)
  devtools           WebView の DevTools を localhost:9333 に転送 (chrome://inspect でも可)
  touch tap <x> <y>  車の画面をタップ (Android Auto と同じ形で送る。debug ビルドのみ)
  touch scroll <dx> <dy> [count] [vy]
                     指を dx,dy ずつ count 回 (16ms ごと) 動かす。vy を付けると最後にその速さでフリング
                     dy が正なら指は上へ (コンテンツは下へスクロール)
  sample [秒]        メインスレッドが何をしているかを数える (debug ビルドのみ)

開発用
  e2e-map            ネイティブ地図枠の実機 E2E (再起動・サイズ変更・残プロセス確認)
  mute [on|off]      debug ビルドで動画を常にミュート (DHU で音を出さない)。aweauto を再起動して反映

端末の設定 (元に戻す: 各コマンドに off)
  resizable [on|off] 分割非対応アプリも地図枠に出せるようにする (force_resizable_activities)
EOF
}

cmd_build() { build_apk; }

cmd_install() { install_apk; }

cmd_release() {
  need_device
  (cd "$ROOT" && ./gradlew :app:assembleRelease)
  adb install -r -i com.android.vending "$ROOT/app/build/outputs/apk/release/app-release.apk"
}

cmd_logs() {
  need_device
  local tags=("$@")
  if [ ${#tags[@]} -eq 0 ]; then
    tags=(AweSurface AweHls AweLounge AweDial AweAdBlock NativeAppMapPane)
  fi
  local filter=()
  for t in "${tags[@]}"; do filter+=("$t:V"); done
  adb logcat -v time "${filter[@]}" '*:S'
}

shizuku_lib() {
  local dir
  dir="$(adb shell pm path "$SHIZUKU_PKG" | tr -d '\r' | sed 's/^package://; s#/base.apk$##')"
  if [ -z "$dir" ]; then
    echo "Shizuku がインストールされていません" >&2
    exit 1
  fi
  local abi
  abi="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  case "$abi" in
    arm64*) abi=arm64 ;;
    armeabi*) abi=arm ;;
    x86_64) abi=x86_64 ;;
    x86) abi=x86 ;;
  esac
  echo "$dir/lib/$abi/libshizuku.so"
}

cmd_shizuku() {
  need_device
  # アプリを開いたことがあれば start.sh があるが、無くても APK 同梱の起動用バイナリで起動できる
  adb shell "$(shizuku_lib)"
}

cmd_tcpip() {
  need_device
  adb tcpip 5555
  # tcpip に切り替えると USB 側の接続が一度切れるので戻るまで待つ
  adb wait-for-device
  echo "スマホ内の adb が 5555 番で待ち受け中です (再起動まで有効)"
}

cmd_shizuku_status() {
  need_device
  # grep -q で途中で抜けると adb がパイプエラーになり pipefail で失敗扱いになるので、先に出力を取る
  local procs
  procs="$(adb shell ps -A)"
  if grep -q shizuku_server <<<"$procs"; then
    echo "Shizuku: 起動中"
  else
    echo "Shizuku: 停止 (scripts/aw.sh shizuku で起動)"
    exit 1
  fi
}

start_dhu() {
  need_device
  need_dhu
  need_head_unit_server
  adb forward tcp:5277 tcp:5277 >/dev/null
  cd "$DHU_DIR" && exec ./desktop-head-unit "$@"
}

cmd_dhu() { start_dhu "$@"; }

cmd_dhu_720() { start_dhu -c "$(dhu_720_ini)"; }

cmd_send() {
  need_device
  local url="${1:?URL を指定してください}"
  adb shell am start -a android.intent.action.SEND -t text/plain \
    --es android.intent.extra.TEXT "'$url'" -n "$PKG/.ShareActivity" >/dev/null
  echo "送信しました: $url"
}

cmd_devtools() {
  need_device
  local pid
  pid="$(adb shell pidof "$PKG" | tr -d '\r')"
  if [ -z "$pid" ]; then
    echo "aweauto が起動していません" >&2
    exit 1
  fi
  adb forward tcp:9333 "localabstract:webview_devtools_remote_$pid" >/dev/null
  echo "http://localhost:9333/json で WebView の一覧が見られます"
}

cmd_touch() {
  need_device
  local a=(adb shell am broadcast -a com.h1rose.aweauto.debug.TOUCH -n "$PKG/.debug.TouchDebugReceiver")
  case "${1:-}" in
    tap) "${a[@]}" --es cmd tap --ef x "${2:?x}" --ef y "${3:?y}" >/dev/null ;;
    scroll)
      local extra=()
      [ -n "${5:-}" ] && extra=(--ef vy "$5")
      "${a[@]}" --es cmd scroll --ef dx "${2:?dx}" --ef dy "${3:?dy}" --ei count "${4:-20}" --ei interval 16 ${extra[@]+"${extra[@]}"} >/dev/null ;;
    *) die "使い方: scripts/aw.sh touch tap <x> <y> | touch scroll <dx> <dy> [count] [vy]" ;;
  esac
}

# メインスレッドが何をしているかを数秒数える (debug ビルドのみ)
cmd_sample() {
  need_device
  local sec="${1:-5}"
  adb logcat -c
  adb shell am broadcast -a com.h1rose.aweauto.debug.TOUCH -n "$PKG/.debug.TouchDebugReceiver" --es cmd sample --ei seconds "$sec" >/dev/null
  sleep $((sec + 1))
  adb logcat -d -s AweSample:I | sed 's/.*AweSample: //'
}

cmd_mute() {
  need_device
  local v
  case "${1:-on}" in on) v=true ;; off) v=false ;; *) echo "on か off を指定してください" >&2; exit 1 ;; esac
  local f=shared_prefs/aweauto.xml
  adb shell "run-as $PKG sh -c 'grep -q dev_mute $f && sed -i \"s#<boolean name=\\\"dev_mute\\\" value=\\\"[a-z]*\\\" />#<boolean name=\\\"dev_mute\\\" value=\\\"$v\\\" />#\" $f || sed -i \"s#</map>#    <boolean name=\\\"dev_mute\\\" value=\\\"$v\\\" />\\n</map>#\" $f'"
  adb shell am force-stop "$PKG"
  echo "dev_mute = $v (aweauto を再起動しました。車の画面で開き直してください)"
}

cmd_resizable() {
  need_device
  case "${1:-on}" in
    on) adb shell settings put global force_resizable_activities 1 ;;
    off) adb shell settings put global force_resizable_activities 0 ;;
    *) echo "on か off を指定してください" >&2; exit 1 ;;
  esac
  echo "force_resizable_activities = $(adb shell settings get global force_resizable_activities | tr -d '\r')"
  echo "反映には対象アプリの再起動が必要です (例: adb shell am force-stop jp.co.yahoo.android.apps.map)"
}

main() {
  local cmd="${1:-help}"
  shift || true
  case "$cmd" in
    build) cmd_build ;;
    install) cmd_install ;;
    deploy) cmd_build && cmd_install ;;
    release) cmd_release ;;
    logs) cmd_logs "$@" ;;
    shizuku) cmd_shizuku ;;
    shizuku-status) cmd_shizuku_status ;;
    tcpip) cmd_tcpip ;;
    dhu) cmd_dhu "$@" ;;
    dhu-720) cmd_dhu_720 ;;
    readme-shots) exec "$ROOT/scripts/readme-shots.sh" "$@" ;;
    send) cmd_send "$@" ;;
    devtools) cmd_devtools ;;
    e2e-map) exec "$ROOT/scripts/e2e-map.sh" "$@" ;;
    resizable) cmd_resizable "$@" ;;
    mute) cmd_mute "$@" ;;
    touch) cmd_touch "$@" ;;
    sample) cmd_sample "$@" ;;
    help | -h | --help) usage ;;
    *) echo "不明なコマンド: $cmd" >&2; usage; exit 1 ;;
  esac
}

main "$@"
