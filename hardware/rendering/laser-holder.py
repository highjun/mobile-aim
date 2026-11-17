"""Renders the laser holder with the knob screw, a Ø23 laser pointer and the rotary switch
(hardware/laser-switch). Run step2stl.py (freecadcmd) first.

Run: python laser-holder.py  -> writes ../renders/laser-holder - knob.png
"""
import os

import numpy as np
import pyvista as pv
import trimesh
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
M = os.environ.get("MOBILEAIM_RENDER_DIR", "/tmp/mobileaim-render") + "/"  # step2stl.py output
FONT = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothic.ttf", 26)

holder = trimesh.load(M + "laser-holder__laser-holder.stl")
knob = trimesh.load(M + "laser-holder__knob.stl")
# knob on the boss face (x=24), bolt axis along x
knob.apply_transform(trimesh.transformations.rotation_matrix(np.pi / 2, [0, 1, 0]))
knob.apply_translation([24 + 9, 0, 30])
bolt = trimesh.creation.cylinder(radius=2, height=16, transform=trimesh.transformations.rotation_matrix(np.pi / 2, [0, 1, 0]))
bolt.apply_translation([33 - 8, 0, 30])
pointer = trimesh.creation.cylinder(radius=11.5, height=150)
pointer.apply_translation([0, 0, 5 + 75])          # sits on the bore bottom z=5, sticks out the front
switch = trimesh.load(os.path.join(HERE, "..", "laser-switch", "laser-switch.stl"))
switch.apply_translation(-switch.bounds.mean(0))
switch.apply_transform(trimesh.transformations.rotation_matrix(np.pi / 2, [0, 1, 0]))
switch.apply_translation([0, 0, 120])               # around the pointer's button, outside the holder

parts = [(holder, "#8e44ad", 0.55), (knob, "#e08a1e", 1), (bolt, "#777777", 1), (pointer, "#222222", 1), (switch, "#2e86c0", 1)]
legend = [("#8e44ad", "레이저 홀더(앞 구멍 Ø23.4)"), ("#e08a1e", "노브(M4 육각 볼트 머리를 끼움)"),
          ("#777777", "M4×16 볼트: 옆 홈에 넣은 너트를 지나 포인터를 누름"), ("#222222", "Ø23 레이저 포인터"),
          ("#2e86c0", "회전 스위치")]
p = pv.Plotter(off_screen=True, window_size=(1400, 900)); p.set_background("white")
for m, c, o in parts:
    p.add_mesh(pv.wrap(m), color=c, opacity=o)
p.view_vector((1, 0.6, 0.5), (0, 1, 0)); p.camera.zoom(1.3)
img = Image.fromarray(p.screenshot(return_img=True)); p.close()
a = np.asarray(img.convert("L")) < 250; ys, xs = np.where(a)
img = img.crop((max(xs.min() - 20, 0), max(ys.min() - 20, 0), min(xs.max() + 20, img.width), min(ys.max() + 20, img.height)))
sheet = Image.new("RGB", (img.width + 760, max(img.height, 280) + 50), "white"); sheet.paste(img, (0, 50))
d = ImageDraw.Draw(sheet); d.text((16, 10), "레이저 홀더: 앞 구멍을 키우고 노브 나사로 포인터 고정", fill="black", font=FONT)
for i, (c, t) in enumerate(legend):
    y = 80 + 44 * i
    d.rectangle((img.width + 10, y, img.width + 40, y + 26), fill=c); d.text((img.width + 52, y - 2), t, fill="black", font=FONT)
out = os.path.join(HERE, "..", "renders", "laser-holder - knob.png"); sheet.save(out); print("saved", out)
