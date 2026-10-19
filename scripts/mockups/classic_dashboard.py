"""Mockup of the Classic dashboard (doc/specs/classic-dashboard.md): round dials in the G31 Live Cockpit colours.

Writes an SVG to stdout: python3 scripts/mockups/classic_dashboard.py W H [regen] > out.svg
Preview as PNG with headless Chrome:
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --screenshot=out.png \\
      --window-size=W,H file://$PWD/out.svg
Layout: main row 55 % (4 dials), sub row 32 % (6 dials), status strip 13 %.
"""
import math, sys

W, H = int(sys.argv[1]), int(sys.argv[2])
REGEN = len(sys.argv) > 3 and sys.argv[3] == "regen"
ts = min(W / 800, H / 480)  # strip text scale
MIN_PX = 11.0

INK, NUM, DIM, MINOR, TRACK = "#FFFFFF", "#D6D6D6", "#8E8E8E", "#8A8A8A", "#2B2B2B"
ORANGE, GLOW, RED, BLUE, AMBER, EDGE, ASSIST = "#FF6A13", "#FF5A00", "#F11A22", "#008AC9", "#FFB000", "#D9D9D9", "#E2E2E2"
FONT = "font-family='Roboto, Helvetica Neue, Arial, sans-serif'"
out = []
uid = [0]


def fs(px):
    return max(px, MIN_PX)


def pt(cx, cy, r, deg):
    a = math.radians(deg)
    return cx + r * math.sin(a), cy - r * math.cos(a)


def arc_d(cx, cy, r, d0, d1):
    x0, y0 = pt(cx, cy, r, d0)
    x1, y1 = pt(cx, cy, r, d1)
    large = 1 if abs(d1 - d0) > 180 else 0
    sweep = 1 if d1 >= d0 else 0
    return f"M{x0:.1f},{y0:.1f} A{r:.1f},{r:.1f} 0 {large} {sweep} {x1:.1f},{y1:.1f}"


def arc(cx, cy, r, d0, d1, col, w, extra=""):
    if abs(d1 - d0) < 0.01:
        return
    out.append(f"<path d='{arc_d(cx, cy, r, d0, d1)}' fill='none' stroke='{col}' stroke-width='{w:.1f}' {extra}/>")


def line(a, b, col, w, extra=""):
    out.append(f"<line x1='{a[0]:.1f}' y1='{a[1]:.1f}' x2='{b[0]:.1f}' y2='{b[1]:.1f}' stroke='{col}' stroke-width='{w:.1f}' {extra}/>")


def text(x, y, s, px, fill=INK, anchor="middle", weight=300, spacing=0.0):
    out.append(f"<text x='{x:.1f}' y='{y:.1f}' fill='{fill}' font-size='{fs(px):.1f}' font-weight='{weight}' "
               f"letter-spacing='{spacing:.1f}' text-anchor='{anchor}' {FONT}>{s}</text>")


def zones_and_edge(cx, cy, r, k, deg, zones, edge_r):
    """Light edge line along the arc; printed zones replace it with a segmented band."""
    covered = sorted((deg(a), deg(b), col) for a, b, col in zones)
    pos = deg(None)
    for d0, d1, col in covered:
        arc(cx, cy, edge_r, pos, d0, EDGE, 1.6 * k)
        arc(cx, cy, edge_r + 1 * k, d0, d1, col, 5.5 * k, f"stroke-dasharray='{9 * k:.1f} {1.6 * k:.1f}'")
        pos = d1
    arc(cx, cy, edge_r, pos, deg(1e9), EDGE, 1.6 * k)


