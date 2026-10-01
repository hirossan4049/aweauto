# aweauto のスクリプト共通の定数と関数。各スクリプトから source する。
# shellcheck shell=bash

PKG=com.h1rose.aweauto
MAP_PROC="$PKG:map_input"
MAP_VD_NAME=aweauto-native-map
SHIZUKU_PKG=moe.shizuku.privileged.api
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
DHU_DIR="$SDK/extras/google/auto"

die() {
  echo "$*" >&2
  exit 1
}

need_device() {
  adb get-state >/dev/null 2>&1 || die "端末が見つかりません (adb devices を確認)"
}

build_apk() {
  (cd "$ROOT" && ./gradlew :app:assembleDebug)
}

install_apk() {
  need_device
  # -i com.android.vending: 提供元が Play ストア以外のナビアプリは Android Auto のランチャーに出ないため
  adb install -r -i com.android.vending "$APK"
}

# ---- 地図枠 (Shizuku の UserService) ----

map_pids() {
  adb shell ps -A 2>/dev/null | tr -d '\r' | awk -v name="$MAP_PROC" '$NF == name { print $2 }'
}

map_proc_count() {
  map_pids | awk 'NF { c++ } END { print c + 0 }'
}

map_display_count() {
  adb shell dumpsys display 2>/dev/null | tr -d '\r' | grep -c "DisplayDeviceInfo{\"$MAP_VD_NAME\"" || true
}

kill_map_services() {
  local pids
  pids="$(map_pids | tr '\n' ' ')"
  [ -z "${pids// /}" ] && return 0
  echo "残っている $MAP_PROC を停止します: $pids"
  adb shell kill $pids >/dev/null 2>&1 || true
  sleep 1
}

# 設定 (SharedPreferences) の文字列を書き換える。aweauto は止まっている必要がある (debug ビルドのみ)
set_pref_string() {
  local key="$1" value="$2" f=shared_prefs/aweauto.xml
  adb shell "run-as $PKG sh -c 'grep -q \"name=\\\"$key\\\"\" $f && sed -i \"s#<string name=\\\"$key\\\">[^<]*</string>#<string name=\\\"$key\\\">$value</string>#\" $f || sed -i \"s#</map>#    <string name=\\\"$key\\\">$value</string>\\n</map>#\" $f'"
}

# 地図枠の仮想ディスプレイで動いているアプリのパッケージ (無ければ空)。
# ログは同じアプリ ID のログが多いと Android に捨てられることがあるので、端末の状態から直接見る
map_display_id() {
  adb shell dumpsys display 2>/dev/null | tr -d '\r' |
    awk -v name="$MAP_VD_NAME" '/^  Display [0-9]+:/ { d = $2 } $0 ~ "mBaseDisplayInfo=DisplayInfo\\{\"" name "\"" { sub(":", "", d); print d; exit }'
}

map_display_package() {
  local id="${1:-$(map_display_id)}"
  [ -z "$id" ] && return 0
  adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' |
    awk -v id="#$id" '/^Display #/ { on = ($2 == id) } on && /TaskRecord\{/ { for (i = 1; i <= NF; i++) if ($i ~ /^A=/) { sub("A=", "", $i); print $i; exit } }'
}

# 地図枠に pkg (省略なら何でも) が出るまで待つ (出たら 0、timeout 秒で 1)。
# old_id を渡すと、そのディスプレイ (再起動前のもの) ではない新しい地図枠を待つ
wait_map_shown() {
  local pkg="${1:-}" timeout="${2:-30}" old_id="${3:-}" i id shown
  for i in $(seq 1 "$timeout"); do
    id="$(map_display_id)"
    shown=""
    [ -n "$id" ] && [ "$id" != "$old_id" ] && shown="$(map_display_package "$id")"
    if [ -n "$shown" ] && { [ -z "$pkg" ] || [ "$shown" = "$pkg" ]; }; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# ---- Desktop Head Unit ----

need_dhu() {
  [ -x "$DHU_DIR/desktop-head-unit" ] || die "DHU がありません: sdkmanager \"extras;google;auto\" でインストール"
}

need_head_unit_server() {
  local ports
  ports="$(adb shell netstat -tln 2>/dev/null || true)"
  grep -q ':5277 ' <<<"$ports" || die "ヘッドユニットサーバーが起動していません。
Android Auto の設定 → ⋮ → 「ヘッドユニットサーバーを起動」を押してから再実行してください"
}

# 1280x720 / 240dpi の設定ファイルを作ってパスを出す (README のスクリーンショットと E2E の座標はこの解像度前提)
dhu_720_ini() {
  local ini
  ini="$(mktemp -t aweauto-dhu).ini"
  sed 's/^dpi = .*/dpi = 240/' "$DHU_DIR/config/default_720p.ini" >"$ini"
  echo "$ini"
}

# DHU をバックグラウンドで起動し、標準入力の FIFO からコマンド (tap / screenshot など) を送れるようにする。
#   dhu_start <ini> <log>  → dhu_cmd "tap 100 200" → dhu_stop
dhu_start() {
  local ini="$1" log="$2"
  need_dhu
  need_head_unit_server
  adb forward tcp:5277 tcp:5277 >/dev/null
  DHU_FIFO="$(mktemp -u -t aweauto-dhu-cmd)"
  mkfifo "$DHU_FIFO"
  (cd "$DHU_DIR" && exec ./desktop-head-unit -c "$ini" <"$DHU_FIFO" >"$log" 2>&1) &
  DHU_PID=$!
  exec 3>"$DHU_FIFO"
  sleep 3
  kill -0 "$DHU_PID" 2>/dev/null || die "DHU が起動しませんでした: $log"
}

dhu_cmd() {
  printf '%s\n' "$*" >&3
}

# 車の画面を PNG に保存する。保存できなければ失敗
dhu_screenshot() {
  local file="$1" i
  rm -f "$file"
  dhu_cmd "screenshot $file"
  for i in $(seq 1 20); do
    [ -s "$file" ] && return 0
    sleep 0.25
  done
  echo "スクリーンショットを保存できませんでした: $file" >&2
  return 1
}

dhu_stop() {
  if [ -n "${DHU_PID:-}" ]; then
    dhu_cmd quit 2>/dev/null || true
    exec 3>&- 2>/dev/null || true
    wait "$DHU_PID" >/dev/null 2>&1 || true
    DHU_PID=""
  fi
  [ -n "${DHU_FIFO:-}" ] && rm -f "$DHU_FIFO"
}

# aweauto が車の画面 (実車 or DHU) に出ているか。出ているときだけ aweauto の仮想ディスプレイがある
car_screen_active() {
  adb shell dumpsys display 2>/dev/null | grep -q 'DisplayDeviceInfo{"aweauto"'
}

# ---- ログ ----

# logcat に pattern が出るまで待つ (出たら 0、timeout 秒で 1)
wait_log() {
  local pattern="$1" timeout="${2:-20}" i
  for i in $(seq 1 "$timeout"); do
    adb logcat -d 2>/dev/null | grep -qE "$pattern" && return 0
    sleep 1
  done
  return 1
}
