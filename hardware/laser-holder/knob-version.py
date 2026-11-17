"""Laser holder: widen the pointer bore and add a knob screw. Run: freecadcmd knob-version.py

- Pointer bore (front, z 5..55): Ø22.6 -> Ø23.4. A Ø23 pointer did not go in.
- Knob screw: M4 radial bolt at z=30 through a side boss with a slide-in hex-nut slot, pressing the
  pointer. knob.step: printed knob that holds an M4 hex bolt head (M4x16).
Run it once on the original laser-holder.step (git show 41afcb0:3d-modeling/laser-holder/laser-holder.step):
it overwrites that file, so the change stays in git history, and writes knob.step.
"""
import math
import os

import FreeCAD
import Part
from FreeCAD import Vector as V

HERE = os.path.dirname(os.path.abspath(__file__))
BORE_D = 23.4
KNOB_Z = 30.0
M4_CLEAR = 4.4
NUT_AF, NUT_T = 7.3, 3.4   # M4 hex nut 7.0 AF x 3.2, plus print clearance

body = Part.Shape(); body.read(os.path.join(HERE, "laser-holder.step"))

# 1. wider pointer bore (z 5..55, plus a little past the open end)
body = body.cut(Part.makeCylinder(BORE_D / 2, 51, V(0, 0, 5)))

# 2. boss on +x: x 13..24, y -7..7, z KNOB_Z-8..KNOB_Z+8
boss = Part.makeBox(11, 14, 16, V(13, -7, KNOB_Z - 8))
body = body.fuse(boss).removeSplitter()
# re-cut the bore in case the boss reached into it
body = body.cut(Part.makeCylinder(BORE_D / 2, 51, V(0, 0, 5)))
# nut slot: NUT_T thick along x, NUT_AF wide along y, open to +z so the nut drops in from the top
body = body.cut(Part.makeBox(NUT_T, NUT_AF, 8 + NUT_AF / 2 + 1, V(17.5, -NUT_AF / 2, KNOB_Z - NUT_AF / 2)))
# bolt hole along x through the boss and the wall
body = body.cut(Part.makeCylinder(M4_CLEAR / 2, 30, V(5, 0, KNOB_Z), V(1, 0, 0)))
body.exportStep(os.path.join(HERE, "laser-holder.step"))

# knob: Ø22 x 9 disk with 10 finger scallops, hex pocket for the M4 bolt head (7.0 AF -> 7.3), Ø4.4 hole
knob = Part.makeCylinder(11, 9)
for i in range(10):
    a = 2 * math.pi * i / 10
    knob = knob.cut(Part.makeCylinder(2.2, 9, V(12.2 * math.cos(a), 12.2 * math.sin(a), 0)))
hexagon = Part.makePolygon([V(NUT_AF / math.sqrt(3) * math.cos(math.pi / 3 * k), NUT_AF / math.sqrt(3) * math.sin(math.pi / 3 * k), 0) for k in range(7)])
knob = knob.cut(Part.Face(hexagon).extrude(V(0, 0, 3.2)))
knob = knob.cut(Part.makeCylinder(M4_CLEAR / 2, 9))
knob.exportStep(os.path.join(HERE, "knob.step"))
print("holder", body.BoundBox, "valid", body.isValid(), "knob", knob.BoundBox, "valid", knob.isValid())
