"""Renders each printed part (case, cap, laser-holder, knob screw), the M3 tapping screw, and the two
assemblies (phone holder, laser boresighter), from an oblique angle, in the same style on white, as PNGs
(margin cropped, model centered). Run step2stl.py (freecadcmd) first.

Run: python parts.py  -> writes ../renders/parts - <name>.png
"""
import os

import numpy as np
import pyvista as pv
import trimesh
from PIL import Image

from shared import (HOLDER_COLOR, HOLDER_STL, KNOB_COLOR, OLIGHT_COLOR, OLIGHT_HW_COLOR, POINTER_COLOR, SWITCH_COLOR,
                    olight_mount, rotary_switch, socket_knobs)

HERE = os.path.dirname(os.path.abspath(__file__))
M = "/tmp/mobileaim-render/"
OUT = os.path.join(HERE, "..", "renders")
SIZE = 1200          # output square side (px) of a single part
COLOR = "#9aa0a8"
METAL = "#c9ccd1"
R = trimesh.transformations.rotation_matrix

# part -> (STL, view vector, view up). Each angle shows the part's features:
# case: open frame (screen side) and the four T pillars; cap: camera window and the six M3 holes;
# holder: rear socket (seen from behind) and the front bore.
PARTS = {
    "case": ("phone-case__case", (0.55, -0.6, 1.0), (0, 1, 0)),
    "cap": ("phone-case__cap", (0.55, -0.6, -1.0), (0, 1, 0)),
    "laser-holder": (HOLDER_STL, (1.0, 0.8, -0.6), (0, 1, 0)),
}

# phone holder placement, as in k2c1-mount.py (gun left out: rail at y = 0, mount at z = 0)
WALL_X_MIN = -48.8                        # -x case side wall in the case mesh frame (mm)
CASE_Z_MID = (-0.82 + 30.63) / 2          # middle of the case + cap along the case normal
LENS_Y = 49.0                             # lens center y in the phone mesh frame (mm)
MOUNT_TOP = 22                            # Olight mount top above the rail
CAP_OUT_Z = -0.82                         # cap outer face (camera side) in the case mesh frame
# M3 holes through cap and case, in the case mesh frame (found by sectioning the STLs)
M3_HOLES = [(x, y) for x in (-43.8, 44.55) for y in (86.4, -0.23, -86.85)]


def knob_screw():
    """Knob (STL, hex pocket at z = 0, grip top at z = 9) on an M4 bolt whose shank sticks out of the
    pocket along -z: the knob screw that is turned by hand."""
    knob = trimesh.load(M + "laser-holder__knob.stl")
    shank = trimesh.creation.cylinder(radius=2, height=12)
    shank.apply_translation([0, 0, -6])
    return knob, shank


def tapping_screw(length=12.0, pitch=1.06):
    """M3 self-tapping screw along +z: pan head over z < 0 with a cross recess, threaded shank
    (rings of a saw-tooth profile), pointed tip at z = length."""
    root, crest, tip = 1.1, 1.5, 2.5
    pts = [(0.0, length)]
    z = length
    k = 0
    while z > 0.3:
        z -= pitch / 2
        r = crest if k % 2 == 0 else root
        r = min(r, (length - z) / tip * crest + 0.05)
        pts.append((r, max(z, 0.0)))
        k += 1
    pts += [(root, 0.0), (2.8, 0.0), (2.8, -0.8), (2.4, -1.6), (1.4, -2.2), (0.0, -2.4)]
    screw = trimesh.creation.revolve(np.array(pts[::-1]), sections=64)
    for ang in (0, np.pi / 2):
        slot = trimesh.creation.box([3.2, 0.7, 2.0])
        slot.apply_transform(R(ang, [0, 0, 1]))
        slot.apply_translation([0, 0, -2.4])
        screw = screw.difference(slot)
    return screw


def place_case(m):
    """Case mesh frame -> world, as k2c1-mount.py place(): phone long axis across the rail,
    -x case side wall on the mount top."""
    v = m.vertices.copy()
    m.vertices = np.column_stack([LENS_Y - v[:, 1], MOUNT_TOP + (v[:, 0] - WALL_X_MIN), v[:, 2] - CASE_Z_MID])
    return m


