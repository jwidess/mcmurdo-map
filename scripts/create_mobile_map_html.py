#!/usr/bin/env python3
"""
create_mobile_map_html.py
Creates map_view.html: optimized mobile map interface embedding the McMurdo Station SVG.
"""

import re
import os
import math

with open("app/src/main/assets/map_core.svg", "r", encoding="utf-8") as f:
    svg_core = f.read()

# Manual placements for odd-shaped facilities
# Format: "BUILDING_ID": {"cx": float, "cy": float, "angle": float, "text": str}
MANUAL_OVERRIDES = {
    # Crary (B001): placed along Phase 1 top section
    "B001": {"cx": 863.0, "cy": 1569.0, "angle": 36.0, "text": "001"},
    # Galley (B155):
    "B155": {"cx": 874.0, "cy": 1412.0, "angle": 43.8, "text": "155"},
    # Building 140: Fire Station (horizontal roofline)
    "B140": {"cx": 979.4, "cy": 1278.2, "angle": -7.5, "text": "140"},
    # Building 143: Trade / Mechanical Shops (aligned along main long axis)
    "B143": {"cx": 958.0, "cy": 1150.0, "angle": -52.6, "text": "143"},
    # Building 136: Central Warehouse (along main body axis)
    "B136": {"cx": 1005.0, "cy": 1480.0, "angle": 76.9, "text": "136"},
    # Building 004: Facilities / Admin (centered along main horizontal arm)
    "B004": {"cx": 1014.0, "cy": 1558.0, "angle": -11.1, "text": "004"},
    # Building 076: EOC / Office (aligned along top wing)
    "B076": {"cx": 778.0, "cy": 1487.0, "angle": -48.6, "text": "076"},
    # Building 189: (aligned along long wall)
    "B189": {"cx": 800.8, "cy": 1516.6, "angle": -47.5, "text": "189"},
    # Building 142: (horizontal along roofline)
    "B142": {"cx": 911.0, "cy": 1474.0, "angle": -2.5, "text": "142"},
    # Building 164: (centered)
    "B164": {"cx": 823.3, "cy": 1353.2, "angle": 42.2, "text": "164"},
    # Building 305: (centered along long front-to-back axis)
    "B305": {"cx": 777.5, "cy": 1315.3, "angle": 35.4, "text": "305"},
    # Building 165: (aligned along 35px wall)
    "B165": {"cx": 766.3, "cy": 1551.4, "angle": 36.3, "text": "165"},
    # Building 196: (horizontal in square building)
    "B196": {"cx": 656.9, "cy": 1485.4, "angle": 0.0, "text": "196"},
    # Building 198: (centered in body)
    "B198": {"cx": 658.9, "cy": 1526.1, "angle": 89.1, "text": "198"},
    # Building 115: Ice Pier
    "B115": {"cx": 450.0, "cy": 1272.0, "angle": -24.5, "text": "115"},
    # Building 306: Cargo Yard (horizontal in large yard)
    "B306": {"cx": 1070.0, "cy": 1260.0, "angle": 0.0, "text": "306"},
    # Building 075 & 129: Twin buildings
    "B075": {"cx": 836.3, "cy": 1749.6, "angle": 45.0, "text": "075"},
    "B129": {"cx": 825.0, "cy": 1763.9, "angle": 45.0, "text": "129"},
    # Building 221: Hillside facility (aligned along long wall)
    "B221": {"cx": 2020.1, "cy": 1403.8, "angle": 34.7, "text": "221"},
    # Building 160 & 073:
    "B160": {"cx": 1083.8, "cy": 1692.6, "angle": 87.6, "text": "160"},
    # Building 108: Aligned with wall
    "B108": {"cx": 879.1, "cy": 1492.1, "angle": 43.5, "text": "108"},
    # Building 107: Aligned with wall
    "B107": {"cx": 842.0, "cy": 1471.2, "angle": -46.1, "text": "107"},
    # Building 078: Aligned with wall
    "B078": {"cx": 801.1, "cy": 1466.7, "angle": 41.4, "text": "078"},
    # Building 132 & 141: Aligned with wall
    "B132": {"cx": 866.4, "cy": 1169.0, "angle": -48.5, "text": "132"},
    "B141": {"cx": 848.4, "cy": 1153.0, "angle": -48.5, "text": "141"},
    # Building 192: Aligned with wall
    "B192": {"cx": 828.1, "cy": 1199.8, "angle": 42.6, "text": "192"},
    # Building 157: Aligned with wall
    "B157": {"cx": 768.9, "cy": 1664.2, "angle": -67.5, "text": "157"},
    # Building 159: (Chapel area, 45°)
    "B159": {"cx": 750.2, "cy": 1699.1, "angle": 45.0, "text": "159"},
}