def main_dial(cx, cy, r, label, unit, vmin, vmax, major, minor, value, numerals, zones=(), text_value=None, target=None):
    k = r / 88
    start, sweep = -120, 240
    f = lambda v: max(0.0, min(1.0, (v - vmin) / (vmax - vmin)))
    deg = lambda v: start if v is None else start + sweep * f(v)
    uid[0] += 1
    cid = f"c{uid[0]}"
    out.append(f"<clipPath id='{cid}'><circle cx='{cx:.1f}' cy='{cy:.1f}' r='{r + 2 * k:.1f}'/></clipPath>")
    out.append(f"<circle cx='{cx:.1f}' cy='{cy:.1f}' r='{r + 2 * k:.1f}' fill='url(#face)'/>")
    # value: glow, then the progress line inside the ticks
    arc(cx, cy, r - 16 * k, start, deg(value), GLOW, 30 * k, f"stroke-opacity='0.42' filter='url(#soft)' clip-path='url(#{cid})'")
    arc(cx, cy, r - 17 * k, start, deg(value), ORANGE, 2.2 * k)
    zones_and_edge(cx, cy, r, k, lambda v: start if v is None else (start + sweep if v == 1e9 else deg(v)), zones, r + 4 * k)
    steps = round((vmax - vmin) / major) * minor
    for i in range(steps + 1):
        d = start + sweep * i / steps
        is_major = i % minor == 0
        half = minor % 2 == 0 and i % minor == minor // 2
        ln = (12 if is_major else 8 if half else 4.5) * k
        line(pt(cx, cy, r - 2 * k, d), pt(cx, cy, r - 2 * k - ln, d), NUM if is_major or half else MINOR,
             (2.2 if is_major else 1.2) * k)
    for v, s in numerals:
        x, y = pt(cx, cy, r - 30 * k, deg(v))
        text(x, y + 5.5 * k, s, 15 * k, NUM)
    if target is not None:
        line(pt(cx, cy, r + 8 * k, deg(target)), pt(cx, cy, r - 6 * k, deg(target)), INK, 3.2 * k)
    # pointer: orange bar from the scale towards the centre, no pivot
    line(pt(cx, cy, r + 1 * k, deg(value)), pt(cx, cy, r - 46 * k, deg(value)), ORANGE, 3.6 * k,
         "stroke-linecap='round' filter='url(#glow)'")
    text(cx, cy - 22 * k, label, 10 * k, DIM, weight=400, spacing=2 * k)
    text(cx, cy + 14 * k, text_value or f"{value:g}", 34 * k, INK, weight=400)
    text(cx, cy + 32 * k, unit, 12 * k, DIM)
    if target is not None:
        out.append(f"<text x='{cx:.1f}' y='{cy + 50 * k:.1f}' text-anchor='middle' {FONT}>"
                   f"<tspan fill='{DIM}' font-size='{fs(9 * k):.1f}' letter-spacing='{1.5 * k:.1f}' font-weight='400'>TARGET </tspan>"
                   f"<tspan fill='{INK}' font-size='{fs(13 * k):.1f}' font-weight='300'>{target:.2f}</tspan></text>")


def sub_dial(cx, top, r, band_h, label, unit, vmin, vmax, value, text_value, numerals, zones=(), centre=None):
    k = r / 64
    start, sweep = -45, 90
    f = lambda v: max(0.0, min(1.0, (v - vmin) / (vmax - vmin)))
    deg = lambda v: start + sweep * f(v)
    cy = top + band_h * 0.2 + r
    text(cx, top + band_h * 0.11, label, 9.5 * k, DIM, weight=400, spacing=1.2 * k)
    # graphite band, edge line / printed zones outside it
    out.append(f"<path d='{arc_d(cx, cy, r - 7 * k, start, start + sweep)}' fill='none' stroke='url(#band)' stroke-width='{14 * k:.1f}'/>")
    zones_and_edge(cx, cy, r, k, lambda v: start if v is None else (start + sweep if v == 1e9 else deg(v)), zones, r + 2.5 * k)
    for i in range(9):
        d = start + sweep * i / 8
        major = i % 4 == 0
        line(pt(cx, cy, r - 1 * k, d), pt(cx, cy, r - (11 if major else 6) * k, d), NUM if major else MINOR,
             (2 if major else 1.1) * k)
    origin = start if centre is None else deg(centre)
    col = ORANGE if centre is None else (ASSIST if value >= centre else BLUE)
    arc(cx, cy, r - 17 * k, min(origin, deg(value)), max(origin, deg(value)), col, 2.2 * k)
    for v, s in numerals:
        x, y = pt(cx, cy, r - 27 * k, deg(v))
        text(x, y + 4 * k, s, 11 * k, NUM)
    line(pt(cx, cy, r + 2 * k, deg(value)), pt(cx, cy, r - 18 * k, deg(value)), ORANGE, 3.2 * k,
         "stroke-linecap='round' filter='url(#glow)'")
    if text_value:
        out.append(f"<text x='{cx:.1f}' y='{cy + 6 * k:.1f}' text-anchor='middle' {FONT}>"
                   f"<tspan fill='{INK}' font-size='{fs(19 * k):.1f}' font-weight='400'>{text_value}</tspan>"
                   f"<tspan fill='{DIM}' font-size='{fs(10 * k):.1f}'> {unit}</tspan></text>")


out.append(f"<svg xmlns='http://www.w3.org/2000/svg' width='{W}' height='{H}' viewBox='0 0 {W} {H}'>")
out.append(f"""<defs>
<radialGradient id='face'><stop offset='0' stop-color='#0C0C0C'/><stop offset='0.62' stop-color='#141414'/><stop offset='1' stop-color='#3A3A3A'/></radialGradient>
<linearGradient id='band' x1='0' x2='0' y1='0' y2='1'><stop offset='0' stop-color='#3A3A3A'/><stop offset='1' stop-color='#1A1A1A'/></linearGradient>
<filter id='soft' x='-30%' y='-30%' width='160%' height='160%'><feGaussianBlur stdDeviation='{6 * ts:.1f}'/></filter>
<filter id='glow' x='-50%' y='-50%' width='200%' height='200%'><feGaussianBlur stdDeviation='{1.6 * ts:.1f}' result='b'/><feMerge><feMergeNode in='b'/><feMergeNode in='SourceGraphic'/></feMerge></filter>
<filter id='lamp' x='-30%' y='-80%' width='160%' height='260%'><feGaussianBlur stdDeviation='{4 * ts:.1f}'/></filter>
</defs>""")
out.append(f"<rect width='{W}' height='{H}' fill='#000'/>")