def phone_holder_assembly():
    """Phone in the case, cap on, six tapping screws in the cap holes, the -x T pillar in the Olight mount."""
    phone, case, cap = (place_case(trimesh.load(M + n + ".stl"))
                        for n in ("reference__galaxy-s25-ultra", "phone-case__case", "phone-case__cap"))
    olight, hw = olight_mount()
    parts = [(olight, OLIGHT_COLOR, 1), (hw, OLIGHT_HW_COLOR, 1), (case, "#3a3d42", 1), (cap, "#55595f", 1), (phone, "#9aa4ad", 1)]
    for x, y in M3_HOLES:
        s = tapping_screw()                             # head outside the cap (-z), tip into the case
        s.apply_translation([x, y, CAP_OUT_Z])
        place_case(s)
        parts.append((s, METAL, 1))
    return parts


def boresighter_assembly():
    """Laser holder + Ø23 pointer in the front bore (sticking out the front only) + rotary switch on the
    pointer's button + two knob screws radial on the rear socket (design concept; holder frame: z along the
    bore, socket at z < 0)."""
    holder = trimesh.load(M + HOLDER_STL + ".stl")
    pointer = trimesh.creation.cylinder(radius=11.5, height=150)
    pointer.apply_translation([0, 0, 5 + 75])          # sits on the bore bottom z = 5, sticks out the front
    switch = rotary_switch()                            # ring around the pointer's button, outside the holder
    parts = [(holder, HOLDER_COLOR, 1), (pointer, POINTER_COLOR, 1), (switch, SWITCH_COLOR, 1)]
    for knob, shank in socket_knobs():
        parts += [(knob, KNOB_COLOR, 1), (shank, METAL, 1)]
    return parts


def render(name, parts, vv, up, size=(SIZE, SIZE), zoom=0.85):
    """parts: [(mesh, color, opacity)]; saves the cropped, centered view on a white sheet of `size`."""
    p = pv.Plotter(off_screen=True, window_size=(1600, 1600), lighting="three lights")
    p.set_background("white")
    for m, c, o in parts:
        p.add_mesh(pv.wrap(m), color=c, opacity=o, smooth_shading=False, specular=0.2)
    p.enable_anti_aliasing("ssaa")
    p.view_vector(vv, up)
    p.camera.parallel_projection = True
    p.reset_camera()
    p.camera.zoom(zoom)
    img = Image.fromarray(p.screenshot(return_img=True))
    p.close()
    a = np.asarray(img.convert("L")) < 250
    ys, xs = np.where(a)
    img = img.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    k = min(size[0] * 0.9 / img.width, size[1] * 0.9 / img.height)
    img = img.resize((round(img.width * k), round(img.height * k)), Image.LANCZOS)
    sheet = Image.new("RGB", size, "white")
    sheet.paste(img, ((size[0] - img.width) // 2, (size[1] - img.height) // 2))
    out = os.path.join(OUT, f"parts - {name}.png")
    sheet.save(out)
    print("saved", out)


if __name__ == "__main__":
    for name, (stl, vv, up) in PARTS.items():
        render(name, [(trimesh.load(M + stl + ".stl"), COLOR, 1)], vv, up)
    # knob screw: knurled grip and top face from above, bolt shank below (screw axis vertical)
    knob, shank = knob_screw()
    render("knob", [(knob, COLOR, 1), (shank, METAL, 1)], (0.6, -0.8, 0.5), (0, 0, 1))
    # tapping screw lying across the view: cross recess head on the left, pointed tip on the right
    s = tapping_screw()
    render("m3-screw", [(s, METAL, 1)], (-0.35, -1.0, 0.25), (0, 1, 0), size=(SIZE, SIZE // 2))
    render("phone-holder assembly", phone_holder_assembly(), (0.75, 0.1, -1.0), (0, 1, 0), size=(1600, 1200))
    render("boresighter assembly", boresighter_assembly(), (1.0, 0.45, -0.75), (0, 1, 0), size=(1600, 1000))
