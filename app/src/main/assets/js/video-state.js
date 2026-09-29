// aweauto: 動画の再生・停止をアプリに知らせる (読み込み画面を閉じる / 割り込み後に再開するため)
(function () {
  if (window.__aweautoVideoState || !window.AweVideo) return;
  window.__aweautoVideoState = true;
  document.addEventListener('playing', function (e) {
    if (e.target instanceof HTMLVideoElement) window.AweVideo.onPlaying();
  }, true);
  document.addEventListener('pause', function (e) {
    if (e.target instanceof HTMLVideoElement) window.AweVideo.onPaused();
  }, true);
})();
