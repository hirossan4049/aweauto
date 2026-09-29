// aweauto: 通信が不安定な車内向けの画質上限 (TVer)
// window.__aweautoConfig = { maxHeight: 480 } が先に定義されている前提
(function () {
  var cfg = window.__aweautoConfig || {};
  if (window.__aweautoPrefetch) return;
  window.__aweautoPrefetch = true;

  var applied = new WeakSet();

  // 先読みはアプリ側 (HlsPrefetcher) がセグメントを丸ごとディスクに落として行うので、
  // プレーヤーの先読み秒数は既定のままにする (増やすと MSE の容量超過で止まる)。ここでは画質の上限だけ掛ける
  function apply() {
    var el = document.querySelector('.video-js');
    var player = el && el.player;
    if (!player) return;
    var vhs;
    try { vhs = player.tech({ IWillNotUseThisInPlugins: true }).vhs; } catch (e) { return; }
    if (!vhs || applied.has(vhs) || !vhs.representations) return;
    applied.add(vhs);
    if (cfg.maxHeight) {
      var reps = vhs.representations();
      var allowed = reps.filter(function (r) { return r.height <= cfg.maxHeight; });
      if (allowed.length) reps.forEach(function (r) { r.enabled(r.height <= cfg.maxHeight); });
    }
  }

  setInterval(apply, 1000);
})();