# main row
main_h = H * 0.55
cw = W / 4
d = min(cw, main_h) * 0.94
r = d / 2 - d * 0.035
cy = main_h / 2 + H * 0.005
xs = [cw * (i + 0.5) for i in range(4)]
main_dial(xs[0], cy, r, "OIL", "°C", 50, 150, 20, 4, 98, [(v, str(v)) for v in range(50, 151, 20)], zones=[(130, 150, RED)])
main_dial(xs[1], cy, r, "COOLANT", "°C", 40, 130, 30, 6, 91, [(v, str(v)) for v in (40, 70, 100, 130)],
          zones=[(40, 60, BLUE), (115, 130, RED)])
main_dial(xs[2], cy, r, "BOOST", "bar", 0, 2.0, 0.5, 5, 1.42, [(v, f"{v:g}") for v in (0, 0.5, 1.0, 1.5, 2.0)],
          text_value="1.42", target=1.60)
main_dial(xs[3], cy, r, "GEARBOX", "°C", 40, 140, 20, 4, 78, [(v, str(v)) for v in range(40, 141, 20)], zones=[(120, 140, RED)])

# sub row
top, band_h = main_h, H * 0.32
sw = W / 6
rs = min(sw * 0.48, band_h * 0.42)
sx = [sw * (i + 0.5) for i in range(6)]
egt = 610 if REGEN else 310
sub_dial(sx[0], top, rs, band_h, "INTAKE", "°C", -20, 80, 24, "24", [(-20, "−20"), (30, "30"), (80, "80")])
sub_dial(sx[1], top, rs, band_h, "EGT", "°C", 0, 900, egt, str(egt), [(0, "0"), (450, "450"), (900, "900")], zones=[(750, 900, RED)])
sub_dial(sx[2], top, rs, band_h, "OIL LEVEL", "", 0, 1, 0.72, None, [(0, "MIN"), (1, "MAX")])
sub_dial(sx[3], top, rs, band_h, "48V BATTERY", "%", 0, 100, 72, "72", [(0, "0"), (50, "50"), (100, "100")])
sub_dial(sx[4], top, rs, band_h, "STARTER-GEN", "kW", -10, 10, 3.2, "+3.2", [(-10, "−10"), (0, "0"), (10, "+10")], centre=0)
sub_dial(sx[5], top, rs, band_h, "12V BATTERY", "V", 10, 16, 14.1, "14.1", [(10, "10"), (13, "13"), (16, "16")])

# status strip
sy = H * 0.87
line((16 * ts, sy), (W - 16 * ts, sy), "#2A2A2A", 1)
lh = H * 0.13 * 0.55
lw = lh * 4.2
lx, ly = 24 * ts, sy + (H - sy - lh) / 2
if REGEN:
    out.append(f"<rect x='{lx:.1f}' y='{ly:.1f}' width='{lw:.1f}' height='{lh:.1f}' rx='{4 * ts:.1f}' fill='{AMBER}' opacity='0.5' filter='url(#lamp)'/>")
    out.append(f"<rect x='{lx:.1f}' y='{ly:.1f}' width='{lw:.1f}' height='{lh:.1f}' rx='{4 * ts:.1f}' fill='{AMBER}'/>")
    text(lx + lw / 2, ly + lh * 0.68, "DPF REGEN", lh * 0.46, "#1A1000", weight=700, spacing=1.5 * ts)
    text(lx + lw + 16 * ts, ly + lh * 0.75, "Regenerating", lh * 0.8, INK, anchor="start")
else:
    out.append(f"<rect x='{lx:.1f}' y='{ly:.1f}' width='{lw:.1f}' height='{lh:.1f}' rx='{4 * ts:.1f}' fill='#101010' stroke='#2E2E2E' stroke-width='1.2'/>")
    text(lx + lw / 2, ly + lh * 0.68, "DPF REGEN", lh * 0.46, "#3E3E3E", weight=700, spacing=1.5 * ts)
    out.append(f"<text x='{lx + lw + 16 * ts:.1f}' y='{ly + lh * 0.78:.1f}' text-anchor='start' {FONT}>"
               f"<tspan fill='{INK}' font-size='{fs(lh * 0.9):.1f}' font-weight='300'>312</tspan>"
               f"<tspan fill='{NUM}' font-size='{fs(lh * 0.55):.1f}' font-weight='300'> km</tspan>"
               f"<tspan fill='{DIM}' font-size='{fs(lh * 0.45):.1f}' letter-spacing='{1.5 * ts:.1f}'>  SINCE REGEN</tspan></text>")
out.append("</svg>")
print("\n".join(out))
