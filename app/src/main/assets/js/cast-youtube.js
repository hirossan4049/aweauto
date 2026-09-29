// aweauto: キャスト受信中に YouTube の再生状態をアプリへ知らせる (window.AweCast は WebView が用意する)
(function () {
  if (window.__aweautoCast || !window.AweCast) return;
  window.__aweautoCast = true;

  function videoId() {
    var m = location.search.match(/[?&]v=([^&]+)/);
    return m ? m[1] : '';
  }

  var last = 0;
  function report(state, force) {
    var v = document.querySelector('video.html5-main-video') || document.querySelector('video');
    if (!v || !videoId()) return;
    var now = Date.now();
    if (!force && now - last < 5000) return;
    last = now;
    window.AweCast.onState(videoId(), state, v.currentTime || 0, isFinite(v.duration) ? v.duration : 0);
  }

  // 1 再生中 / 2 一時停止 / 3 読み込み中 / 0 終了 (Lounge の状態コード)
  var events = { playing: 1, pause: 2, waiting: 3, seeking: 3, ended: 0, loadedmetadata: 3 };
  Object.keys(events).forEach(function (type) {
    document.addEventListener(type, function (e) {
      if (!(e.target instanceof HTMLVideoElement)) return;
      if (document.querySelector('.ad-showing')) return;
      report(events[type], true);
    }, true);
  });
  document.addEventListener('timeupdate', function (e) {
    if (e.target instanceof HTMLVideoElement && !e.target.paused) report(1, false);
  }, true);
})();