def generate_building_labels(svg_text):
    labels_xml = []
    # 1. Polygons and Polylines
    poly_matches = re.finditer(
        r"<(polygon|polyline)\b[^>]*\bid=[\"'](B\d+[\w\-]*)[\"'][^>]*points=[\"']([^\"']+)[\"']",
        svg_text
    )
    for m in poly_matches:
        bid = m.group(2)
        if bid in MANUAL_OVERRIDES:
            ov = MANUAL_OVERRIDES[bid]
            if ov is not None:
                ocx, ocy, oang, otxt = ov["cx"], ov["cy"], ov["angle"], ov["text"]
                labels_xml.append(
                    f'<text class="bldg-label" x="{ocx}" y="{ocy}" '
                    f'transform="rotate({oang} {ocx} {ocy})">{otxt}</text>'
                )
            continue

        pts = [float(x) for x in m.group(3).split()]
        coords = [(pts[i], pts[i+1]) for i in range(0, len(pts)-1, 2)]
        n = len(coords)
        if n < 3:
            continue

        # Polygon Area and Centroid by Green's Theorem
        area2 = 0.0
        cx = 0.0
        cy = 0.0
        for i in range(n):
            x0, y0 = coords[i]
            x1, y1 = coords[(i+1)%n]
            cross = x0 * y1 - x1 * y0
            area2 += cross
            cx += (x0 + x1) * cross
            cy += (y0 + y1) * cross

        area = abs(area2 / 2.0)
        if abs(area2) > 1e-4:
            cx /= (3.0 * area2)
            cy /= (3.0 * area2)
        else:
            cx = sum(p[0] for p in coords) / n
            cy = sum(p[1] for p in coords) / n

        # Find longest edge
        edges = []
        for i in range(n):
            p0 = coords[i]
            p1 = coords[(i+1)%n]
            dx = p1[0] - p0[0]
            dy = p1[1] - p0[1]
            length = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(dy, dx))
            while ang > 90: ang -= 180
            while ang < -90: ang += 180
            edges.append((length, ang))
        edges.sort(key=lambda e: e[0], reverse=True)

        xs = [p[0] for p in coords]
        ys = [p[1] for p in coords]
        w = max(xs) - min(xs)
        h = max(ys) - min(ys)
        major = max(w, h)
        minor = min(w, h)

        if major < 18 or minor < 6 or area < 80:
            continue

        dominant_angle = edges[0][1] if edges else 0.0
        while dominant_angle > 88: dominant_angle -= 180
        while dominant_angle < -88: dominant_angle += 180

        text = bid[1:] if bid.startswith("B") else bid
        cx_r, cy_r, ang_r = round(cx, 1), round(cy, 1), round(dominant_angle, 1)
        labels_xml.append(
            f'<text class="bldg-label" x="{cx_r}" y="{cy_r}" '
            f'transform="rotate({ang_r} {cx_r} {cy_r})">{text}</text>'
        )

    # 2. Rectangles
    rect_matches = re.finditer(r"<rect\b[^>]*\bid=[\"'](B\d+[\w\-]*)[\"'][^>]*>", svg_text)
    for m in rect_matches:
        full_tag = m.group(0)
        bid = m.group(1)
        if bid in MANUAL_OVERRIDES:
            ov = MANUAL_OVERRIDES[bid]
            if ov is not None:
                ocx, ocy, oang, otxt = ov["cx"], ov["cy"], ov["angle"], ov["text"]
                labels_xml.append(
                    f'<text class="bldg-label" x="{ocx}" y="{ocy}" '
                    f'transform="rotate({oang} {ocx} {ocy})">{otxt}</text>'
                )
            continue

        m_x = re.search(r"\bx=[\"']([-\d.]+)[\"']", full_tag)
        m_y = re.search(r"\by=[\"']([-\d.]+)[\"']", full_tag)
        m_w = re.search(r"\bwidth=[\"']([-\d.]+)[\"']", full_tag)
        m_h = re.search(r"\bheight=[\"']([-\d.]+)[\"']", full_tag)
        if not (m_x and m_y and m_w and m_h):
            continue

        x, y, w, h = float(m_x.group(1)), float(m_y.group(1)), float(m_w.group(1)), float(m_h.group(1))

        m_tr = re.search(r"transform=[\"']([^\"']+)[\"']", full_tag)
        tr = m_tr.group(1) if m_tr else ""
        m_trans = re.search(r"translate\(\s*([-\d.]+)\s+([-\d.]+)\s*\)", tr)
        tx = float(m_trans.group(1)) if m_trans else 0.0
        ty = float(m_trans.group(2)) if m_trans else 0.0
        m_rot = re.search(r"rotate\(\s*([-\d.]+)\s*\)", tr)
        rot_deg = float(m_rot.group(1)) if m_rot else 0.0
        rot_rad = math.radians(rot_deg)

        lx = x + w / 2.0
        ly = y + h / 2.0
        cx = math.cos(rot_rad) * lx - math.sin(rot_rad) * ly + tx
        cy = math.sin(rot_rad) * lx + math.cos(rot_rad) * ly + ty

        major = max(w, h)
        minor = min(w, h)
        if major < 18 or minor < 6 or (w * h) < 80:
            continue

        base_angle = 90.0 if h > w else 0.0
        total_angle = rot_deg + base_angle
        while total_angle > 88: total_angle -= 180
        while total_angle < -88: total_angle += 180

        text = bid[1:] if bid.startswith("B") else bid
        cx_r, cy_r, ang_r = round(cx, 1), round(cy, 1), round(total_angle, 1)
        labels_xml.append(
            f'<text class="bldg-label" x="{cx_r}" y="{cy_r}" '
            f'transform="rotate({ang_r} {cx_r} {cy_r})">{text}</text>'
        )

    labels_group = '<g id="building-labels" pointer-events="none">\n    ' + '\n    '.join(labels_xml) + '\n  </g>'
    return labels_group

# Generate labels and inject before </svg>
labels_group = generate_building_labels(svg_core)
svg_core = svg_core.replace('</svg>', f'  {labels_group}\n  <g id="tap-effects" pointer-events="none"></g>\n</svg>')

# Configure SVG tag
svg_core = re.sub(
    r'<svg\b[^>]*>',
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 3648.11 2160.44" id="map-site" preserveAspectRatio="xMidYMid slice">',
    svg_core,
    count=1
)

