"""Renders the phone clamp (phone-clamp/) holding a Galaxy S25 Ultra, and the clamp on a K2C1
with the laser holder on the muzzle. Run step2stl.py (freecadcmd) first.

Run: python phone-clamp.py  -> writes 4 PNGs to ../renders/

Assumptions (not in the STEP files):
- The phone's camera side (mesh z min) rests on the frame lips, its screen faces the thickness jaw.
  The phone's long edge without the camera sits in the long arm's channel, the short edge near the
  camera in the short arm's channel, so the lens ends up near the L corner, clear of the lips.
- On the gun: on the Olight rail mount + our mount + mount-adapter (boxes of their outer sizes), or on the
  printed picatinny clamp blocks only; long arm across the barrel, screen to the shooter, lens straight
  above the bore. (The case holder in k2c1-mount.py has no such stack: its T pillar goes straight into
  the Olight mount.)
- Laser holder: the rear socket slides over the flash hider and two knob screws press it, the Ø23.4 bore
  holds a Ø23 laser pointer (hardware/laser-switch is a rotary switch for this pointer size).
"""
import os

import numpy as np
import pyvista as pv
import trimesh
from PIL import Image, ImageDraw, ImageFont

from shared import HOLDER_COLOR, KNOB_COLOR, POINTER_COLOR

MESH = "/tmp/mobileaim-render"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "renders")
FONT = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothic.ttf", 26)

load = lambda name: trimesh.load(os.path.join(MESH, name + ".stl"))
JAWS = ["height-jaw", "height-jaw-cap", "width-jaw", "width-jaw-cap", "thickness-jaw", "thickness-jaw-cap"]
COLOR = {"frame": "#b8bec6", "height-jaw": "#e08a1e", "width-jaw": "#e08a1e", "thickness-jaw": "#2e9e5b",
         "height-jaw-cap": "#c0392b", "width-jaw-cap": "#c0392b", "thickness-jaw-cap": "#c0392b",
         "phone": "#30343a", "gun": "#5a5f66", "clamp": "#7fb3e0", "laser-holder": HOLDER_COLOR,
         "pointer": POINTER_COLOR, "beam": "#ff0000", "knob": KNOB_COLOR, "shank": "#8a9099"}
LEGEND = [("frame", "본체(frame): L자, 두 팔이 ㄷ자 홈"), ("width-jaw", "폭·높이 조(jaw)"), ("thickness-jaw", "두께 조"),
          ("width-jaw-cap", "조 덮개(cap): 폰에 닿는 면"), ("knob", "노브 나사(폭 1, 높이 1, 뒷면 두께 3)")]


def clamp_with_phone():
    """Clamp parts in their STEP (design) positions, phone inserted, jaws tightened onto the phone."""
    parts = {"frame": load("phone-clamp__frame")}
    parts.update({j: load("phone-clamp__" + j) for j in JAWS})
    phone = load("reference__galaxy-s25-ultra")
    # long edge (x min) against the long-arm channel wall x=-19.2, short edge (y max) against the
    # short-arm channel wall y=19.2, camera side (z min) on the lips z=-23.2
    phone.apply_translation([-19.2 + 38.8, 19.2 - 81.4, -23.2 + 0.12])
    gaps = {"width": 66.4 - phone.bounds[1][0], "height": phone.bounds[0][1] + 151.4, "thickness": -7.6 - phone.bounds[1][2]}
    for j in ("width-jaw", "width-jaw-cap"):
        parts[j].apply_translation([-gaps["width"], 0, 0])
    for j in ("height-jaw", "height-jaw-cap"):
        parts[j].apply_translation([0, gaps["height"], 0])
    for j in ("thickness-jaw", "thickness-jaw-cap"):
        parts[j].apply_translation([0, 0, gaps["thickness"]])
    parts["phone"] = phone
    # knob screws: width (x=80 face), height (y=-165 face) and three on the back for the thickness jaw (z=6,
    # over the jaw's three nut pockets), knob outside the frame, M5 shank through the hole into the jaw's nut
    for i, (center, axis) in enumerate([((80, 12.5, -11.5), (1, 0, 0)), ((-9.5, -165, -11.5), (0, -1, 0)),
                                        ((-12.5, -107.5, 6), (0, 0, 1)), ((-12.5, 12.5, 6), (0, 0, 1)),
                                        ((35.5, 12.5, 6), (0, 0, 1))]):
        knob = load("laser-holder__knob")
        knob.apply_translation([-knob.bounds.mean(0)[0], -knob.bounds.mean(0)[1], -knob.bounds[0][2]])  # base on z=0, axis z
        shank = trimesh.creation.cylinder(radius=2.5, height=14); shank.apply_translation([0, 0, -7])
        rot = trimesh.geometry.align_vectors([0, 0, 1], axis)
        for m in (knob, shank):
            m.apply_transform(rot); m.apply_translation(center)
        parts["knob#%d" % i] = knob
        parts["shank#%d" % i] = shank
    print("jaw travel used (mm): " + ", ".join("%s %.1f" % kv for kv in gaps.items()))
    return parts


