#!/usr/bin/env python3
"""Generate stylized game cover art for all ROMs on the device."""

import subprocess
import os
import hashlib
import math
from PIL import Image, ImageDraw, ImageFont

COVERS_DIR = "/Users/stepankaiser/Git/retro-handheld/launcher-app/covers"
W, H = 280, 260  # 2x CSS card size for retina sharpness

# System color palettes: [bg1, bg2, accent, text_shadow]
PALETTES = {
    'GBA': [(60, 20, 140), (120, 50, 200), (180, 100, 255), (40, 10, 100)],
    'GB':  [(15, 60, 20), (30, 110, 40), (80, 200, 100), (10, 40, 15)],
    'GBC': [(70, 15, 110), (150, 30, 160), (220, 80, 255), (50, 10, 80)],
    'NES': [(140, 15, 15), (200, 40, 40), (255, 80, 80), (100, 10, 10)],
    'SNES': [(80, 50, 5), (170, 120, 20), (255, 200, 60), (60, 35, 0)],
    'Genesis': [(5, 55, 45), (15, 130, 100), (40, 220, 170), (0, 40, 30)],
    'N64': [(80, 5, 65), (170, 30, 150), (255, 80, 230), (60, 0, 50)],
    'PS1': [(20, 40, 80), (50, 80, 160), (100, 150, 255), (15, 30, 60)],
    'PSP': [(40, 45, 60), (70, 85, 115), (120, 150, 200), (30, 35, 50)],
    'Doom': [(100, 5, 5), (180, 20, 20), (255, 50, 30), (70, 0, 0)],
    'Dreamcast': [(5, 45, 80), (15, 110, 170), (40, 180, 255), (0, 30, 60)],
    'Arcade': [(80, 50, 5), (170, 130, 15), (255, 210, 40), (60, 35, 0)],
}

# System display names
SYS_NAMES = {
    'GBA': 'GBA', 'GB': 'GAME BOY', 'GBC': 'GBC', 'NES': 'NES',
    'SNES': 'SNES', 'Genesis': 'GENESIS', 'N64': 'N64', 'PS1': 'PS1',
    'PSP': 'PSP', 'Doom': 'DOOM', 'Dreamcast': 'DC', 'Arcade': 'ARCADE'
}


def name_hash(s):
    return int(hashlib.md5(s.encode()).hexdigest()[:8], 16)


def clean_name(filename):
    name = os.path.splitext(filename)[0]
    # Insert spaces before capitals in camelCase
    result = []
    for i, c in enumerate(name):
        if c.isupper() and i > 0 and name[i-1].islower():
            result.append(' ')
        result.append(c)
    name = ''.join(result)
    name = name.replace('_', ' ').replace('-', ' ')
    # Remove region info
    paren = name.find('(')
    if paren >= 0:
        name = name[:paren]
    # Split numbers from letters
    import re
    name = re.sub(r'(\d)([a-zA-Z])', r'\1 \2', name)
    name = re.sub(r'([a-zA-Z])(\d)', r'\1 \2', name)
    # Title case
    words = name.split()
    titled = []
    for i, w in enumerate(words):
        if len(w) <= 3 and w == w.upper():
            titled.append(w)
        elif i == 0:
            titled.append(w.capitalize())
        else:
            titled.append(w.capitalize())
    return ' '.join(titled).strip()