html_template = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=no, viewport-fit=cover">
  <title>McMurdo Station Map</title>
  <style>
    * {
      box-sizing: border-box;
      -webkit-tap-highlight-color: transparent;
      user-select: none;
      -webkit-user-select: none;
    }

    html, body {
      margin: 0;
      padding: 0;
      width: 100%;
      height: 100%;
      overflow: hidden;
      background-color: #daecf6;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Open Sans", sans-serif;
      touch-action: none;
    }

    #map-container {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      overflow: hidden;
      background-color: #daecf6;
    }

    svg#map-site {
      width: 100%;
      height: 100%;
      display: block;
      cursor: grab;
      will-change: viewBox;
    }

    svg#map-site:active {
      cursor: grabbing;
    }

    svg#map-site #Roads_and_Trails * {
      stroke: #c2b2a1 !important;
    }

    /* All clickable buildings & groups */
    svg#map-site #Buildings_and_Structures *[id],
    svg#map-site #Storage_Tanks *[id],
    svg#map-site #Construction *[id],
    svg#map-site #SCOTT_BASE,
    svg#map-site #SCOTT_BASE *,
    svg#map-site #TURBINES,
    svg#map-site #TURBINES *,
    svg#map-site #POWER_STORE,
    svg#map-site #POWER_STORE * {
      cursor: pointer;
      pointer-events: visiblePainted;
    }

    /* Active selected building - Vibrant Blaze Safety Orange with pulsating navy/white border */
    .building-active,
    .building-active * {
      fill: #ff5500 !important;
      paint-order: stroke fill !important;
      animation: pulse-stroke 0.85s infinite alternate ease-in-out !important;
    }

    @keyframes pulse-stroke {
      0% {
        stroke: #162e51;
        stroke-width: 3.5px;
      }
      100% {
        stroke: #ffffff;
        stroke-width: 7.0px;
      }
    }

    /* High-Performance Category Highlights (Vector outer strokes - No GPU filter lag) */
    .cat-highlight-aed {
      fill: #d31245 !important;
      stroke: #ffffff !important;
      stroke-width: 4.5px !important;
      paint-order: stroke fill !important;
    }

    .cat-highlight-science {
      fill: #0085ca !important;
      stroke: #ffffff !important;
      stroke-width: 4.5px !important;
      paint-order: stroke fill !important;
    }

    .cat-highlight-dining {
      fill: #e66f0e !important;
      stroke: #ffffff !important;
      stroke-width: 4.5px !important;
      paint-order: stroke fill !important;
    }

    .cat-highlight-dorms {
      fill: #8168b3 !important;
      stroke: #ffffff !important;
      stroke-width: 4.5px !important;
      paint-order: stroke fill !important;
    }

    .cat-highlight-medical {
      fill: #d31245 !important;
      stroke: #ffffff !important;
      stroke-width: 5px !important;
      paint-order: stroke fill !important;
    }

    .cat-highlight-fitness {
      fill: #162e51 !important;
      stroke: #ffffff !important;
      stroke-width: 4.5px !important;
      paint-order: stroke fill !important;
    }

    /* Item 4: Prominent outer amber glow/stroke for smoking shelters (blue inside) */
    .cat-highlight-smoking {
      fill: #38bdf8 !important;
      stroke: #f59e0b !important;
      stroke-width: 8px !important;
      paint-order: stroke fill !important;
    }

    /* Touch Controls Overlay */
    .touch-controls {
      position: absolute;
      right: 16px;
      bottom: 12px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      z-index: 100;
    }

    .touch-btn {
      position: relative;
      width: 44px;
      height: 44px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.95);
      backdrop-filter: blur(8px);
      -webkit-backdrop-filter: blur(8px);
      border: 1.5px solid #8a8980;
      color: #162e51;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.16);
      transition: transform 0.1s ease-out, background 0.1s, color 0.1s;
      padding: 0;
      outline: none;
    }

    /* Item 3: Expanded touch hitbox (~56x60px hit area) */
    .touch-btn::after {
      content: "";
      position: absolute;
      top: -6px;
      bottom: -6px;
      left: -8px;
      right: -8px;
      border-radius: 50%;
    }

    .touch-btn svg {
      width: 22px;
      height: 22px;
      fill: currentColor;
      pointer-events: none;
    }

    .touch-btn:active {
      transform: scale(0.92);
      background: #e66f0e;
      border-color: #e66f0e;
      color: #ffffff;
    }

    /* Compass Rose at bottom-left (shifted down & left) */
    .compass-overlay {
      position: absolute;
      bottom: 6px;
      left: 6px;
      width: 124px;
      height: 124px;
      z-index: 100;
      opacity: 0.92;
      pointer-events: none;
      filter: drop-shadow(0 2px 10px rgba(0, 0, 0, 0.35));
    }

    @media (orientation: landscape) {
      .touch-controls {
        bottom: 10px;
        right: 14px;
      }
      .compass-overlay {
        width: 86px;
        height: 86px;
        bottom: 8px;
        left: 8px;
      }
    }

    .compass-overlay img {
      width: 100%;
      height: 100%;
    }

    /* Item 1: High-contrast building number labels oriented along longest axis */
    .bldg-label {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Open Sans", sans-serif;
      font-size: 6.8px;
      font-weight: 700;
      letter-spacing: 0.4px;
      text-anchor: middle;
      dominant-baseline: central;
      fill: #ffffff;
      stroke: #162e51;
      stroke-width: 1.6px;
      paint-order: stroke fill;
      pointer-events: none;
      user-select: none;
      -webkit-user-select: none;
    }
  </style>
</head>
<body>

  <!-- Compass at bottom-left -->
  <div class="compass-overlay">
    <img src="assets/img/compass.svg" alt="Compass">
  </div>

  <div id="map-container">