def render(meshes, view, up, fname, opacity=None, arrows=(), legend=(), zoom=1.2, clip=None, title=None, focus=None):
    opacity = opacity or {}
    p = pv.Plotter(off_screen=True, window_size=(1400, 1100))
    p.set_background("white")
    for name, m in meshes.items():
        mesh = pv.wrap(m)
        if clip is not None:
            mesh = mesh.clip(normal=clip[0], origin=clip[1])
            if mesh.n_points == 0:
                continue
        key = name.split("#")[0]
        p.add_mesh(mesh, color=COLOR.get(key, "#999999"), opacity=opacity.get(key, 1.0))
    for start, d, scale in arrows:
        p.add_mesh(pv.Arrow(start=start, direction=d, scale=scale, tip_length=0.35, shaft_radius=0.06), color="#1f5fbf")
    p.view_vector(view, up)
    if focus is not None:  # close-up: aim at a point instead of fitting the whole scene
        p.camera.focal_point = focus
        p.camera.position = np.asarray(focus) + np.asarray(view, float) / np.linalg.norm(view) * 600
    p.camera.zoom(zoom)
    arr = p.screenshot(return_img=True).copy()
    p.close()
    img = Image.fromarray(arr)
    a = np.asarray(img.convert("L")) < 250
    ys, xs = np.where(a)
    img = img.crop((max(xs.min() - 20, 0), max(ys.min() - 20, 0), min(xs.max() + 20, img.width), min(ys.max() + 20, img.height)))
    rows = list(legend) + ([("", "파란 화살표: 조를 미는 방향")] if arrows else [])
    head = 50 if title else 0
    sheet = Image.new("RGB", (img.width + (780 if rows else 0), max(img.height, 60 + 44 * len(rows)) + head), "white")
    sheet.paste(img, (0, head))
    d = ImageDraw.Draw(sheet)
    if title:
        d.text((16, 10), title, fill="black", font=FONT)
    for i, (key, text) in enumerate(rows):
        y = head + 30 + 44 * i
        if key:
            d.rectangle((img.width + 10, y, img.width + 40, y + 26), fill=COLOR[key])
        d.text((img.width + 52, y - 2), text, fill="black", font=FONT)
    path = os.path.join(OUT, fname)
    sheet.save(path)
    print("saved", path)


