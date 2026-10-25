"""Laser holder, final shape: knob screws on the rear socket press the flash hider. Run: freecadcmd socket-knob-version.py

Starts from the holder printed for the 2026-10-19 live fire (laser-holder - printed.step), which did not fit:
- Pointer bore (front, z 5..55): Ø22.6 -> Ø23.4. A Ø23 pointer did not go in.
- Rear socket (z -40..0, Ø24 inside, Ø30 outside): did not fit the K2C1 flash hider. Two bosses on opposite
  sides (+y, -y) at z = KNOB_Z, each with a slide-in M4 hex-nut slot and a radial Ø4.4 bolt hole into the
  socket. Turning the knobs (knob.step on M4x16 hex bolts) presses the flash hider from both sides, so the
  holder fits flash hiders thinner than the socket.
- No side boss for a pointer knob: the pointer is a press fit in the Ø23.4 bore.
Writes laser-holder.step.
"""
import os

import Part
from FreeCAD import Vector as V

HERE = os.path.dirname(os.path.abspath(__file__))
BORE_D = 23.4
KNOB_Z = -20.0
SOCKET_R = 15.0
BOSS_OUT = 24.0                 # boss face distance from the axis (knob sits here)
BOSS_W, BOSS_H = 14.0, 16.0     # boss width (x) and height (z)
M4_CLEAR = 4.4
NUT_AF, NUT_T = 7.3, 3.4        # M4 hex nut 7.0 AF x 3.2, plus print clearance

body = Part.Shape(); body.read(os.path.join(HERE, "laser-holder - printed.step"))
body = body.cut(Part.makeCylinder(BORE_D / 2, 51, V(0, 0, 5)))
for sign in (1, -1):
    y0 = 13.0 if sign > 0 else -BOSS_OUT
    boss = Part.makeBox(BOSS_W, BOSS_OUT - 13.0, BOSS_H, V(-BOSS_W / 2, y0, KNOB_Z - BOSS_H / 2))
    body = body.fuse(boss).removeSplitter()
for sign in (1, -1):
    # keep the socket cavity clear where the boss overlapped the wall's inside
    ny = 18.5 if sign > 0 else -18.5 - NUT_T
    # nut slot: NUT_T thick along y, NUT_AF wide along x, open to +z so the nut drops in from the front
    body = body.cut(Part.makeBox(NUT_AF, NUT_T, BOSS_H / 2 + NUT_AF / 2 + 1, V(-NUT_AF / 2, ny, KNOB_Z - NUT_AF / 2)))
    # radial bolt hole through the boss and the socket wall
    body = body.cut(Part.makeCylinder(M4_CLEAR / 2, BOSS_OUT - 10, V(0, sign * 10, KNOB_Z), V(0, sign, 0)))
body = body.removeSplitter()
body.exportStep(os.path.join(HERE, "laser-holder.step"))
print(body.isValid(), body.BoundBox, round(body.Volume, 1))
