#!/usr/bin/env bash
# aweauto の開発・運用でよく使う adb 操作をまとめたもの。
#
#   scripts/aw.sh <command> [args]
#
# 端末が複数つながっているときは ANDROID_SERIAL=<serial> を付ける。
set -euo pipefail

PKG=com.h1rose.aweauto
SHIZUKU_PKG=moe.shizuku.privileged.api
GEARHEAD=com.google.android.projection.gearhead
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
DHU_DIR="$SDK/extras/google/auto"

usage() {
  cat <<'EOF'
使い方: scripts/aw.sh <command> [args]

ビルド・インストール
  build              debug APK をビルド
  install            インストール (Android Auto に隠されないよう Play ストア扱いで入れる)
  deploy             build + install
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

操作
  send <url>         YouTube / TVer の URL を車の画面で開く (共有と同じ)
  devtools           WebView の DevTools を localhost:9333 に転送 (chrome://inspect でも可)

端末の設定 (元に戻す: 各コマンドに off)
  resizable [on|off] 分割非対応アプリも地図枠に出せるようにする (force_resizable_activities)
EOF
}

adb_() { adb "$@"; }

need_device() {
  if ! adb get-state >/dev/null 2>&1; then
    echo "端末が見つかりません (adb devices を確認)" >&2
    exit 1
  fi
}

cmd_build() {
  (cd "$ROOT" && ./gradlew :app:assembleDebug)
}

cmd_install() {
  need_device
  # -i com.android.vending: 提供元が Play ストア以外のナビアプリは Android Auto のランチャーに出ないため
  adb_ install -r -i com.android.vending "$APK"
}

cmd_logs() {
  need_device
  local tags=("$@")
  if [ ${#tags[@]} -eq 0 ]; then
    tags=(AweSurface AweHls AweLounge AweDial AweAdBlock NativeAppMapPane)
  fi
  local filter=()
  for t in "${tags[@]}"; do filter+=("$t:V"); done
  adb_ logcat -v time "${filter[@]}" '*:S'
}

shizuku_lib() {
  local dir
  dir="$(adb_ shell pm path "$SHIZUKU_PKG" | tr -d '\r' | sed 's/^package://; s#/base.apk$##')"
  if [ -z "$dir" ]; then
    echo "Shizuku がインストールされていません" >&2
    exit 1
  fi
  local abi
  abi="$(adb_ shell getprop ro.product.cpu.abi | tr -d '\r')"
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
  adb_ shell "$(shizuku_lib)"
}

cmd_tcpip() {
  need_device
  adb_ tcpip 5555
  # tcpip に切り替えると USB 側の接続が一度切れるので戻るまで待つ
  adb_ wait-for-device
  echo "スマホ内の adb が 5555 番で待ち受け中です (再起動まで有効)"
}

cmd_shizuku_status() {
  need_device
  # grep -q で途中で抜けると adb がパイプエラーになり pipefail で失敗扱いになるので、先に出力を取る
  local procs
  procs="$(adb_ shell ps -A)"
  if grep -q shizuku_server <<<"$procs"; then
    echo "Shizuku: 起動中"
  else
    echo "Shizuku: 停止 (scripts/aw.sh shizuku で起動)"
    exit 1
  fi
}

start_dhu() {
  need_device
  if [ ! -x "$DHU_DIR/desktop-head-unit" ]; then
    echo "DHU がありません: sdkmanager \"extras;google;auto\" でインストール" >&2
    exit 1
  fi
  local ports
  ports="$(adb_ shell netstat -tln)"
  if ! grep -q ':5277 ' <<<"$ports"; then
    echo "ヘッドユニットサーバーが起動していません。" >&2
    echo "Android Auto の設定 → ⋮ → 「ヘッドユニットサーバーを起動」を押してから再実行してください" >&2
    exit 1
  fi
  adb_ forward tcp:5277 tcp:5277 >/dev/null
  cd "$DHU_DIR" && exec ./desktop-head-unit "$@"
}

cmd_dhu() { start_dhu "$@"; }

cmd_dhu_720() {
  local ini
  ini="$(mktemp -t aweauto-dhu).ini"
  sed 's/^dpi = .*/dpi = 240/' "$DHU_DIR/config/default_720p.ini" >"$ini"
  start_dhu -c "$ini"
}

cmd_send() {
  need_device
  local url="${1:?URL を指定してください}"
  adb_ shell am start -a android.intent.action.SEND -t text/plain \
    --es android.intent.extra.TEXT "'$url'" -n "$PKG/.ShareActivity" >/dev/null
  echo "送信しました: $url"
}

cmd_devtools() {
  need_device
  local pid
  pid="$(adb_ shell pidof "$PKG" | tr -d '\r')"
  if [ -z "$pid" ]; then
    echo "aweauto が起動していません" >&2
    exit 1
  fi
  adb_ forward tcp:9333 "localabstract:webview_devtools_remote_$pid" >/dev/null
  echo "http://localhost:9333/json で WebView の一覧が見られます"
}

cmd_resizable() {
  need_device
  case "${1:-on}" in
    on) adb_ shell settings put global force_resizable_activities 1 ;;
    off) adb_ shell settings put global force_resizable_activities 0 ;;
    *) echo "on か off を指定してください" >&2; exit 1 ;;
  esac
  echo "force_resizable_activities = $(adb_ shell settings get global force_resizable_activities | tr -d '\r')"
  echo "反映には対象アプリの再起動が必要です (例: adb shell am force-stop jp.co.yahoo.android.apps.map)"
}

main() {
  local cmd="${1:-help}"
  shift || true
  case "$cmd" in
    build) cmd_build ;;
    install) cmd_install ;;
    deploy) cmd_build && cmd_install ;;
    logs) cmd_logs "$@" ;;
    shizuku) cmd_shizuku ;;
    shizuku-status) cmd_shizuku_status ;;
    tcpip) cmd_tcpip ;;
    dhu) cmd_dhu "$@" ;;
    dhu-720) cmd_dhu_720 ;;
    send) cmd_send "$@" ;;
    devtools) cmd_devtools ;;
    resizable) cmd_resizable "$@" ;;
    help | -h | --help) usage ;;
    *) echo "不明なコマンド: $cmd" >&2; usage; exit 1 ;;
  esac
}

main "$@"
