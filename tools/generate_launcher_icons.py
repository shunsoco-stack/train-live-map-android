#!/usr/bin/env python3
"""Generate Android launcher and Play Store icons from the approved train cutout.

Requires Pillow. The adaptive foreground is authored on Android's 108 dp layer
canvas at xxxhdpi (432 px). All non-transparent train pixels are kept inside a
slightly inset version of the 66 dp adaptive-icon safe circle.
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageCms, ImageDraw, ImageOps


ORANGE = (246, 139, 30, 255)
BROWN = (43, 29, 23, 255)
ADAPTIVE_SIZE = 432
ADAPTIVE_SAFE_RADIUS = 132  # 33 dp at xxxhdpi
ADAPTIVE_ART_RADIUS = 126  # 1.5 dp breathing room inside the safe circle
LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def alpha_crop(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError("Foreground source has no visible pixels")
    return rgba.crop(bbox)


def max_alpha_radius(image: Image.Image) -> float:
    alpha = image.getchannel("A")
    width, height = alpha.size
    center_x = (width - 1) / 2
    center_y = (height - 1) / 2
    pixels = alpha.load()
    radius = 0.0
    for y in range(height):
        for x in range(width):
            if pixels[x, y] > 0:
                radius = max(radius, math.hypot(x - center_x, y - center_y))
    return radius


def centered_foreground(
    source: Image.Image,
    canvas_size: int,
    target_radius: float,
) -> Image.Image:
    subject = alpha_crop(source)
    source_radius = max_alpha_radius(subject)
    scale = target_radius / source_radius
    target_size = (
        max(1, round(subject.width * scale)),
        max(1, round(subject.height * scale)),
    )
    subject = subject.resize(target_size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    offset = (
        round((canvas_size - subject.width) / 2),
        round((canvas_size - subject.height) / 2),
    )
    canvas.alpha_composite(subject, offset)
    return canvas


def composite_on_orange(foreground: Image.Image) -> Image.Image:
    background = Image.new("RGBA", foreground.size, ORANGE)
    background.alpha_composite(foreground)
    return background


def antialiased_mask(size: int, kind: str, supersampling: int = 4) -> Image.Image:
    high_size = size * supersampling
    mask = Image.new("L", (high_size, high_size), 0)
    draw = ImageDraw.Draw(mask)
    inset = supersampling
    bounds = (inset, inset, high_size - inset - 1, high_size - inset - 1)

    if kind == "circle":
        draw.ellipse(bounds, fill=255)
    elif kind == "rounded_square":
        draw.rounded_rectangle(bounds, radius=round(high_size * 0.22), fill=255)
    elif kind == "squircle":
        center = (high_size - 1) / 2
        radius = center - inset
        exponent = 4.0
        points: list[tuple[float, float]] = []
        for index in range(720):
            angle = 2 * math.pi * index / 720
            cos_value = math.cos(angle)
            sin_value = math.sin(angle)
            x = math.copysign(abs(cos_value) ** (2 / exponent), cos_value)
            y = math.copysign(abs(sin_value) ** (2 / exponent), sin_value)
            points.append((center + radius * x, center + radius * y))
        draw.polygon(points, fill=255)
    else:
        raise ValueError(f"Unknown mask kind: {kind}")

    return mask.resize((size, size), Image.Resampling.LANCZOS)


def validate_inside_mask(subject_alpha: Image.Image, mask: Image.Image) -> int:
    subject_values = subject_alpha.get_flattened_data()
    mask_values = mask.get_flattened_data()
    return sum(
        1
        for subject, mask_value in zip(subject_values, mask_values)
        if subject > 0 and mask_value < 254
    )


def subject_bbox(image: Image.Image) -> tuple[int, int, int, int]:
    bbox = image.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError("Generated foreground has no visible pixels")
    return bbox


def save_png(image: Image.Image, path: Path, *, srgb: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    options: dict[str, object] = {"optimize": True}
    if srgb:
        profile = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB"))
        options["icc_profile"] = profile.tobytes()
    image.save(path, format="PNG", **options)


def make_mask_preview(
    adaptive_composite: Image.Image,
    adaptive_foreground: Image.Image,
    destination: Path,
) -> dict[str, int]:
    icon_size = 512
    panel_width = 560
    panel_height = 590
    preview = Image.new("RGBA", (panel_width * 3, panel_height), (239, 235, 232, 255))
    draw = ImageDraw.Draw(preview)
    icon = adaptive_composite.resize((icon_size, icon_size), Image.Resampling.LANCZOS)
    subject_alpha = adaptive_foreground.resize(
        (icon_size, icon_size),
        Image.Resampling.LANCZOS,
    ).getchannel("A")

    results: dict[str, int] = {}
    mask_specs = (
        ("circle", "Circle"),
        ("rounded_square", "Rounded square"),
        ("squircle", "Squircle"),
    )
    for index, (kind, label) in enumerate(mask_specs):
        mask = antialiased_mask(icon_size, kind)
        masked_icon = icon.copy()
        masked_icon.putalpha(ImageChops.multiply(icon.getchannel("A"), mask))
        x = index * panel_width + (panel_width - icon_size) // 2
        preview.alpha_composite(masked_icon, (x, 18))
        text_box = draw.textbbox((0, 0), label)
        text_width = text_box[2] - text_box[0]
        draw.text(
            (index * panel_width + (panel_width - text_width) / 2, 548),
            label,
            fill=BROWN,
        )
        results[kind] = validate_inside_mask(subject_alpha, mask)

    save_png(preview, destination)
    return results


def generate(source_path: Path, project_root: Path, preview_path: Path) -> None:
    source = Image.open(source_path).convert("RGBA")
    res_root = project_root / "app" / "src" / "main" / "res"

    adaptive_foreground = centered_foreground(
        source,
        ADAPTIVE_SIZE,
        ADAPTIVE_ART_RADIUS,
    )
    adaptive_alpha = adaptive_foreground.getchannel("A")
    safe_circle = Image.new("L", (ADAPTIVE_SIZE, ADAPTIVE_SIZE), 0)
    safe_draw = ImageDraw.Draw(safe_circle)
    center = ADAPTIVE_SIZE / 2
    safe_draw.ellipse(
        (
            center - ADAPTIVE_SAFE_RADIUS,
            center - ADAPTIVE_SAFE_RADIUS,
            center + ADAPTIVE_SAFE_RADIUS,
            center + ADAPTIVE_SAFE_RADIUS,
        ),
        fill=255,
    )
    outside_safe_circle = validate_inside_mask(adaptive_alpha, safe_circle)
    if outside_safe_circle:
        raise RuntimeError(
            f"{outside_safe_circle} adaptive foreground pixels exceed the safe circle"
        )

    save_png(
        adaptive_foreground,
        res_root / "drawable-xxxhdpi" / "ic_launcher_foreground.png",
    )
    grayscale = ImageOps.grayscale(adaptive_foreground)
    darkness = ImageOps.invert(grayscale)
    detail_alpha = ImageChops.multiply(darkness, adaptive_alpha)
    base_alpha = adaptive_alpha.point(lambda value: round(value * 0.34))
    monochrome_alpha = ImageChops.lighter(base_alpha, detail_alpha)
    monochrome = Image.new("RGBA", adaptive_foreground.size, (255, 255, 255, 0))
    monochrome.putalpha(monochrome_alpha)
    save_png(
        monochrome,
        res_root / "drawable-xxxhdpi" / "ic_launcher_monochrome.png",
    )

    for density, size in LEGACY_SIZES.items():
        foreground = centered_foreground(source, size, size * 0.41)
        square_icon = composite_on_orange(foreground)
        mipmap = res_root / f"mipmap-{density}"
        square_icon.putalpha(
            ImageChops.multiply(
                square_icon.getchannel("A"),
                antialiased_mask(size, "rounded_square"),
            )
        )
        save_png(square_icon, mipmap / "ic_launcher.png")
        round_icon = composite_on_orange(foreground)
        round_icon.putalpha(
            ImageChops.multiply(
                round_icon.getchannel("A"),
                antialiased_mask(size, "circle"),
            )
        )
        save_png(round_icon, mipmap / "ic_launcher_round.png")

    play_foreground = centered_foreground(source, 512, 512 * 0.38)
    play_icon = composite_on_orange(play_foreground)
    play_path = project_root / "artwork" / "play-store-icon-512.png"
    save_png(play_icon, play_path, srgb=True)
    if play_path.stat().st_size > 1024 * 1024:
        raise RuntimeError("Play Store icon exceeds the 1 MB limit")

    adaptive_composite = composite_on_orange(adaptive_foreground)
    save_png(
        adaptive_composite.resize((512, 512), Image.Resampling.LANCZOS),
        preview_path.parent / "adaptive-icon-composite-512.png",
    )
    mask_results = make_mask_preview(
        adaptive_composite,
        adaptive_foreground,
        preview_path,
    )
    clipped_masks = {
        name: count for name, count in mask_results.items() if count != 0
    }
    if clipped_masks:
        raise RuntimeError(f"Adaptive foreground is clipped: {clipped_masks}")

    monochrome_preview = Image.new("RGBA", (512, 512), (239, 235, 232, 255))
    monochrome_tint = Image.new("RGBA", adaptive_foreground.size, BROWN)
    monochrome_tint.putalpha(monochrome_alpha)
    monochrome_preview.alpha_composite(
        monochrome_tint.resize((512, 512), Image.Resampling.LANCZOS)
    )
    save_png(
        monochrome_preview,
        preview_path.parent / "monochrome-icon-preview-512.png",
    )

    print(f"Adaptive foreground bbox: {subject_bbox(adaptive_foreground)}")
    print(f"Pixels outside 66dp safe circle: {outside_safe_circle}")
    for name, outside_count in mask_results.items():
        print(f"Foreground pixels clipped by {name}: {outside_count}")
    print(f"Play Store icon: {play_path} ({play_path.stat().st_size} bytes)")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--source",
        type=Path,
        default=Path("artwork/train-foreground-source.png"),
    )
    parser.add_argument("--project-root", type=Path, default=Path("."))
    parser.add_argument(
        "--preview",
        type=Path,
        default=Path(".verification/launcher-mask-preview.png"),
    )
    args = parser.parse_args()
    generate(
        args.source.resolve(),
        args.project_root.resolve(),
        args.preview.resolve(),
    )


if __name__ == "__main__":
    main()
