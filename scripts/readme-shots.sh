#!/usr/bin/env bash
# README に載せるスクリーンショットを DHU (1280x720 / 240dpi) から撮る。
#
#   scripts/readme-shots.sh [--deploy] [--out docs/screenshots] [--only home,settings]
#
# DHU はこのスクリプトが起動する。各画面は DHU 上で手で開き、プロンプトで Enter を押すと PNG を保存する。
set -euo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"

OUT="$ROOT/docs/screenshots"
DEPLOY=false
ONLY=""

# 撮る画像 (ファイル名|撮る前の案内)。README の Screenshots の表と同じ順
SHOTS=(
  "home|ホーム画面を表示してください。"
  "library|ホームの「ライブラリ」タブを表示してください。"
  "youtube-search|YouTube で検索結果のグリッドを表示してください。"
  "youtube-player|YouTube の再生画面を表示してください。"
  "loading|再生直後の読み込みカバーを表示してください。"
  "settings|設定画面を表示してください。"
  "tver-home|TVer のホームを表示してください。"
  "tver-player|TVer の再生画面を表示してください。"
  "map-split|地図ボタンで地図を左右に並べ、右側で動画を再生してください (地図は東京タワーを表示します)。"
  "map-pip|地図を小窓 (PiP) にして、地図が小さい側になるよう入れ替えてください (地図は経路のプレビューを表示します)。"
)

# 地図の画像は、自宅のボタン・アカウントの写真・現在地が写らないように、撮る前に東京の画面を出しておく
# (検索結果と経路の画面には自宅やアカウントが出ない)
map_demo_url() {
  case "$1" in
    map-split) echo 'geo:0,0?q=東京タワー' ;;
    map-pip) echo 'https://www.google.com/maps/dir/?api=1&origin=東京駅&destination=東京タワー&travelmode=driving' ;;
  esac
}

# 地図枠 (仮想ディスプレイ) の Google マップに URL を開かせる
show_on_map() {
  local url="$1" display
  display="$(adb shell dumpsys activity activities | tr -d '\r' |
    awk '/^Display #/ { d = $2 } /TaskRecord.*A=com.google.android.apps.maps/ { sub("#", "", d); print d; exit }')"
  if [ -z "$display" ]; then
    echo "地図枠に Google マップが見つかりません。設定の「地図枠に出すアプリ」を Google マップにしてください" >&2
    return 1
  fi
  adb shell am start --display "$display" -a android.intent.action.VIEW -d "'$url'" -p com.google.android.apps.maps >/dev/null 2>&1
}

want() {
  [ -z "$ONLY" ] || [[ ",$ONLY," == *",$1,"* ]]
}

main() {
  need_device
  if pgrep -f desktop-head-unit >/dev/null; then
    die "DHU がすでに起動しています。閉じてから実行してください"
  fi
  if [ "$DEPLOY" = true ]; then
    build_apk
    install_apk
  fi
  mkdir -p "$OUT"

  local ini log
  ini="$(dhu_720_ini)"
  log="$(mktemp -t aweauto-readme-dhu).log"
  trap 'dhu_stop; rm -f "$ini"' EXIT
  dhu_start "$ini" "$log"
  echo "DHU を起動しました。ランチャーから aweauto を開いてください。"

  local shot name prompt
  for shot in "${SHOTS[@]}"; do
    name="${shot%%|*}"
    prompt="${shot#*|}"
    want "$name" || continue
    echo
    echo "== $name.png =="
    echo "$prompt"
    if [ -n "$(map_demo_url "$name")" ]; then
      read -r -p "地図枠が出たら Enter (地図に東京の画面を出します): " _
      show_on_map "$(map_demo_url "$name")" || true
      [ "$name" = map-pip ] && echo "経路が出たら、小窓の中の「プレビュー」を押すとナビ中のような画面になります。"
    fi
    read -r -p "DHU の画面を合わせたら Enter: " _
    dhu_screenshot "$OUT/$name.png"
    echo "保存しました: $OUT/$name.png"
  done
  echo
  echo "完了: $OUT"
}

main "$@"
