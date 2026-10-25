"""Laser holder for the Academy K2C1 airsoft rifle. Run: freecadcmd airsoft-version.py

Starts from the printed K2C1 holder (laser-holder - printed.step) and changes the rear end:
- Rear socket (z -40..0): a plain Ø22.5 bore for the ~Ø22 airsoft muzzle. The K2C1 version's centre cone, which
  goes into the flash hider, is left out because the airsoft bore size is unknown.
- Pointer bore (front, z 5..55): Ø23.4, as in the K2C1 version.
- Two bosses on opposite sides (+y, -y) at z = KNOB_Z, each with a heat-set insert hole for an M5 x 25 knob
  screw (B type, 25 mm head). The boss face sits so the screw tip passes the socket wall and presses the muzzle
  with the head about 4 mm off the face; a thinner muzzle (down to about Ø14) takes more turns.
Writes laser-holder - airsoft.step and laser-holder - airsoft.stl.
"""
import os

import Mesh
import Part
from FreeCAD import Vector as V

HERE = os.path.dirname(os.path.abspath(__file__))
BORE_D = 23.4                   # Ø23 laser pointer
SOCKET_D = 22.5                 # ~Ø22 airsoft muzzle + 0.5 clearance
SOCKET_R = 15.0                 # socket outside radius, as the K2C1 version
KNOB_Z = -20.0
BOSS_OUT = 32.0                 # boss face distance from the axis
BOSS_W, BOSS_H = 14.0, 16.0     # boss width (x) and height (z)
INSERT_D, INSERT_DEPTH = 6.4, 10.5   # common M5 heat-set insert: Ø7.1 x up to 10 mm, Ø6.4 hole
M5_CLEAR = 5.5

body = Part.Shape(); body.read(os.path.join(HERE, "laser-holder - printed.step"))
body = body.cut(Part.makeCylinder(BORE_D / 2, 51, V(0, 0, 5)))
# rebuild the rear socket as a plain tube
body = body.cut(Part.makeCylinder(SOCKET_R + 0.5, 40.5, V(0, 0, -40.5)))
tube = Part.makeCylinder(SOCKET_R, 40, V(0, 0, -40)).cut(Part.makeCylinder(SOCKET_D / 2, 40, V(0, 0, -40)))
body = body.fuse(tube).removeSplitter()
for sign in (1, -1):
    y0 = 13.0 if sign > 0 else -BOSS_OUT
    boss = Part.makeBox(BOSS_W, BOSS_OUT - 13.0, BOSS_H, V(-BOSS_W / 2, y0, KNOB_Z - BOSS_H / 2))
    body = body.fuse(boss)
body = body.cut(Part.makeCylinder(SOCKET_D / 2, 40, V(0, 0, -40)))       # bosses must not close the bore
for sign in (1, -1):
    # insert hole from the boss face, then a clearance hole down into the socket
    body = body.cut(Part.makeCylinder(INSERT_D / 2, INSERT_DEPTH, V(0, sign * BOSS_OUT, KNOB_Z), V(0, -sign, 0)))
    body = body.cut(Part.makeCylinder(M5_CLEAR / 2, BOSS_OUT - INSERT_DEPTH, V(0, sign * 5, KNOB_Z), V(0, sign, 0)))
body = body.removeSplitter()
body.exportStep(os.path.join(HERE, "laser-holder - airsoft.step"))
Mesh.Mesh(body.tessellate(0.02)).write(os.path.join(HERE, "laser-holder - airsoft.stl"))
print("ok", body.isValid(), body.BoundBox, round(body.Volume, 1))
