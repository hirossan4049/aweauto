// aweauto: 広告ブロック ON のときだけ注入される YouTube 用の広告除去
// YouTube の動画広告は動画本体と同じドメインから来るのでドメインブロックでは止まらない。
// 1) プレーヤー設定から広告の予定 (adPlacements など) を消す
// 2) それでも流れた広告は即スキップする
(function () {
  if (window.__aweautoAdblock) return;
  window.__aweautoAdblock = true;

  var AD_KEYS = ['adPlacements', 'adSlots', 'playerAds', 'adBreakHeartbeatParams'];

  function prune(obj) {
    if (!obj || typeof obj !== 'object') return obj;
    AD_KEYS.forEach(function (k) { if (k in obj) delete obj[k]; });
    if (obj.playerResponse) prune(obj.playerResponse);
    return obj;
  }

  // 初回表示: HTML 内のスクリプトが代入する ytInitialPlayerResponse
  var initial;
  try {
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function () { return initial; },
      set: function (v) { initial = prune(v); },
    });
  } catch (e) {}

  // 画面遷移: /youtubei/v1/player の応答
  var parse = JSON.parse;
  JSON.parse = function () {
    var r = parse.apply(this, arguments);
    try { if (r && typeof r === 'object' && (r.adPlacements || r.playerAds || r.adSlots || r.playerResponse)) prune(r); } catch (e) {}
    return r;
  };
  var json = Response.prototype.json;
  Response.prototype.json = function () {
    return json.apply(this, arguments).then(prune);
  };

  // 取りこぼした広告は最後まで飛ばしてスキップボタンを押す。
  // 常時 300ms で DOM を探すと再生中も負荷になるので、DOM 変化時を主にして低頻度の保険だけ残す。
  function skipVisibleAd() {
    var player = document.querySelector('.html5-video-player');
    if (!player || !player.classList.contains('ad-showing')) return;
    var v = player.querySelector('video');
    if (v && isFinite(v.duration)) {
      v.muted = true;
      v.currentTime = v.duration;
    }
    var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
    if (skip) skip.click();
  }

  var scheduled = false;
  function scheduleSkip() {
    if (scheduled) return;
    scheduled = true;
    setTimeout(function () {
      scheduled = false;
      skipVisibleAd();
    }, 120);
  }
  new MutationObserver(scheduleSkip).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
  setInterval(skipVisibleAd, 1500);
})();
