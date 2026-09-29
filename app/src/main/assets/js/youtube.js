// aweauto: 最適化 ON のときだけ注入される YouTube 用の挙動調整
(function () {
  // 共有やホームから開いた動画は「ユーザー操作なし」扱いでミュート再生になるので、
  // 動画ごとに一度だけミュートを解除する (その後ユーザーがミュートしたら尊重する)
  var unmutedSrc = null;
  document.addEventListener('playing', function (e) {
    var v = e.target;
    if (!(v instanceof HTMLVideoElement) || !v.muted || unmutedSrc === v.src) return;
    unmutedSrc = v.src;
    var button = document.querySelector('.ytp-unmute');
    if (button) button.click();
    else v.muted = false;
  }, true);
})();
