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
  "map-split|地図ボタンで地図を左右に並べ、右側で動画を再生してください。"
  "map-pip|地図を小窓 (PiP) にしてください。"
)

usage() {
  local names
  names="$(printf '%s\n' "${SHOTS[@]}" | cut -d'|' -f1 | paste -sd, -)"
  cat <<EOF
使い方: scripts/readme-shots.sh [options]

Options:
  --deploy          撮影前に build + install する
  --out <dir>       出力先 (default: docs/screenshots)
  --only <names>    カンマ区切りで撮る画像を限定
                    $names
  -h, --help        ヘルプ

事前準備:
  1. Android Auto の開発者メニューで「ヘッドユニットサーバーを起動」
  2. ほかの DHU を閉じておく (ヘッドユニットサーバーにつなげる DHU は 1 つだけ)
  3. 地図の画像を撮るなら Shizuku を起動しておく (scripts/aw.sh shizuku)
  ヒント: 動画のページは別のターミナルで scripts/aw.sh send <url> を使うと早い
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --deploy) DEPLOY=true; shift ;;
    --out) OUT="$2"; shift 2 ;;
    --only) ONLY="$2"; shift 2 ;;
    -h | --help) usage; exit 0 ;;
    *) echo "不明なオプション: $1" >&2; usage; exit 1 ;;
  esac
done

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
    read -r -p "DHU の画面を合わせたら Enter: " _
    dhu_screenshot "$OUT/$name.png"
    echo "保存しました: $OUT/$name.png"
  done
  echo
  echo "完了: $OUT"
}

main "$@"
