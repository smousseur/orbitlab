"""Two schematic renders of the same 6 h LEO flight, seen at the same instant,
with the globe drawn in the same (body-fixed) orientation in both.

A: the trail as it is drawn today  -> inertial, rigidly rotated by -theta_final
B: the trail in the rotating frame -> each vertex rotated by -theta(t_i)
"""

import math

DEG = math.pi / 180.0

R = 1.0                      # Earth radius, normalised
ALT = 400.0 / 6371.0
r_orb = R + ALT
INC = 28.5 * DEG
RAAN = -90.0 * DEG           # puts the spacecraft over the pad at t = 0
T_ORB = 92.6                 # minutes
T_END = 360.0                # 6 h
T_ASCENT = 8.0
OMEGA_E = 360.0 / 1436.0     # deg / minute (sidereal)
PAD_LAT = 28.5 * DEG
N = 2000

W, H = 960, 960
CX, CY = W / 2.0, H / 2.0 + 18
SCALE = 300.0
YAW = -45.0 * DEG   # centres the visible face on lon -45°, so the pad (lon 0) and
                    # the inertial foot (lon -90 after 6 h) are both on it
TILT = 45.0 * DEG


def rz(v, a):
    c, s = math.cos(a), math.sin(a)
    return (v[0] * c - v[1] * s, v[0] * s + v[1] * c, v[2])


def rx(v, a):
    c, s = math.cos(a), math.sin(a)
    return (v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c)


def view(v):
    return rx(rz(v, YAW), TILT)


def project(v):
    p = view(v)
    return (CX + p[0] * SCALE, CY - p[2] * SCALE, p[1])


def hidden(sx, sy, depth):
    dx, dy = sx - CX, sy - CY
    return depth > 0 and math.hypot(dx, dy) < R * SCALE - 0.5


def inertial(t):
    """Spacecraft position at t minutes, inertial frame, ascent ramped from the pad."""
    u = 90.0 * DEG + 2 * math.pi * t / T_ORB
    if t < T_ASCENT:
        s = t / T_ASCENT
        s = s * s * (3 - 2 * s)
        rad = R + (r_orb - R) * s
    else:
        rad = r_orb
    p = (rad * math.cos(u), rad * math.sin(u), 0.0)
    return rz(rx(p, INC), RAAN)


THETA_END = OMEGA_E * T_END * DEG
PAD = (R * math.cos(PAD_LAT), 0.0, R * math.sin(PAD_LAT))


def trail(mode):
    pts = []
    for k in range(N + 1):
        t = T_END * k / N
        p = inertial(t)
        ang = THETA_END if mode == "inertial" else OMEGA_E * t * DEG
        pts.append((rz(p, -ang), t))
    return pts


def polyline_svg(pts, colour, width, klass):
    """Emit one polyline per visible/hidden run so the globe occludes the trace."""
    out = []
    run, run_hidden = [], None
    for v, t in pts:
        sx, sy, d = project(v)
        h = hidden(sx, sy, d)
        if run_hidden is None:
            run_hidden = h
        if h != run_hidden and run:
            out.append(emit(run, colour, width, run_hidden, klass))
            run = [run[-1]]
            run_hidden = h
        run.append((sx, sy))
    if len(run) > 1:
        out.append(emit(run, colour, width, run_hidden, klass))
    return "\n".join(out)


def emit(run, colour, width, is_hidden, klass):
    d = " ".join(f"{x:.1f},{y:.1f}" for x, y in run)
    op = 0.22 if is_hidden else 1.0
    dash = ' stroke-dasharray="5 6"' if is_hidden else ""
    return (f'<polyline class="{klass}" points="{d}" fill="none" stroke="{colour}" '
            f'stroke-width="{width}" stroke-opacity="{op}"{dash} '
            f'stroke-linecap="round" stroke-linejoin="round"/>')


def graticule():
    out = []
    for lon in range(0, 360, 30):
        pts = []
        for lat in range(-90, 91, 3):
            la, lo = lat * DEG, lon * DEG
            pts.append(((R * math.cos(la) * math.cos(lo),
                         R * math.cos(la) * math.sin(lo),
                         R * math.sin(la)), 0))
        out.append(polyline_svg(pts, "#33507f", 1.0, "grid"))
    for lat in range(-60, 61, 30):
        pts = []
        la = lat * DEG
        for lon in range(0, 361, 3):
            lo = lon * DEG
            pts.append(((R * math.cos(la) * math.cos(lo),
                         R * math.cos(la) * math.sin(lo),
                         R * math.sin(la)), 0))
        out.append(polyline_svg(pts, "#33507f", 1.0, "grid"))
    return "\n".join(out)


