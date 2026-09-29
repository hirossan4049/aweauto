// aweauto: 最適化 ON のときだけ注入される TVer 用の挙動調整
(function () {
  // 再生前に出る属性アンケート (誕生年・月・性別・郵便番号) は答えないと再生できないので、
  // 各欄のプレースホルダーと「その他」をダミー値として自動で回答する
  var setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;

  function fill(input) {
    if (!input || input.value) return;
    // React の制御コンポーネントなので、ネイティブの setter を通してから input イベントを送る
    setValue.call(input, input.placeholder);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function answer() {
    var form = document.querySelector('[class*="QuestionnaireModal_form__"]');
    if (!form || form.dataset.aweauto) return;
    form.dataset.aweauto = 'answering';
    ['birthYear', 'birthMonth', 'postCode'].forEach(function (name) {
      fill(form.querySelector('input[name="' + name + '"]'));
    });
    var other = form.querySelector('button[value="9"]');
    if (other) other.click();
    setTimeout(function () {
      var submit = document.querySelector('[class*="QuestionnaireModal_"] button[type="submit"]');
      if (submit && !submit.disabled) submit.click();
      else form.dataset.aweauto = '';
    }, 300);
  }

  answer();
  new MutationObserver(answer).observe(document.documentElement, { childList: true, subtree: true });
})();

(function () {
  // 番組ページのサムネイル上の再生ボタンを自動で押す (アプリ側の読み込み画面から直接再生に入るため)
  var clicked = null;
  setInterval(function () {
    var button = document.querySelector('[class*="PlayerThumbnail_playButton"]');
    if (button && clicked !== location.pathname) {
      clicked = location.pathname;
      button.click();
    }
  }, 500);
})();
