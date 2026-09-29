// aweauto: 動画の再生が始まったことをアプリに知らせる (読み込み中の画面を閉じるため)
(function () {
  if (window.__aweautoVideoState || !window.AweVideo) return;
  window.__aweautoVideoState = true;
  document.addEventListener('playing', function (e) {
    if (e.target instanceof HTMLVideoElement) window.AweVideo.onPlaying();
  }, true);
})();