""" + svg_core + """
  </div>

  <div class="touch-controls">
    <!-- Zoom In (+) -->
    <button class="touch-btn" id="btn-zoom-in" title="Zoom In">
      <svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
    </button>
    <!-- Zoom Out (−) -->
    <button class="touch-btn" id="btn-zoom-out" title="Zoom Out">
      <svg viewBox="0 0 24 24"><path d="M19 13H5v-2h14v2z"/></svg>
    </button>
    <!-- Reset View (Reload icon) -->
    <button class="touch-btn" id="btn-reset" title="Reset View">
      <svg viewBox="0 0 24 24"><path d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"/></svg>
    </button>
  </div>

  <script>
    (function() {
      const svg = document.getElementById('map-site');
      const container = document.getElementById('map-container');

      const NATIVE_SVG_W = 3648.11;
      const NATIVE_SVG_H = 2160.44;

      const STATION_CENTER = { x: 875, y: 1410 };
      const DEFAULT_SPAN_W = 390;

      let currentVB = { x: 0, y: 0, w: 1000, h: 1000 };
      let targetVB = { x: 0, y: 0, w: 1000, h: 1000 };

      const MIN_WIDTH = 60;
      const MAX_WIDTH = NATIVE_SVG_W * 1.6;
      const TAP_SLOP_PX = 18;

      function getScreenAspect() {
        const w = container.clientWidth || window.innerWidth || 1080;
        const h = container.clientHeight || window.innerHeight || 2400;
        return w / h;
      }

      function updateViewBox(vb) {
        svg.setAttribute('viewBox', `${vb.x} ${vb.y} ${vb.w} ${vb.h}`);
      }

      function buildTargetVB(centerX, centerY, width) {
        const aspect = getScreenAspect();
        const clampedW = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
        const clampedH = clampedW / aspect;
        return {
          x: centerX - clampedW / 2,
          y: centerY - clampedH / 2,
          w: clampedW,
          h: clampedH
        };
      }

      // Smooth animation loop
      let animFrame = null;
      function animateViewBox() {
        const lerp = 0.24;
        currentVB.x += (targetVB.x - currentVB.x) * lerp;
        currentVB.y += (targetVB.y - currentVB.y) * lerp;
        currentVB.w += (targetVB.w - currentVB.w) * lerp;
        currentVB.h += (targetVB.h - currentVB.h) * lerp;

        updateViewBox(currentVB);

        const dx = Math.abs(targetVB.x - currentVB.x);
        const dy = Math.abs(targetVB.y - currentVB.y);
        const dw = Math.abs(targetVB.w - currentVB.w);

        if (dx > 0.05 || dy > 0.05 || dw > 0.05) {
          animFrame = requestAnimationFrame(animateViewBox);
        } else {
          currentVB = { ...targetVB };
          updateViewBox(currentVB);
          animFrame = null;
        }
      }

      function setTargetVB(newVB, animate = true) {
        const aspect = getScreenAspect();
        newVB.w = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, newVB.w));
        newVB.h = newVB.w / aspect;

        targetVB = newVB;
        if (animate) {
          if (!animFrame) animFrame = requestAnimationFrame(animateViewBox);
        } else {
          if (animFrame) {
            cancelAnimationFrame(animFrame);
            animFrame = null;
          }
          currentVB = { ...targetVB };
          updateViewBox(currentVB);
        }
      }

      // --- Gesture Handling ---
      let userMovedSinceFocus = false;
      let isDragging = false;
      let initialTouchPos = { x: 0, y: 0 };
      let lastTouchPos = { x: 0, y: 0 };
      let startTime = 0;
      let lastPinchDist = 0;
      let lastPinchCenter = { x: 0, y: 0 };
      let pinchTransitionCooldown = 0;
      let lastTouchEndTime = 0;

      container.addEventListener('touchstart', (e) => {
        if (e.touches.length === 1) {
          isDragging = false;
          initialTouchPos = { x: e.touches[0].clientX, y: e.touches[0].clientY };
          lastTouchPos = { x: e.touches[0].clientX, y: e.touches[0].clientY };
          startTime = Date.now();
        } else if (e.touches.length === 2) {
          isDragging = true;
          userMovedSinceFocus = true;
          lastPinchDist = Math.hypot(
            e.touches[0].clientX - e.touches[1].clientX,
            e.touches[0].clientY - e.touches[1].clientY
          );
          lastPinchCenter = {
            x: (e.touches[0].clientX + e.touches[1].clientX) / 2,
            y: (e.touches[0].clientY + e.touches[1].clientY) / 2
          };
        }
      }, { passive: true });

      container.addEventListener('touchmove', (e) => {
        if (e.touches.length === 1) {
          if (Date.now() - pinchTransitionCooldown < 120) {
            lastTouchPos = { x: e.touches[0].clientX, y: e.touches[0].clientY };
            return;
          }

          const totalDistFromStart = Math.hypot(
            e.touches[0].clientX - initialTouchPos.x,
            e.touches[0].clientY - initialTouchPos.y
          );

          if (totalDistFromStart > TAP_SLOP_PX) {
            isDragging = true;
          }

          if (isDragging) {
            userMovedSinceFocus = true;
            const dx = e.touches[0].clientX - lastTouchPos.x;
            const dy = e.touches[0].clientY - lastTouchPos.y;

            const rect = container.getBoundingClientRect();
            const scaleX = targetVB.w / rect.width;
            const scaleY = targetVB.h / rect.height;

            setTargetVB({
              x: targetVB.x - dx * scaleX,
              y: targetVB.y - dy * scaleY,
              w: targetVB.w,
              h: targetVB.h
            }, false);

            lastTouchPos = { x: e.touches[0].clientX, y: e.touches[0].clientY };
          }
        } else if (e.touches.length === 2) {
          isDragging = true;
          userMovedSinceFocus = true;
          const currentDist = Math.hypot(
            e.touches[0].clientX - e.touches[1].clientX,
            e.touches[0].clientY - e.touches[1].clientY
          );
          const currentCenter = {
            x: (e.touches[0].clientX + e.touches[1].clientX) / 2,
            y: (e.touches[0].clientY + e.touches[1].clientY) / 2
          };

          if (lastPinchDist > 5 && currentDist > 5) {
            const scaleFactor = lastPinchDist / currentDist;

            let newW = targetVB.w * scaleFactor;
            if (newW < MIN_WIDTH) newW = MIN_WIDTH;
            if (newW > MAX_WIDTH) newW = MAX_WIDTH;

            if (Math.abs(newW - targetVB.w) > 0.1) {
              const rect = container.getBoundingClientRect();
              const centerSvgX = targetVB.x + (currentCenter.x - rect.left) * (targetVB.w / rect.width);
              const centerSvgY = targetVB.y + (currentCenter.y - rect.top) * (targetVB.h / rect.height);

              const aspect = getScreenAspect();
              const newH = newW / aspect;

              setTargetVB({
                x: centerSvgX - (currentCenter.x - rect.left) * (newW / rect.width),
                y: centerSvgY - (currentCenter.y - rect.top) * (newH / rect.height),
                w: newW,
                h: newH
              }, false);
            }
          }

          lastPinchDist = currentDist;
          lastPinchCenter = currentCenter;
        }
      }, { passive: true });

      container.addEventListener('touchend', (e) => {
        if (e.touches.length === 1) {
          lastTouchPos = { x: e.touches[0].clientX, y: e.touches[0].clientY };
          pinchTransitionCooldown = Date.now();
          lastPinchDist = 0;
        } else if (e.touches.length === 0) {
          const touch = e.changedTouches[0];
          const totalMovement = Math.hypot(
            touch.clientX - initialTouchPos.x,
            touch.clientY - initialTouchPos.y
          );
          const duration = Date.now() - startTime;

          if (totalMovement <= TAP_SLOP_PX && duration < 500) {
            // Prevent synthetic mouse/click events that would otherwise deselect post-pan
            e.preventDefault();
            lastTouchEndTime = Date.now();
            handleTapAt(touch.clientX, touch.clientY);
          }
          lastPinchDist = 0;
          pinchTransitionCooldown = Date.now();
        }
      });

      // Mouse drag fallback for PC testing
      let isMouseDown = false;
      let startMouse = { x: 0, y: 0 };
      let lastMouse = { x: 0, y: 0 };
      let mouseStartTime = 0;

      container.addEventListener('mousedown', (e) => {
        if (Date.now() - lastTouchEndTime < 650) return;
        if (e.button !== 0) return;
        isMouseDown = true;
        startMouse = { x: e.clientX, y: e.clientY };
        lastMouse = { x: e.clientX, y: e.clientY };
        mouseStartTime = Date.now();
      });

      window.addEventListener('mousemove', (e) => {
        if (!isMouseDown) return;
        const dx = e.clientX - lastMouse.x;
        const dy = e.clientY - lastMouse.y;
        if (Math.hypot(e.clientX - startMouse.x, e.clientY - startMouse.y) > 6) {
          userMovedSinceFocus = true;
          const rect = container.getBoundingClientRect();
          setTargetVB({
            x: targetVB.x - dx * (targetVB.w / rect.width),
            y: targetVB.y - dy * (targetVB.h / rect.height),
            w: targetVB.w,
            h: targetVB.h
          }, false);
          lastMouse = { x: e.clientX, y: e.clientY };
        }
      });

      window.addEventListener('mouseup', (e) => {
        if (Date.now() - lastTouchEndTime < 650) return;
        if (!isMouseDown) return;
        isMouseDown = false;
        const dist = Math.hypot(e.clientX - startMouse.x, e.clientY - startMouse.y);
        if (dist <= 8 && (Date.now() - mouseStartTime < 450)) {
          handleTapAt(e.clientX, e.clientY);
        }
      });

      // Suppress any delayed synthetic clicks that browsers might fire post-touch
      container.addEventListener('click', (e) => {
        if (Date.now() - lastTouchEndTime < 650) {
          e.preventDefault();
          e.stopPropagation();
        }
      }, true);

      container.addEventListener('wheel', (e) => {
        e.preventDefault();
        userMovedSinceFocus = true;
        const factor = e.deltaY < 0 ? 0.8 : 1.25;
        let newW = targetVB.w * factor;
        if (newW < MIN_WIDTH) newW = MIN_WIDTH;
        if (newW > MAX_WIDTH) newW = MAX_WIDTH;

        if (Math.abs(newW - targetVB.w) > 0.1) {
          const rect = container.getBoundingClientRect();
          const mouseX = e.clientX - rect.left;
          const mouseY = e.clientY - rect.top;

          const svgX = targetVB.x + mouseX * (targetVB.w / rect.width);
          const svgY = targetVB.y + mouseY * (targetVB.h / rect.height);

          const aspect = getScreenAspect();
          const newH = newW / aspect;

          setTargetVB({
            x: svgX - mouseX * (newW / rect.width),
            y: svgY - mouseY * (newH / rect.height),
            w: newW,
            h: newH
          }, true);
        }
      }, { passive: false });

      // Robust building detection helper with layer-group filtering and SCOTT_BASE/TURBINES/POWER_STORE support
      function getBuildingIdFromElement(el) {
        if (!el) return null;

        // Group parents (Scott Base, Turbines, Power Store)
        const groupParent = el.closest('#SCOTT_BASE, #TURBINES, #POWER_STORE');
        if (groupParent) {
          return groupParent.id;
        }

        const ignoredIds = new Set([
          'Buildings_and_Structures',
          'Storage_Tanks',
          'Construction',
          'Water_and_Sea_Ice',
          'Shoreline',
          'Roads_and_Trails',
          'map-site'
        ]);

        // Direct element ID check (must not be an internal _ id or layer container group)
        if (el.id && !el.id.startsWith('_') && !ignoredIds.has(el.id)) {
          if (el.closest('#Buildings_and_Structures, #Storage_Tanks, #Construction')) {
            return el.id;
          }
        }

        // Parent element ID check (must match building ID pattern, never a layer container group)
        if (el.parentElement && el.parentElement.id && !el.parentElement.id.startsWith('_') && !ignoredIds.has(el.parentElement.id)) {
          if (/^(B\\d+|UNKNOWN)/i.test(el.parentElement.id)) {
            return el.parentElement.id;
          }
        }
        return null;
      }

      // Item 1: Subtle, fast grey tap ripple anchored in SVG coordinate space (moves with map)
      const tapEffects = document.getElementById('tap-effects');
      let lastRippleTime = 0;

      function showTapRipple(clientX, clientY) {
        if (!clientX || !clientY || !tapEffects) return;
        const now = Date.now();
        if (now - lastRippleTime < 80) return;
        lastRippleTime = now;

        try {
          const rect = container.getBoundingClientRect();
          const pxToSvg = currentVB.w / rect.width;
          const svgX = currentVB.x + (clientX - rect.left) * pxToSvg;
          const svgY = currentVB.y + (clientY - rect.top) * (currentVB.h / rect.height);

          // Subtle grey ripple: radius ~2.5px -> ~14px on screen, duration 0.22s
          const startR = (2.5 * pxToSvg).toFixed(2);
          const endR = (14.0 * pxToSvg).toFixed(2);

          const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
          circle.setAttribute('cx', svgX.toFixed(2));
          circle.setAttribute('cy', svgY.toFixed(2));
          circle.setAttribute('r', startR);
          circle.setAttribute('fill', 'rgba(226, 232, 240, 0.18)');
          circle.setAttribute('stroke', 'rgba(203, 213, 225, 0.75)');
          circle.setAttribute('stroke-width', '1.5');
          circle.setAttribute('vector-effect', 'non-scaling-stroke');

          const animR = document.createElementNS('http://www.w3.org/2000/svg', 'animate');
          animR.setAttribute('attributeName', 'r');
          animR.setAttribute('from', startR);
          animR.setAttribute('to', endR);
          animR.setAttribute('dur', '0.22s');
          animR.setAttribute('fill', 'freeze');

          const animOp = document.createElementNS('http://www.w3.org/2000/svg', 'animate');
          animOp.setAttribute('attributeName', 'opacity');
          animOp.setAttribute('from', '0.85');
          animOp.setAttribute('to', '0');
          animOp.setAttribute('dur', '0.22s');
          animOp.setAttribute('fill', 'freeze');

          circle.appendChild(animR);
          circle.appendChild(animOp);
          tapEffects.appendChild(circle);

          try {
            animR.beginElement();
            animOp.beginElement();
          } catch (e) {}

          setTimeout(() => {
            if (circle.parentNode) circle.parentNode.removeChild(circle);
          }, 240);
        } catch (e) {
          console.error("Tap ripple error", e);
        }
      }

      // Item 1: Tap handling with Double-Tap to Zoom & Single-Tap Selection/Deselection
      let lastTapTime = 0;
      let lastTapPos = { x: 0, y: 0 };
      let singleTapTimer = null;

      function handleTapAt(clientX, clientY) {
        showTapRipple(clientX, clientY);

        const now = Date.now();
        const timeDiff = now - lastTapTime;
        const dist = Math.hypot(clientX - lastTapPos.x, clientY - lastTapPos.y);

        if (timeDiff < 320 && dist < 36) {
          // Double-tap detected: cancel single-tap selection and zoom in to tapped point
          if (singleTapTimer) {
            clearTimeout(singleTapTimer);
            singleTapTimer = null;
          }
          lastTapTime = 0;
          handleDoubleTapZoom(clientX, clientY);
        } else {
          lastTapTime = now;
          lastTapPos = { x: clientX, y: clientY };

          if (singleTapTimer) {
            clearTimeout(singleTapTimer);
          }
          singleTapTimer = setTimeout(() => {
            singleTapTimer = null;
            executeSingleTap(clientX, clientY);
          }, 220);
        }
      }

      function handleDoubleTapZoom(clientX, clientY) {
        try {
          const rect = container.getBoundingClientRect();
          const pxToSvgX = currentVB.w / rect.width;
          const pxToSvgY = currentVB.h / rect.height;
          const tapSvgX = currentVB.x + (clientX - rect.left) * pxToSvgX;
          const tapSvgY = currentVB.y + (clientY - rect.top) * pxToSvgY;

          // Zoom factor ~1.8x closer centered on tapped point
          const zoomFactor = 1.8;
          const baseW = Math.min(currentVB.w, targetVB.w);
          let newW = baseW / zoomFactor;
          if (newW < MIN_WIDTH) newW = MIN_WIDTH;

          const aspect = getScreenAspect();
          const newH = newW / aspect;

          const newX = tapSvgX - newW / 2;
          const newY = tapSvgY - newH / 2;

          userMovedSinceFocus = true;

          setTargetVB({
            x: newX,
            y: newY,
            w: newW,
            h: newH
          }, true);
        } catch (e) {
          console.error("Double tap zoom error", e);
        }
      }

      function executeSingleTap(clientX, clientY) {
        const elements = document.elementsFromPoint(clientX, clientY);
        let selectedId = null;

        for (const el of elements) {
          const bldgId = getBuildingIdFromElement(el);
          if (bldgId) {
            selectedId = bldgId;
            break;
          }
        }

        // Hitbox tolerance for small buildings: if exact pixel missed, search a radius around the point
        if (!selectedId) {
          const radii = [6, 12];
          const angles = [0, 45, 90, 135, 180, 225, 270, 315];
          let bestDist = Infinity;

          for (const r of radii) {
            for (const deg of angles) {
              const rad = (deg * Math.PI) / 180;
              const testX = clientX + r * Math.cos(rad);
              const testY = clientY + r * Math.sin(rad);
              const nearbyElements = document.elementsFromPoint(testX, testY);
              for (const el of nearbyElements) {
                const bldgId = getBuildingIdFromElement(el);
                if (bldgId) {
                  if (r < bestDist) {
                    bestDist = r;
                    selectedId = bldgId;
                  }
                  break;
                }
              }
              if (selectedId && bestDist <= 6) break;
            }
            if (selectedId) break;
          }
        }

        if (selectedId) {
          selectBuilding(selectedId, true);
          focusBuildingInTopRegion(selectedId);
        } else {
          window.deselectBuilding(true);
        }
      }

      function selectBuilding(bldgId, notifyBridge = true) {
        document.querySelectorAll('.building-active').forEach(el => {
          el.classList.remove('building-active');
          if (el.dataset.prevCat) {
            el.classList.add(el.dataset.prevCat);
            delete el.dataset.prevCat;
          }
        });

        const targetEl = document.getElementById(bldgId);
        if (targetEl) {
          // Temporarily remove any category class that could block the pulse animation
          const catClass = Array.from(targetEl.classList).find(c => c.startsWith('cat-highlight-'));
          if (catClass) {
            targetEl.dataset.prevCat = catClass;
            targetEl.classList.remove(catClass);
          }
          targetEl.classList.add('building-active');
          targetEl.parentElement.appendChild(targetEl);
        }

        if (notifyBridge && window.AndroidBridge && window.AndroidBridge.onBuildingSelected) {
          try {
            window.AndroidBridge.onBuildingSelected(bldgId);
          } catch (e) {
            console.error("AndroidBridge error", e);
          }
        }
      }

      window.deselectBuilding = function(notifyBridge = true) {
        document.querySelectorAll('.building-active').forEach(el => {
          el.classList.remove('building-active');
          if (el.dataset.prevCat) {
            el.classList.add(el.dataset.prevCat);
            delete el.dataset.prevCat;
          }
        });
        userMovedSinceFocus = false;
        if (notifyBridge && window.AndroidBridge && window.AndroidBridge.onBuildingDeselected) {
          try {
            window.AndroidBridge.onBuildingDeselected();
          } catch (e) {
            console.error("AndroidBridge error", e);
          }
        }
      };

      // Item 2: Focus building in the upper exposed map region (above ~58% bottom sheet)
      function focusBuildingInTopRegion(bldgId, animate = true) {
        const el = document.getElementById(bldgId);
        if (!el) return;

        try {
          const bbox = el.getBBox();
          const centerX = bbox.x + bbox.width / 2;
          const centerY = bbox.y + bbox.height / 2;

          const aspect = getScreenAspect();
          // Maintain current zoom if comfortable; otherwise clamp to comfortable overview
          const maxOverviewW = aspect >= 1.0 ? 340 * aspect : 340;
          let targetW = targetVB.w;
          if (targetW > maxOverviewW * 1.05) targetW = maxOverviewW;
          if (targetW < MIN_WIDTH) targetW = MIN_WIDTH;
          targetW = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, targetW));
          const targetH = targetW / aspect;

          // In landscape the search bar is in a side column, leaving the upper region open (~22%);
          // In portrait, the top search bar leaves ~27% as the center of the upper region.
          const focusRatioY = aspect >= 1.0 ? 0.22 : 0.27;
          const targetX = centerX - targetW / 2;
          const targetY = centerY - focusRatioY * targetH;

          userMovedSinceFocus = false;

          setTargetVB({
            x: targetX,
            y: targetY,
            w: targetW,
            h: targetH
          }, animate);
        } catch (e) {
          console.error("Failed to focus building in top region", e);
        }
      }

      // --- Public Window API for Android Compose Integration ---
      window.handleTapAt = handleTapAt;
      window.getTargetVB = function() { return { ...targetVB }; };
      window.getCurrentVB = function() { return { ...currentVB }; };
      window.zoomToBuilding = function(bldgId) {
        selectBuilding(bldgId, false);
        focusBuildingInTopRegion(bldgId);
      };

      // Fit all buildings of a category in view
      window.fitBuildingsInView = function(idsJson) {
        const ids = typeof idsJson === 'string' ? JSON.parse(idsJson) : idsJson;
        if (!ids || ids.length === 0) return;

        userMovedSinceFocus = true;

        let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
        let count = 0;

        ids.forEach(id => {
          const el = document.getElementById(id);
          if (el) {
            try {
              const bbox = el.getBBox();
              minX = Math.min(minX, bbox.x);
              minY = Math.min(minY, bbox.y);
              maxX = Math.max(maxX, bbox.x + bbox.width);
              maxY = Math.max(maxY, bbox.y + bbox.height);
              count++;
            } catch (e) {}
          }
        });

        if (count > 0 && minX !== Infinity) {
          const width = maxX - minX;
          const height = maxY - minY;
          const aspect = getScreenAspect();

          const padW = Math.max(width * 1.35, height * aspect * 1.35);
          const centerX = (minX + maxX) / 2;
          const centerY = (minY + maxY) / 2;

          setTargetVB(buildTargetVB(centerX, centerY, Math.max(380, padW)), true);
        }
      };

      window.highlightCategory = function(category, idsJson) {
        window.clearCategoryHighlights();
        const ids = typeof idsJson === 'string' ? JSON.parse(idsJson) : idsJson;
        const className = `cat-highlight-${category}`;

        ids.forEach(id => {
          const el = document.getElementById(id);
          if (el) el.classList.add(className);
        });

        window.fitBuildingsInView(ids);
      };

      window.clearCategoryHighlights = function() {
        const classes = [
          'cat-highlight-aed',
          'cat-highlight-science',
          'cat-highlight-dining',
          'cat-highlight-dorms',
          'cat-highlight-medical',
          'cat-highlight-fitness',
          'cat-highlight-smoking'
        ];
        classes.forEach(c => {
          document.querySelectorAll(`.${c}`).forEach(el => el.classList.remove(c));
        });
      };

      function getDefaultSpanW() {
        const aspect = getScreenAspect();
        return aspect >= 1.0 ? Math.max(DEFAULT_SPAN_W, 520 * aspect) : DEFAULT_SPAN_W;
      }

      window.resetView = function() {
        userMovedSinceFocus = true;
        setTargetVB(buildTargetVB(STATION_CENTER.x, STATION_CENTER.y, getDefaultSpanW()), true);
      };

      window.zoomIn = function() {
        userMovedSinceFocus = true;
        const centerX = targetVB.x + targetVB.w / 2;
        const centerY = targetVB.y + targetVB.h / 2;
        setTargetVB(buildTargetVB(centerX, centerY, targetVB.w * 0.65), true);
      };

      window.zoomOut = function() {
        userMovedSinceFocus = true;
        const centerX = targetVB.x + targetVB.w / 2;
        const centerY = targetVB.y + targetVB.h / 2;
        setTargetVB(buildTargetVB(centerX, centerY, targetVB.w * 1.45), true);
      };

      // Direct multi-event binder for touch buttons
      function bindButton(id, fn) {
        const el = document.getElementById(id);
        if (!el) return;
        let lastTrigger = 0;
        const handler = (e) => {
          e.preventDefault();
          e.stopPropagation();
          const now = Date.now();
          if (now - lastTrigger > 250) {
            lastTrigger = now;
            fn();
          }
        };
        el.addEventListener('touchend', handler);
        el.addEventListener('click', handler);
      }

      bindButton('btn-zoom-in', window.zoomIn);
      bindButton('btn-zoom-out', window.zoomOut);
      bindButton('btn-reset', window.resetView);

      let lastContainerW = 0;
      let lastContainerH = 0;

      function handleResize() {
        const newContainerW = container.clientWidth || window.innerWidth;
        const newContainerH = container.clientHeight || window.innerHeight;
        if (!newContainerW || !newContainerH) return;

        if (lastContainerW <= 0 || lastContainerH <= 0) {
          lastContainerW = newContainerW;
          lastContainerH = newContainerH;
          return;
        }

        if (Math.abs(newContainerW - lastContainerW) < 2 && Math.abs(newContainerH - lastContainerH) < 2) {
          return;
        }

        const aspect = newContainerW / newContainerH;
        const scaleFactor = newContainerW / lastContainerW;

        if (animFrame) {
          cancelAnimationFrame(animFrame);
          animFrame = null;
        }

        const activeEl = document.querySelector('.building-active');
        if (activeEl && !userMovedSinceFocus) {
          let newW = targetVB.w * scaleFactor;
          newW = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, newW));
          targetVB.w = newW;
          currentVB.w = newW;
          targetVB.h = newW / aspect;
          currentVB.h = targetVB.h;
          focusBuildingInTopRegion(activeEl.id, false);
        } else {
          // Preserve the exact geographic center of the viewport and visual scale
          const prevTargetCenterX = targetVB.x + targetVB.w / 2;
          const prevTargetCenterY = targetVB.y + targetVB.h / 2;

          let newTargetW = targetVB.w * scaleFactor;
          newTargetW = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, newTargetW));
          const newTargetH = newTargetW / aspect;

          targetVB = {
            x: prevTargetCenterX - newTargetW / 2,
            y: prevTargetCenterY - newTargetH / 2,
            w: newTargetW,
            h: newTargetH
          };

          currentVB = { ...targetVB };

          updateViewBox(currentVB);
        }

        lastContainerW = newContainerW;
        lastContainerH = newContainerH;
      }

      window.addEventListener('resize', handleResize);

      lastContainerW = container.clientWidth || window.innerWidth || 1080;
      lastContainerH = container.clientHeight || window.innerHeight || 2400;
      setTargetVB(buildTargetVB(STATION_CENTER.x, STATION_CENTER.y, getDefaultSpanW()), false);

      // Signal to Android that initial map layout, SVG parsing and viewBox are completely ready
      requestAnimationFrame(() => {
        if (window.AndroidBridge && window.AndroidBridge.onMapReady) {
          try {
            window.AndroidBridge.onMapReady();
          } catch (e) {
            console.error("onMapReady bridge error", e);
          }
        }
      });
    })();
  </script>
</body>
</html>
"""

with open("app/src/main/assets/map_view.html", "w", encoding="utf-8") as out:
    out.write(html_template)

print("Regenerated map_view.html successfully!")
