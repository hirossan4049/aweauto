// aweauto: 通信が不安定な車内向けの先読み・画質上限 (TVer)
// window.__aweautoConfig = { maxHeight: 480, readaheadSec: 600 } が先に定義されている前提
(function () {
  var cfg = window.__aweautoConfig || {};
  if (window.__aweautoPrefetch) return;
  window.__aweautoPrefetch = true;

  var applied = new WeakSet();
  // これ以上溜めると音声 SourceBuffer の容量 (MSE の上限) を超え、
  // 「audio append ... failed」で再生が止まる (実測で約 470 秒で発生)
  var MAX_READAHEAD_SEC = 300;

  // TVer のプレーヤーは video.js + HLS。既定では 20 秒しか先読みしないので、
  // 電波の良いうちに数分ぶん溜めておく (MSE の容量上限まで)
  function apply() {
    var el = document.querySelector('.video-js');
    var player = el && el.player;
    if (!player) return;
    var vhs;
    try { vhs = player.tech({ IWillNotUseThisInPlugins: true }).vhs; } catch (e) { return; }
    if (!vhs || applied.has(vhs)) return;
    var loader = vhs.playlistController_ && vhs.playlistController_.mainSegmentLoader_;
    var streaming = loader && loader.vhs_ && loader.vhs_.options_ && loader.vhs_.options_.streaming;
    if (!streaming) return;
    applied.add(vhs);

    if (cfg.readaheadSec) {
      streaming.bufferingGoal = Math.min(cfg.readaheadSec, MAX_READAHEAD_SEC);
      // 途切れたら 10 秒溜まるまで待ってから再開する (細かいカクつきを減らす)
      streaming.rebufferingGoal = 10;
    }
    if (cfg.maxHeight && vhs.representations) {
      var reps = vhs.representations();
      var allowed = reps.filter(function (r) { return r.height <= cfg.maxHeight; });
      if (allowed.length) reps.forEach(function (r) { r.enabled(r.height <= cfg.maxHeight); });
    }
  }

  setInterval(apply, 1000);
})();
