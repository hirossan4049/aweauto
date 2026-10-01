#!/usr/bin/env python3
"""README の一番上の画像 (docs/hero.png) を、docs/screenshots のスクリーンショットから作る。

    python3 scripts/readme-hero.py        # Pillow が必要 (pip install pillow)

中央に「動画の横に地図」、左右の奥にホームと YouTube の再生画面を重ねる。
背景はアプリアイコンと同じ紺のグラデーション。
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "docs" / "screenshots"
OUT = ROOT / "docs" / "hero.png"

W, H = 1600, 640
TOP, BOTTOM = (0x2A, 0x33, 0x50), (0x11, 0x13, 0x18)  # ic_launcher_background と同じ色


def gradient() -> Image.Image:
    img = Image.new("RGB", (W, H))
    draw = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        draw.line([(0, y), (W, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))
    return img


def card(name: str, width: int, dim: float = 1.0) -> Image.Image:
    """角を丸めたスクリーンショット。dim < 1 で奥にあるように暗くする"""
    shot = Image.open(SHOTS / f"{name}.png").convert("RGB")
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


def main() -> None:
    canvas = gradient().convert("RGBA")
    back_w, front_w = 720, 920
    left = card("home", back_w, dim=0.6)
    right = card("youtube-player", back_w, dim=0.6)
    front = card("map-split", front_w)
    back_y = H - left.height - 70
    paste_with_shadow(canvas, left, 30, back_y)
    paste_with_shadow(canvas, right, W - back_w - 30, back_y)
    paste_with_shadow(canvas, front, (W - front_w) // 2, H - front.height - 50)
    canvas.convert("RGB").save(OUT, optimize=True)
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