def on_gun(clamp, printed_mount=False):
    """K2C1 + mount stack + phone clamp + laser holder and pointer.

    printed_mount=True puts only the printed picatinny clamp blocks (picatinny-rail-mount/
    clamp-left·right) on the gun rail, with the phone clamp straight on top of them.
    """
    sc, bore_y, bore_x = 77.95, 0.8305, -0.255
    s = trimesh.load(os.path.join(HERE, "..", "reference", "daewoo-k2c1.obj"))
    gun = trimesh.util.concatenate(list(s.geometry.values()))
    gun.apply_translation([-bore_x, -bore_y, 0]); gun.apply_scale(sc)  # bore axis = z axis, muzzle at z min
    rail_top, z0 = (1.389 - bore_y) * sc, -1.3 * sc

    # stack of Olight rail mount, our mount and mount-adapter (not the case holder's mounting: in
    # k2c1-mount.py the case's T pillar goes straight into the Olight mount)
    olight = trimesh.creation.box([35.5, 31, 79]); olight.apply_translation([0, rail_top - 9 + 15.5, z0])
    ourmount = trimesh.creation.box([25, 12, 60]); ourmount.apply_translation([0, rail_top + 22 + 6, z0])
    adapter = trimesh.creation.box([80, 25.6, 31.5]); adapter.apply_translation([0, rail_top + 34 + 12.8, z0])
    pic = trimesh.util.concatenate([olight, ourmount, adapter])
    pic_top = rail_top + 34 + 25.6
    if printed_mount:
        # block frame: x along the rail (110..135), y up (its rail top at 13), z across (rail center 4)
        blocks = trimesh.util.concatenate([load("picatinny-rail-mount__clamp-left"), load("picatinny-rail-mount__clamp-right")])
        blocks.apply_transform(np.array([[0, 0, 1, -4], [0, 1, 0, rail_top - 13], [1, 0, 0, z0 - 122.5], [0, 0, 0, 1]], float))
        pic = blocks
        pic_top = blocks.bounds[1][1]

    # phone clamp: frame x (up from the long arm) -> world y, frame y -> world -x, frame z -> world z
    # (camera side, frame -z, toward the muzzle); lens (frame y -13.2) over the bore, long arm on the adapter
    t = np.array([[0, -1, 0, -13.2], [1, 0, 0, pic_top + 25], [0, 0, 1, z0 + 12], [0, 0, 0, 1]], float)
    meshes = {"gun": gun, "clamp": pic}
    for name, m in clamp.items():
        m = m.copy(); m.apply_transform(t); meshes[name] = m

    holder = load("laser-holder__laser-holder")
    muzzle = gun.bounds[0][2]
    holder.apply_transform(np.array([[-1, 0, 0, 0], [0, 1, 0, 0], [0, 0, -1, muzzle + 10 - 40], [0, 0, 0, 1]], float))
    pointer = trimesh.creation.cylinder(radius=11.3, height=150)
    pointer.apply_translation([0, 0, holder.bounds[0][2] + 45 - 75])
    beam = trimesh.creation.cylinder(radius=1.2, height=400)
    beam.apply_translation([0, 0, pointer.bounds[0][2] - 200])
    meshes.update({"laser-holder": holder, "pointer": pointer, "beam": beam})
    return meshes, (0, rail_top + 90, z0)


def three_view(meshes, fname, title="클램프 거치대를 K2C1 에 단 모습"):
    """Side, front and iso views on one sheet, in the layout of k2c1-mount.py, headed by title."""
    views = {}
    for name, vv in [("side", (1, 0, 0)), ("front", (0, 0, -1)), ("iso", (1, 0.5, -0.8))]:
        p = pv.Plotter(off_screen=True, window_size=(1400, 800))
        p.set_background("white")
        for key, m in meshes.items():
            p.add_mesh(pv.wrap(m), color=COLOR.get(key, "#999999"), opacity={"frame": 0.6, "phone": 0.7}.get(key, 1.0))
        p.view_vector(vv, (0, 1, 0))
        if name != "iso":
            p.camera.parallel_projection = True
        img = Image.fromarray(p.screenshot(return_img=True).copy())
        p.close()
        a = np.asarray(img.convert("L")) < 250
        ys, xs = np.where(a)
        views[name] = img.crop((max(xs.min() - 20, 0), max(ys.min() - 20, 0), min(xs.max() + 20, img.width), min(ys.max() + 20, img.height)))
    side, front, iso = views["side"], views["front"], views["iso"]
    head = 50
    text = title
    sheet = Image.new("RGB", (max(side.width + front.width, iso.width, int(FONT.getlength(text)) + 32), head + max(side.height, front.height) + iso.height), "white")
    sheet.paste(side, (0, head)); sheet.paste(front, (side.width, head)); sheet.paste(iso, (0, head + max(side.height, front.height)))
    ImageDraw.Draw(sheet).text((16, 10), text, fill="black", font=FONT)
    path = os.path.join(OUT, fname)
    sheet.save(path)
    print("saved", path)