def marker(v, colour, label, dx=14, dy=-12, ring=False, anchor="start"):
    sx, sy, d = project(v)
    back = hidden(sx, sy, d)
    op = 0.30 if back else 1.0
    s = []
    if ring:
        s.append(f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="8" fill="none" '
                 f'stroke="{colour}" stroke-width="2.5" opacity="{op}"/>')
    else:
        s.append(f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="6" fill="{colour}" opacity="{op}"/>')
    if label:
        s.append(f'<text x="{sx + dx:.1f}" y="{sy + dy:.1f}" fill="{colour}" '
                 f'font-size="19" font-family="Segoe UI, sans-serif" '
                 f'text-anchor="{anchor}" opacity="{op}">{label}</text>')
    return "\n".join(s), (sx, sy)


def build(mode, title, subtitle, caption):
    pts = trail(mode)
    asc = [p for p in pts if p[1] <= T_ASCENT + 0.5]
    rest = [p for p in pts if p[1] >= T_ASCENT]

    body = [f'<rect width="{W}" height="{H}" fill="#0a0f1e"/>']
    body.append(f'<circle cx="{CX}" cy="{CY}" r="{R * SCALE:.1f}" fill="url(#globe)"/>')
    body.append(graticule())
    body.append(f'<circle cx="{CX}" cy="{CY}" r="{R * SCALE:.1f}" fill="none" '
                f'stroke="#4a6ea8" stroke-width="1.6"/>')

    body.append(polyline_svg(rest, "#4fd1e6", 3.0, "trail"))
    body.append(polyline_svg(asc, "#ff9f43", 4.5, "ascent"))

    pad_svg, pad_xy = marker(PAD, "#ffd447", "pas de tir", dx=16, dy=6)
    foot_pos = pts[0][0]
    foot_svg, foot_xy = marker(foot_pos, "#ff9f43",
                               "pied de l'ascension", dx=-16, dy=-16, ring=True,
                               anchor="end")
    body.append(pad_svg)
    body.append(foot_svg)

    if mode == "inertial":
        # Apex pushed clear of the trail's own band, so the annotation never crosses the trace.
        apex = 330.0
        cy = 2 * apex - (foot_xy[1] + pad_xy[1]) / 2
        body.append(
            f'<path d="M {foot_xy[0]:.1f},{foot_xy[1]:.1f} Q {(foot_xy[0] + pad_xy[0]) / 2:.1f},'
            f'{cy:.1f} {pad_xy[0]:.1f},{pad_xy[1]:.1f}" '
            f'fill="none" stroke="#ff6b6b" stroke-width="2" stroke-dasharray="7 6"/>')
        mx = (foot_xy[0] + pad_xy[0]) / 2
        my = apex - 18
        body.append(f'<text x="{mx:.1f}" y="{my:.1f}" fill="#ff6b6b" font-size="21" '
                    f'font-family="Segoe UI, sans-serif" text-anchor="middle">'
                    f'90° de longitude en 6 h</text>')

    body.append(f'<text x="44" y="62" fill="#eaf2ff" font-size="31" '
                f'font-family="Segoe UI, sans-serif" font-weight="600">{title}</text>')
    body.append(f'<text x="44" y="94" fill="#8fa8cc" font-size="20" '
                f'font-family="Segoe UI, sans-serif">{subtitle}</text>')
    for i, line in enumerate(caption):
        body.append(f'<text x="44" y="{H - 62 + i * 27}" fill="#8fa8cc" font-size="19" '
                    f'font-family="Segoe UI, sans-serif">{line}</text>')

    defs = ('<defs><radialGradient id="globe" cx="35%" cy="30%">'
            '<stop offset="0%" stop-color="#24406e"/>'
            '<stop offset="100%" stop-color="#101c33"/></radialGradient></defs>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
            f'viewBox="0 0 {W} {H}">{defs}' + "\n".join(body) + '</svg>')


out = r"C:\Users\Okita\AppData\Local\Temp\claude\C--Prog-projects-intelliJ-orbitlab\16bf1fa3-7e3e-48a6-8592-d0a3c6552bd0\scratchpad"

with open(out + r"\trace-inertielle.svg", "w", encoding="utf-8") as f:
    f.write(build(
        "inertial",
        "A — trace inertielle (comportement actuel)",
        "Vol LEO 400 km, i = 28,5°, vu 6 h apres le decollage",
        ["L'orbite garde sa forme et son plan : c'est bien une ellipse fixe dans l'inertiel.",
         "Mais le globe a tourne de 90° sous elle, et le pied de l'ascension a suivi le ciel,",
         "pas le sol : il ne part plus du pas de tir."]))

with open(out + r"\trace-repere-tournant.svg", "w", encoding="utf-8") as f:
    f.write(build(
        "rotating",
        "B — trace en repere tournant (bascule proposee)",
        "Meme vol, meme instant, meme orientation du globe",
        ["Chaque sommet est ramene dans le repere du sol : la trace part exactement du pas",
         "de tir et y reste. Prix a payer : l'ellipse devient un enroulement qui derive vers",
         "l'ouest d'environ 22° par revolution, et la lecture « orbite » disparait."]))

print("ok")
