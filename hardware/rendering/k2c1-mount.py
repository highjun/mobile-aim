"""K2C1 + 2-piece phone case holder (phone-case), mounted straight on the Olight Picatinny rail mount,
with the laser holder (+ two knob screws on the socket, Ø23 pointer and beam) on the muzzle.

Inputs (not in git):
- /tmp/mobileaim-render/*.stl: run step2stl.py with freecadcmd first

The case has four T-tongue pillars on its long side walls (two per side, at case y = 0 and 48.87).
Each tongue sticks 5.6 mm out of the side wall (5 mm neck, 10 mm flange) and runs along the case
normal. The pillar at y = 48.87 lines up with the lens (y = 49.0), so with the lens over the bore it
is the one over the rail. The phone sits with the camera edge on top (lens above the bore): the -x
pillar's tongue goes into the Olight mount and the -x side wall rests on the mount top.

Run: python k2c1-mount.py  -> writes the 3-view PNG to ../renders/.
"""
import os
import numpy as np
import pyvista as pv
import trimesh
from PIL import Image

from shared import (HOLDER_COLOR, HOLDER_STL, KNOB_COLOR, OLIGHT_COLOR, OLIGHT_HW_COLOR, POINTER_COLOR, olight_mount,
                    socket_knobs)

SC = 77.95; BORE_Y = 0.8305; BORE_X = -0.255
WALL_X_MIN = -48.8                        # -x case side wall in the case mesh frame (mm)
CASE_Z_MID = (-0.82 + 30.63) / 2          # middle of the case + cap along the case normal
LENS_X, LENS_Y = 24.7, 49.0               # lens center in the phone mesh frame (mm)

HERE = os.path.dirname(os.path.abspath(__file__))
s = trimesh.load(os.path.join(HERE, "..", "reference", "daewoo-k2c1.obj"))
gun = trimesh.util.concatenate([g for g in s.geometry.values()])
gun.apply_translation([-BORE_X, -BORE_Y, 0]); gun.apply_scale(SC)   # bore axis = (x0,y0), mm
y_rail = (1.389 - BORE_Y) * SC; z0 = -1.3 * SC                      # mount position on front rail
olight, olight_hw = olight_mount()                                   # rail top at y = 0 in its frame
for m in (olight, olight_hw):
    m.apply_translation([0, y_rail, z0])
mount_top = y_rail + 22
M = "/tmp/mobileaim-render/"  # step2stl.py output
phone = trimesh.load(M + "reference__galaxy-s25-ultra.stl"); case = trimesh.load(M + "phone-case__case.stl"); cap = trimesh.load(M + "phone-case__cap.stl")


def place(m):
    """Phone long axis across the gun, lens over the bore, -x case side wall on the mount top."""
    v = m.vertices.copy(); px, py, pz = v[:, 0], v[:, 1], v[:, 2]
    m.vertices = np.column_stack([LENS_Y - py, mount_top + (px - WALL_X_MIN), z0 - CASE_Z_MID + pz])
    return m


for m in (phone, case, cap):
    place(m)
lens_y = mount_top + (LENS_X - WALL_X_MIN)
lens = trimesh.creation.cylinder(radius=8.25, height=4); lens.apply_translation([0, lens_y, phone.bounds[0][2] - 2])
bore = trimesh.creation.cylinder(radius=1.5, height=1100)

# laser holder on the muzzle, placed as in phone-clamp.py on_gun(): socket over the flash hider,
# Ø23 pointer in the front bore, two knob screws radial on the socket (design concept), beam along the bore axis
muzzle = gun.bounds[0][2]
holder_t = np.array([[-1, 0, 0, 0], [0, 1, 0, 0], [0, 0, -1, muzzle + 10 - 40], [0, 0, 0, 1]], float)
holder = trimesh.load(M + HOLDER_STL + ".stl"); holder.apply_transform(holder_t)
knobs = [k for k, _ in socket_knobs()]                 # as in parts.py boresighter_assembly()
for k in knobs:
    k.apply_transform(holder_t)
pointer = trimesh.creation.cylinder(radius=11.3, height=150)
pointer.apply_translation([0, 0, holder.bounds[0][2] + 45 - 75])
beam = trimesh.creation.cylinder(radius=1.2, height=400)
beam.apply_translation([0, 0, pointer.bounds[0][2] - 200])
P = lambda m: pv.wrap(m)
views = {}
for name, vv, up in [("side", (1, 0, 0), (0, 1, 0)), ("front", (0, 0, -1), (0, 1, 0)), ("iso", (1, 0.5, -0.8), (0, 1, 0))]:
    p = pv.Plotter(off_screen=True, window_size=(1400, 800))
    p.set_background("white")
    p.add_mesh(P(gun), color="#5a5f66"); p.add_mesh(P(olight), color=OLIGHT_COLOR); p.add_mesh(P(olight_hw), color=OLIGHT_HW_COLOR)
    p.add_mesh(P(case), color="#222222"); p.add_mesh(P(cap), color="#333333"); p.add_mesh(P(phone), color="#9aa4ad", opacity=0.6)
    p.add_mesh(P(lens), color="red"); p.add_mesh(P(bore), color="red")
    p.add_mesh(P(holder), color=HOLDER_COLOR); [p.add_mesh(P(k), color=KNOB_COLOR) for k in knobs]
    p.add_mesh(P(pointer), color=POINTER_COLOR); p.add_mesh(P(beam), color="#ff0000")
    p.view_vector(vv, up)
    if name != "iso":
        p.camera.parallel_projection = True
    views[name] = Image.fromarray(p.screenshot(return_img=True))
    p.close()


def trim(img, pad=20):
    """Crops the white margin around the model."""
    a = np.asarray(img.convert("L")) < 250
    ys, xs = np.where(a)
    return img.crop((max(xs.min() - pad, 0), max(ys.min() - pad, 0), min(xs.max() + pad, img.width), min(ys.max() + pad, img.height)))


# side and front on top, iso below
side, front, iso = (trim(views[k]) for k in ("side", "front", "iso"))
sheet = Image.new("RGB", (max(side.width + front.width, iso.width), max(side.height, front.height) + iso.height), "white")
sheet.paste(side, (0, 0))
sheet.paste(front, (side.width, 0))
sheet.paste(iso, (0, max(side.height, front.height)))
out = os.path.join(HERE, "..", "renders", "k2c1-mount - lens-up.png")
sheet.save(out)
print("saved", out)