def clamp_sheet(clamp, fname):
    """The clamp with the phone from three angles (screen side, camera side, section) on one sheet, one legend."""
    tmp = []
    for i, (view, up, kw, title) in enumerate([
            ((0.5, -0.4, 1), (0, 1, 0), dict(opacity={"frame": 0.45}), "화면 쪽(사수 쪽)"),
            ((0.5, -0.4, -1), (0, 1, 0), dict(opacity={"phone": 0.6}), "카메라 쪽(표적 쪽)"),
            ((0.2, 1, 0.3), (0, 0, 1), dict(opacity={"frame": 0.6}), "옆(폭 조 쪽)")]):
        name = "_clamp_view_%d.png" % i
        render(clamp, view, up, name, title=title, **kw)
        tmp.append(os.path.join(OUT, name))
    imgs = [Image.open(t) for t in tmp]
    h = max(i.height for i in imgs)
    legend_h = 60 + 44 * len(LEGEND)
    sheet = Image.new("RGB", (sum(i.width for i in imgs) + 40, h + legend_h), "white")
    x = 0
    for im in imgs:
        sheet.paste(im, (x, (h - im.height) // 2)); x += im.width + 20
    d = ImageDraw.Draw(sheet)
    for k, (key, text) in enumerate(LEGEND):
        y = h + 30 + 44 * k
        d.rectangle((20, y, 50, y + 26), fill=COLOR[key])
        d.text((62, y - 2), text, fill="black", font=FONT)
    sheet.save(os.path.join(OUT, fname))
    for t in tmp:
        os.remove(t)
    print("saved", os.path.join(OUT, fname))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    c = clamp_with_phone()
    render(c, (0.5, -0.4, 1), (0, 1, 0), "phone-clamp - screen side.png", opacity={"frame": 0.45},
           arrows=[((100, 10, -15), (-1, 0, 0), 25), ((-10, -190, -15), (0, 1, 0), 25), ((10, -50, 35), (0, 0, -1), 25)],
           legend=LEGEND, title="화면 쪽(사수 쪽)에서 본 클램프")
    render(c, (0.5, -0.4, -1), (0, 1, 0), "phone-clamp - camera side.png", opacity={"phone": 0.6},
           legend=LEGEND, title="카메라 쪽(표적 쪽)에서 본 클램프")
    render(c, (0, -1, 0.0001), (0, 0, 1), "phone-clamp - section.png", clip=((0, -1, 0), (0, -80, 0)),
           legend=LEGEND, zoom=1.0, title="긴 팔 단면(y=-80mm): 홈 턱, 폰, 두께 조")
    clamp_sheet(c, "phone-clamp - 3 views.png")
    g, mount = on_gun(c)
    three_view(on_gun(c, printed_mount=True)[0], "phone-clamp on k2c1 - 3-view.png", title="클램프형 거치대를 자체 레일 마운트로 K2C1 에 단 모습")
    render(g, (1, 0.35, 0.25), (0, 1, 0), "phone-clamp on k2c1.png", opacity={"frame": 0.6, "phone": 0.7},
           legend=[("clamp", "Olight 레일 마운트 + 받침 + mount-adapter(외곽 크기)"), ("frame", "폰 클램프(긴 팔이 총열과 직각)"),
                   ("laser-holder", "레이저 홀더(소염기에 씌움)"), ("pointer", "Ø23 레이저 포인터"), ("beam", "레이저(총열 축)")],
           zoom=1.0, title="K2C1 장착(케이스 거치대와 같은 받침)")
    gp, mount_p = on_gun(c, printed_mount=True)
    render(gp, (1, 0.45, 0.9), (0, 1, 0), "phone-clamp on k2c1 - close-up.png", opacity={"frame": 0.6},
           legend=[("clamp", "자체 레일 마운트(좌우 클램프 블록)"), ("frame", "폰 클램프"), ("knob", "노브 나사"), ("phone", "폰 화면(사수 쪽)")],
           zoom=1.2, focus=mount_p, title="장착 부위 확대: 화면은 사수 쪽, 카메라는 표적 쪽")
