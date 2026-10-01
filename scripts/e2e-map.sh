#!/usr/bin/env bash
# ネイティブ地図枠の実機 E2E。
#
#   scripts/e2e-map.sh [--deploy] [--rounds 3] [--apps all|pkg,...] [--cleanup] [--out dir]
#
# 確認するもの:
# - 地図アプリが地図枠に出ること (地図枠の仮想ディスプレイにそのアプリの画面があるかで判定)
# - 分割/PiP・幅変更・タップで落ちないこと (この操作だけは手で行う)
# - aweauto を再起動しても com.h1rose.aweauto:map_input と仮想ディスプレイが 1 個のままなこと
# - --apps: 地図アプリを切り替えても、それぞれ地図枠に起動できること
set -euo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"

ROUNDS=3
DEPLOY=false
CLEANUP=false
RESIZE=true
APPS=""
OUT=""
ATTACH_TIMEOUT=30

# 出たら失敗とみなすログ。「Display removed」は aweauto を止めたときにも出るので、サイズ変更のステップでだけ見る
BAD_LOG='FATAL EXCEPTION|ANR in com\.h1rose\.aweauto|remote attach failed|no input forwarder|Error: Activity not started|launch map failed'
LAUNCH_FAILED_LOG='Error: Activity not started|launch map failed'

usage() {
  cat <<'EOF'
使い方: scripts/e2e-map.sh [options]

Options:
  --deploy          先に build + install する
  --rounds <n>      aweauto の force-stop → 再表示を繰り返す回数 (default: 3)
  --apps <list>     地図アプリの切り替えも確かめる。all で入っているもの全部、またはパッケージ名をカンマ区切り
  --no-resize       分割/PiP/タップを手で試すステップを飛ばす
  --cleanup         開始前に残っている map_input プロセスを止める
  --out <dir>       結果の出力先 (default: build/e2e/map-YYYYmmdd-HHMMSS)
  -h, --help        ヘルプ

事前準備:
  1. scripts/aw.sh dhu (または実車) で aweauto を開き、地図枠 (分割表示) を ON にしておく
  2. Shizuku を起動しておく (scripts/aw.sh shizuku)

aweauto を force-stop すると Android Auto が開き直すので、地図が出るまでは自動で待つ。
30 秒待っても出なければ、車の画面で aweauto を開くよう案内する。
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --deploy) DEPLOY=true; shift ;;
    --rounds) ROUNDS="$2"; shift 2 ;;
    --apps) APPS="$2"; shift 2 ;;
    --no-resize) RESIZE=false; shift ;;
    --cleanup) CLEANUP=true; shift ;;
    --out) OUT="$2"; shift 2 ;;
    -h | --help) usage; exit 0 ;;
    *) echo "不明なオプション: $1" >&2; usage; exit 1 ;;
  esac
done

FAILED=0

record() {
  local step="$1" result="$2" note="${3:-}"
  printf '%s\t%s\t%s\t%s\t%s\n' "$step" "$(map_proc_count)" "$(map_display_count)" "$result" "$note" |
    tee -a "$OUT/summary.tsv"
  [ "$result" = FAIL ] && FAILED=$((FAILED + 1))
  adb shell dumpsys display >"$OUT/display-$step.txt" 2>/dev/null || true
}

# 地図枠に出るまで待つ。出なければ手で開いてもらってもう一度待つ
wait_attached() {
  local step="$1" pkg="${2:-}" old_id="${3:-}"
  if wait_map_shown "$pkg" "$ATTACH_TIMEOUT" "$old_id"; then
    return 0
  fi
  if [ -t 0 ]; then
    read -r -p "地図が出ません。車の画面で aweauto を開いてから Enter: " _
    wait_map_shown "$pkg" "$ATTACH_TIMEOUT" "$old_id" && return 0
  fi
  echo "$step: 地図枠に表示されませんでした" >&2
  return 1
}

# ちょうど 1 個ずつなら PASS
check_counts() {
  local step="$1"
  if [ "$(map_proc_count)" = 1 ] && [ "$(map_display_count)" = 1 ]; then
    record "$step" PASS
  else
    record "$step" FAIL "map_input/仮想ディスプレイが 1 個ではない"
  fi
}

restart_and_check() {
  local step="$1" old_id
  old_id="$(map_display_id)"
  adb logcat -c
  adb shell am force-stop "$PKG"
  if wait_attached "$step" "" "$old_id"; then
    sleep 2 # 古いサービスが destroy されるまで少し待つ
    check_counts "$step"
  else
    record "$step" FAIL "地図枠に表示されない"
  fi
  adb logcat -d -v time >>"$OUT/logcat.txt"
}

