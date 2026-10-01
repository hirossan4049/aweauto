#!/usr/bin/env python3
"""README とリポジトリの紹介用の画像を、docs/screenshots のスクリーンショットから作る。

    python3 scripts/readme-images.py      # Pillow が必要 (pip install pillow)

- docs/hero.png: README の一番上。中央に「動画の横に地図」、左右の奥にホームと YouTube の再生画面
- docs/social-preview.png: GitHub の Social preview (OGP、1280x640)。
  リポジトリの Settings → General → Social preview からアップロードする
背景はどちらもアプリアイコンと同じ紺のグラデーション。文字は macOS の Avenir Next を使う。
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "docs" / "screenshots"
HERO = ROOT / "docs" / "hero.png"
SOCIAL = ROOT / "docs" / "social-preview.png"
FONT = "/System/Library/Fonts/Avenir Next.ttc"
BOLD, MEDIUM = 0, 5  # Avenir Next.ttc の中の書体の番号

W, H = 1600, 640
TOP, BOTTOM = (0x2A, 0x33, 0x50), (0x11, 0x13, 0x18)  # ic_launcher_background と同じ色


def gradient(w: int = W, h: int = H) -> Image.Image:
    img = Image.new("RGB", (w, h))
    draw = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        draw.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))
    return img


def card(name: str, width: int, dim: float = 1.0, app_only: bool = False) -> Image.Image:
    """角を丸めたスクリーンショット。dim < 1 で奥にあるように暗くする。
    app_only なら下の Android Auto のバー (720 中 120px) を切り落としてアプリの画面だけにする"""
    shot = Image.open(SHOTS / f"{name}.png").convert("RGB")
    if app_only:
        shot = shot.crop((0, 0, shot.width, shot.height * 600 // 720))
    shot = shot.resize((width, round(shot.height * width / shot.width)), Image.LANCZOS)
    if dim < 1:
        shot = ImageEnhance.Brightness(shot).enhance(dim)
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, *shot.size], radius=round(width * 0.025), fill=255)
    out = shot.convert("RGBA")
    out.putalpha(mask)
    return out


def paste_with_shadow(canvas: Image.Image, img: Image.Image, x: int, y: int) -> None:
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    alpha = img.getchannel("A").point(lambda a: a * 0.6)
    shadow.paste((0, 0, 0, 255), (x, y + 18), alpha)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(28)))
    canvas.alpha_composite(img, (x, y))


def font(size: int, face: int) -> ImageFont.FreeTypeFont:
    try:
        return ImageFont.truetype(FONT, size, index=face)
    except OSError:
        return ImageFont.load_default(size)


def logo(size: int) -> Image.Image:
    """アプリアイコン (res/drawable/ic_launcher_*.xml) と同じ図柄。72x72 の範囲を size に拡大して描く"""
    k = size / 72
    img = gradient(size, size).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size, size], radius=round(18 * k), fill=255)
    img.putalpha(mask)
    d = ImageDraw.Draw(img)
    p = lambda x, y: ((x - 18) * k, (y - 18) * k)  # noqa: E731 アイコンの座標 (18〜90) をこの画像の座標に
    d.rounded_rectangle([*p(30, 36), *p(78, 70)], radius=5 * k, fill="#8AB4F8")
    d.polygon([p(49, 45.5), p(63, 53), p(49, 60.5)], fill="#111318")
    d.rounded_rectangle([*p(44, 74.5), *p(64, 78)], radius=1.75 * k, fill="#8AB4F8")
    return img


def hero() -> None:
    canvas = gradient().convert("RGBA")
    back_w, front_w = 720, 920
    left = card("home", back_w, dim=0.6)
    right = card("youtube-player", back_w, dim=0.6)
    front = card("map-split", front_w)
    back_y = H - left.height - 70
    paste_with_shadow(canvas, left, 30, back_y)
    paste_with_shadow(canvas, right, W - back_w - 30, back_y)
    paste_with_shadow(canvas, front, (W - front_w) // 2, H - front.height - 50)
    canvas.convert("RGB").save(HERO, optimize=True)
    print(f"wrote {HERO.relative_to(ROOT)}")


def social_preview() -> None:
    w, h = 1280, 640
    canvas = gradient(w, h).convert("RGBA")
    # 右側にスクリーンショット (右端は少しはみ出させる)
    shot = card("map-split", 760, app_only=True)
    paste_with_shadow(canvas, shot, w - 590, (h - shot.height) // 2)
    # 左側にロゴと名前、説明
    canvas.alpha_composite(logo(112), (80, 128))
    d = ImageDraw.Draw(canvas)
    d.text((80, 262), "aweauto", font=font(92, BOLD), fill="#FFFFFF")
    d.text((84, 378), "Streaming sites on Android Auto", font=font(36, MEDIUM), fill="#8AB4F8")
    for i, line in enumerate(["Google TV–style player", "Your map app alongside", "Turn-by-turn on the HUD"]):
        y = 448 + i * 46
        d.ellipse([86, y + 11, 96, y + 21], fill="#8AB4F8")
        d.text((112, y), line, font=font(28, MEDIUM), fill="#C9D1E4")
    canvas.convert("RGB").save(SOCIAL, optimize=True)
    print(f"wrote {SOCIAL.relative_to(ROOT)}")


def main() -> None:
    hero()
    social_preview()


if __name__ == "__main__":
    main()