def lerp_color(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def draw_cover(filename, system, output_path):
    """Generate a stylized cover art image."""
    h = name_hash(filename + system)
    palette = PALETTES.get(system, PALETTES['GBA'])
    bg1, bg2, accent, shadow = palette

    # Vary colors slightly per game
    variation = ((h % 60) - 30)
    bg1 = tuple(max(0, min(255, c + variation)) for c in bg1)
    bg2 = tuple(max(0, min(255, c + variation // 2)) for c in bg2)

    img = Image.new('RGB', (W, H), bg1)
    draw = ImageDraw.Draw(img)

    # Draw gradient background
    for y in range(H):
        t = y / H
        # Create a diagonal gradient
        color = lerp_color(bg1, bg2, t)
        draw.line([(0, y), (W, y)], fill=color)

    # Draw decorative elements based on hash
    pattern_type = h % 5

    if pattern_type == 0:
        # Circles
        for i in range(3):
            cx = (h >> (i * 4)) % W
            cy = (h >> (i * 4 + 2)) % H
            r = 30 + (h >> (i * 3)) % 40
            circle_color = tuple(min(255, c + 20) for c in bg2) + (30,)
            overlay = Image.new('RGBA', (W, H), (0, 0, 0, 0))
            od = ImageDraw.Draw(overlay)
            od.ellipse([cx - r, cy - r, cx + r, cy + r], fill=circle_color)
            img = Image.alpha_composite(img.convert('RGBA'), overlay).convert('RGB')
            draw = ImageDraw.Draw(img)

    elif pattern_type == 1:
        # Diagonal lines
        spacing = 20 + h % 15
        line_color = tuple(min(255, c + 15) for c in bg2)
        for offset in range(-H, W + H, spacing):
            draw.line([(offset, 0), (offset + H, H)], fill=line_color, width=1)

    elif pattern_type == 2:
        # Diamond shape
        cx, cy = W // 2, H // 2 - 10
        size = 50 + h % 30
        diamond_color = tuple(min(255, c + 25) for c in bg2)
        points = [(cx, cy - size), (cx + size, cy), (cx, cy + size), (cx - size, cy)]
        draw.polygon(points, outline=diamond_color, width=2)

    elif pattern_type == 3:
        # Grid dots
        spacing = 18 + h % 10
        dot_color = tuple(min(255, c + 20) for c in bg2)
        for x in range(0, W, spacing):
            for y in range(0, H, spacing):
                draw.ellipse([x - 1, y - 1, x + 1, y + 1], fill=dot_color)

    elif pattern_type == 4:
        # Horizontal bands
        band_y = 40 + h % 60
        band_color = tuple(min(255, c + 20) for c in bg2)
        draw.rectangle([0, band_y, W, band_y + 3], fill=band_color)
        draw.rectangle([0, band_y + 30, W, band_y + 32], fill=band_color)

    # Draw border frame
    border_color = tuple(min(255, c + 30) for c in accent)
    draw.rectangle([4, 4, W - 5, H - 5], outline=border_color + (60,) if len(border_color) == 3 else border_color, width=1)

    # Draw game title
    title = clean_name(filename)
    try:
        # Try to find a nice font
        for font_path in [
            '/System/Library/Fonts/SFCompact.ttf',
            '/System/Library/Fonts/Helvetica.ttc',
            '/System/Library/Fonts/SFNSDisplay.ttf',
            '/Library/Fonts/Arial Bold.ttf',
        ]:
            if os.path.exists(font_path):
                font_large = ImageFont.truetype(font_path, 28)
                font_small = ImageFont.truetype(font_path, 14)
                break
        else:
            font_large = ImageFont.load_default()
            font_small = ImageFont.load_default()
    except Exception:
        font_large = ImageFont.load_default()
        font_small = ImageFont.load_default()

    # Word wrap the title
    max_width = W - 30
    lines = []
    words = title.split()
    current_line = ""
    for word in words:
        test = (current_line + " " + word).strip()
        bbox = draw.textbbox((0, 0), test, font=font_large)
        if bbox[2] - bbox[0] > max_width and current_line:
            lines.append(current_line)
            current_line = word
        else:
            current_line = test
    if current_line:
        lines.append(current_line)
    lines = lines[:3]  # Max 3 lines

    # Position text - centered vertically
    line_height = 32
    total_height = len(lines) * line_height
    start_y = (H - total_height) // 2 - 10

    # Draw text shadow then text
    for i, line in enumerate(lines):
        y = start_y + i * line_height
        # Shadow
        draw.text((W // 2 + 2, y + 2), line, fill=shadow, font=font_large, anchor="mt")
        # Main text
        draw.text((W // 2, y), line, fill=(255, 255, 255), font=font_large, anchor="mt")

    # System badge at bottom
    badge_text = SYS_NAMES.get(system, system)
    badge_bbox = draw.textbbox((0, 0), badge_text, font=font_small)
    badge_w = badge_bbox[2] - badge_bbox[0] + 16
    badge_h = 20
    badge_x = W - badge_w - 8
    badge_y = H - badge_h - 8

    # Badge background
    draw.rounded_rectangle([badge_x, badge_y, badge_x + badge_w, badge_y + badge_h],
                            radius=4, fill=(0, 0, 0, 150) if hasattr(draw, 'rounded_rectangle') else (20, 20, 30))
    draw.text((badge_x + badge_w // 2, badge_y + badge_h // 2), badge_text,
              fill=(200, 200, 220), font=font_small, anchor="mm")

    # Add subtle vignette effect
    overlay = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    for i in range(30):
        alpha = int((30 - i) * 2.5)
        od.rectangle([i, i, W - i - 1, H - i - 1], outline=(0, 0, 0, alpha))
    img = Image.alpha_composite(img.convert('RGBA'), overlay).convert('RGB')

    img.save(output_path, 'PNG', optimize=True)


def get_rom_list():
    """Get all ROM files from the device via ADB."""
    result = subprocess.run(
        ['adb', 'shell', 'find /sdcard/RetroHandheld/roms -type f '
         '\\( -name "*.gb" -o -name "*.gbc" -o -name "*.gba" -o -name "*.nes" '
         '-o -name "*.sfc" -o -name "*.smc" -o -name "*.bin" -o -name "*.n64" '
         '-o -name "*.z64" -o -name "*.wad" -o -name "*.zip" \\)'],
        capture_output=True, text=True
    )
    games = []
    seen = set()
    for line in result.stdout.strip().split('\n'):
        if not line.strip():
            continue
        # Extract system from path: /sdcard/RetroHandheld/roms/SYSTEM/...
        parts = line.strip().split('/')
        if len(parts) < 5:
            continue
        system = parts[4]  # The system folder name
        filename = os.path.basename(line.strip())

        # Deduplicate
        key = (system, filename.lower())
        if key in seen:
            continue
        seen.add(key)
        games.append((system, filename))
    return games


def main():
    games = get_rom_list()
    print(f"Found {len(games)} unique games")

    generated = 0
    skipped = 0

    for system, filename in games:
        system_dir = os.path.join(COVERS_DIR, system)
        os.makedirs(system_dir, exist_ok=True)

        base_name = os.path.splitext(filename)[0]
        output_path = os.path.join(system_dir, base_name + ".png")

        # Skip if cover already exists (e.g., downloaded Pokemon art)
        if os.path.exists(output_path):
            skipped += 1
            continue

        try:
            draw_cover(filename, system, output_path)
            generated += 1
        except Exception as e:
            print(f"Error generating {filename}: {e}")

    print(f"Generated {generated} covers, skipped {skipped} existing")
    print(f"Total covers: {generated + skipped}")


if __name__ == '__main__':
    main()