# 地図枠に出せる地図・カーナビアプリ (aweauto の選択肢と同じ条件: geo: を開けて、ホームから起動できる)
installed_map_apps() {
  adb shell cmd package query-activities -a android.intent.action.VIEW -d 'geo:0,0?q=' 2>/dev/null |
    tr -d '\r' | sed -n 's/^ *packageName=//p' | sort -u |
    # adb shell は標準入力を読んでしまうので、ループ内では </dev/null を付ける
    while read -r p; do
      [ "$p" = "$PKG" ] || [ "$p" = com.waze ] && continue
      adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$p" </dev/null 2>/dev/null |
        grep -q / || continue
      adb shell dumpsys package "$p" </dev/null 2>/dev/null | grep -q ACCESS_MOCK_LOCATION && continue
      echo "$p"
    done
}

check_app() {
  local app="$1" step="app-$1" old_id
  old_id="$(map_display_id)"
  adb logcat -c
  # 書き換えてから止める (止めた直後に Android Auto が開き直すので、後から書くと間に合わないことがある)
  set_pref_string map_app "$app" >/dev/null
  adb shell am force-stop "$PKG"
  if wait_attached "$step" "$app" "$old_id"; then
    sleep 2
    if adb logcat -d | grep -qE "$LAUNCH_FAILED_LOG"; then
      record "$step" FAIL "$app を起動できない"
    else
      check_counts "$step"
    fi
  else
    record "$step" FAIL "$app が地図枠に出ない"
  fi
  adb logcat -d -v time >>"$OUT/logcat.txt"
}

write_report() {
  local bad
  bad="$(grep -cE "$BAD_LOG" "$OUT/logcat.txt" || true)"
  {
    echo "# Native map E2E"
    echo
    echo "- package: \`$PKG\`"
    echo "- rounds: \`$ROUNDS\`"
    echo "- output: \`$OUT\`"
    echo
    echo '| step | map_input | 仮想ディスプレイ | 結果 | メモ |'
    echo '|---|---:|---:|---|---|'
    awk -F '\t' 'NR > 1 { printf "| %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5 }' "$OUT/summary.tsv"
    echo
    echo "## ログ"
    echo
    if [ "$bad" = 0 ]; then
      echo "PASS: 失敗を示すログはありませんでした。"
    else
      echo "FAIL: 失敗を示すログが $bad 行あります (logcat.txt)。"
      echo
      echo '```'
      grep -E "$BAD_LOG" "$OUT/logcat.txt" | tail -40
      echo '```'
    fi
  } >"$OUT/report.md"
  [ "$bad" = 0 ] || FAILED=$((FAILED + 1))
}

main() {
  need_device
  if [ "$DEPLOY" = true ]; then
    build_apk
    install_apk
  fi
  [ "$CLEANUP" = true ] && kill_map_services
  car_screen_active || die "車の画面に aweauto が出ていません。DHU (scripts/aw.sh dhu) か実車で aweauto を開いてから実行してください"
  OUT="${OUT:-$ROOT/build/e2e/map-$(date +%Y%m%d-%H%M%S)}"
  mkdir -p "$OUT"
  : >"$OUT/logcat.txt"
  printf 'step\tmap_input\tvirtual_displays\tresult\tnote\n' >"$OUT/summary.tsv"
  echo "出力先: $OUT"

  restart_and_check opened

  if [ "$RESIZE" = true ] && [ -t 0 ]; then
    adb logcat -c
    echo
    echo "車の画面で 分割/PiP の切り替え・左右入れ替え・幅変更・地図のタップを試してください。"
    read -r -p "一通り操作したら Enter: " _
    adb logcat -d -v time >>"$OUT/logcat.txt"
    if adb logcat -d | grep -qE 'reused=false|Display removed'; then
      record resized FAIL "サイズ変更で仮想ディスプレイが作り直された"
    else
      check_counts resized
    fi
  fi

  local i
  for ((i = 1; i <= ROUNDS; i++)); do
    restart_and_check "restart-$i"
  done

  if [ -n "$APPS" ]; then
    local list original
    original="$(adb shell "run-as $PKG cat shared_prefs/aweauto.xml" | tr -d '\r' | sed -n 's#.*<string name="map_app">\([^<]*\)</string>.*#\1#p')"
    if [ "$APPS" = all ]; then list="$(installed_map_apps)"; else list="$(tr ',' '\n' <<<"$APPS")"; fi
    for app in $list; do check_app "$app"; done
    # 元の地図アプリに戻す
    [ -n "$original" ] && { set_pref_string map_app "$original" >/dev/null; adb shell am force-stop "$PKG"; }
  fi

  write_report
  echo
  cat "$OUT/report.md"
  [ "$FAILED" = 0 ]
}

main "$@"
