#!/usr/bin/env python3
"""
generate_high_res_icons.py
Generates launcher icons, splash screen icon, and in-app emblem from high-res USAP_Color_Logo.png
"""

import os
from PIL import Image, ImageDraw

def generate_icons():
    src_path = "USAP_Color_Logo.png"
    if not os.path.exists(src_path):
        print(f"Error: {src_path} not found!")
        return False

    src = Image.open(src_path).convert("RGBA")
    bbox = src.getbbox()
    print("Cropping emblem from bbox:", bbox)
    cropped = src.crop(bbox)
    print(f"Emblem dimensions: {cropped.size[0]}x{cropped.size[1]} px")

    # 1. Adaptive Icon Foregrounds (108dp canvas, emblem ~64dp safe zone to preserve outer text ring)
    adaptive_sizes = {
        "mdpi": (108, 68),
        "hdpi": (162, 102),
        "xhdpi": (216, 136),
        "xxhdpi": (324, 204),
        "xxxhdpi": (432, 272),
    }

    for density, (canvas_size, emblem_size) in adaptive_sizes.items():
        out_dir = f"app/src/main/res/drawable-{density}"
        os.makedirs(out_dir, exist_ok=True)
        canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        resized_emblem = cropped.resize((emblem_size, emblem_size), Image.Resampling.LANCZOS)
        offset = (canvas_size - emblem_size) // 2
        canvas.paste(resized_emblem, (offset, offset), resized_emblem)
        out_file = os.path.join(out_dir, "ic_launcher_foreground.png")
        canvas.save(out_file, "PNG", optimize=True)
        print(f"  [Adaptive Foreground] {out_file}: {canvas_size}x{canvas_size} px (emblem {emblem_size}x{emblem_size})")

    # 2. Legacy and Round Launcher Mipmaps
    legacy_sizes = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }

    for density, size in legacy_sizes.items():
        out_dir = f"app/src/main/res/mipmap-{density}"
        os.makedirs(out_dir, exist_ok=True)

        # Round icon: circular emblem direct fit
        emblem_round = cropped.resize((size, size), Image.Resampling.LANCZOS)
        round_path = os.path.join(out_dir, "ic_launcher_round.png")
        emblem_round.save(round_path, "PNG", optimize=True)

        # Legacy squircle icon: dark background with emblem centered
        square_canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(square_canvas)
        corner_radius = int(size * 0.22)
        draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=corner_radius, fill=(15, 23, 42, 255))
        inner_size = int(size * 0.82)
        inner_emblem = cropped.resize((inner_size, inner_size), Image.Resampling.LANCZOS)
        inner_offset = (size - inner_size) // 2
        square_canvas.paste(inner_emblem, (inner_offset, inner_offset), inner_emblem)
        square_path = os.path.join(out_dir, "ic_launcher.png")
        square_canvas.save(square_path, "PNG", optimize=True)
        print(f"  [Mipmap Launcher] {round_path} & {square_path}: {size}x{size} px")

    # 3. High-Resolution Splash Icon (1024x1024 px) for Android 12+ Splash Screen
    # Android 12+ applies a 160dp circular mask out of 288dp viewport (55.5% diameter = ~568px).
    # Sizing the emblem to 560x560 px ensures the entire logo is visible
    for dir_name in ["app/src/main/res/drawable-nodpi", "app/src/main/res/drawable-xxxhdpi"]:
        os.makedirs(dir_name, exist_ok=True)
        splash_canvas = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
        splash_emblem = cropped.resize((560, 560), Image.Resampling.LANCZOS)
        splash_offset = (1024 - 560) // 2
        splash_canvas.paste(splash_emblem, (splash_offset, splash_offset), splash_emblem)
        splash_file = os.path.join(dir_name, "splash_icon.png")
        splash_canvas.save(splash_file, "PNG", optimize=True)
        print(f"  [Splash Screen Icon] {splash_file}: 1024x1024 px (emblem 560x560 px)")

    # 4. In-App UI USAP Logo (512x512 px) for Help Modal / Dialogs
    for dir_name in ["app/src/main/res/drawable-nodpi", "app/src/main/res/drawable-xxxhdpi"]:
        os.makedirs(dir_name, exist_ok=True)
        ui_logo = cropped.resize((512, 512), Image.Resampling.LANCZOS)
        ui_logo_path = os.path.join(dir_name, "usap_logo.png")
        ui_logo.save(ui_logo_path, "PNG", optimize=True)
        print(f"  [In-App Logo] {ui_logo_path}: 512x512 px")

    # Remove any conflicting non-density template vector foreground if present
    dummy_vector = "app/src/main/res/drawable/ic_launcher_foreground.xml"
    if os.path.exists(dummy_vector):
        os.remove(dummy_vector)
        print(f"  Removed obsolete dummy template vector: {dummy_vector}")

    print("All icons successfully generated!")
    return True

if __name__ == "__main__":
    generate_icons()
