// aweauto: 通信が不安定な車内向けの先読み・画質上限 (YouTube)
// window.__aweautoConfig = { maxHeight: 480, readaheadSec: 600 } が先に定義されている前提
(function () {
  var cfg = window.__aweautoConfig || {};
  if (window.__aweautoPrefetch) return;
  window.__aweautoPrefetch = true;

  // ---- 先読み秒数: プレーヤーが読む実験フラグを生成前に書き換える ----
  function patchFlags(serialized) {
    if (!cfg.readaheadSec || typeof serialized !== 'string') return serialized;
    var overrides = {
      enable_server_driven_readahead: 'false',
      html5_minimum_readahead_seconds: String(cfg.readaheadSec),
      html5_maximum_readahead_seconds: String(cfg.readaheadSec * 2),
      html5_platform_minimum_readahead_seconds: String(cfg.readaheadSec),
    };
    var parts = serialized.split('&').filter(function (kv) { return !(kv.split('=')[0] in overrides); });
    Object.keys(overrides).forEach(function (k) { parts.push(k + '=' + overrides[k]); });
    return parts.join('&');
  }

  function patchConfig(obj) {
    var ctx = obj && obj.WEB_PLAYER_CONTEXT_CONFIGS;
    if (!ctx) return;
    Object.keys(ctx).forEach(function (k) {
      ctx[k].serializedExperimentFlags = patchFlags(ctx[k].serializedExperimentFlags);
    });
  }

  function hookYtcfg(ytcfg) {
    if (!ytcfg || ytcfg.__aweauto || typeof ytcfg.set !== 'function') return ytcfg;
    var set = ytcfg.set;
    ytcfg.set = function (a, b) {
      if (typeof a === 'object') patchConfig(a);
      else if (a === 'WEB_PLAYER_CONTEXT_CONFIGS') patchConfig({ WEB_PLAYER_CONTEXT_CONFIGS: b });
      return set.apply(this, arguments);
    };
    ytcfg.__aweauto = true;
    if (ytcfg.data_) patchConfig(ytcfg.data_);
    return ytcfg;
  }

  var current = window.ytcfg;
  try {
    Object.defineProperty(window, 'ytcfg', {
      configurable: true,
      get: function () { return current; },
      set: function (v) { current = hookYtcfg(v); },
    });
  } catch (e) {}
  hookYtcfg(current);

  // ---- 画質の上限 ----
  var LABELS = [[144, 'tiny'], [240, 'small'], [360, 'medium'], [480, 'large'], [720, 'hd720'], [1080, 'hd1080']];
  function qualityFor(height) {
    var q = 'tiny';
    LABELS.forEach(function (l) { if (l[0] <= height) q = l[1]; });
    return q;
  }

  if (cfg.maxHeight) {
    var q = qualityFor(cfg.maxHeight);
    document.addEventListener('loadedmetadata', function (e) {
      if (!(e.target instanceof HTMLVideoElement)) return;
      var player = document.getElementById('movie_player');
      if (!player || !player.setPlaybackQualityRange) return;
      player.setPlaybackQualityRange(q, q);
      if (player.setPlaybackQuality) player.setPlaybackQuality(q);
    }, true);
  }
})();
