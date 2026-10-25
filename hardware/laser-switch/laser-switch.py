"""Rotary switch ring for the Ø23 laser pointer. Run: freecadcmd laser-switch.py

The ring slides onto the pointer from its bottom face with the pointer's push button in the open slot (OFF).
Turning the ring about 75° runs the button up a cam ramp until the bore presses it down (ON). The laser stays on
without holding the button, and a bump against the pointer does not turn it on.
- Bore Ø23.2 around the Ø23 pointer body. The cam pocket is open at the bottom face, z 0..CAM_H.
- Cam radius by angle: OFF slot (button free) 0..OFF_DEG, ramp down to the bore OFF_DEG..ON_DEG.
- Round outside with grip grooves. A dot on the top face marks ON, a line marks the OFF slot.
Writes laser-switch.step and laser-switch.stl.
"""
import math
import os

import Mesh
import Part
from FreeCAD import Vector as V

HERE = os.path.dirname(os.path.abspath(__file__))
BORE_R = 23.2 / 2
OUT_R = 17.5                     # 2.2mm wall between the OFF slot and the grip grooves
H = 14.0
CAM_H = 9.0                     # pocket height from the bottom face, covers the Ø5.5 button with clearance
FREE_R = 14.4                   # pocket radius where the button stands free
OFF_DEG, ON_DEG = 30.0, 90.0    # OFF slot width, end of the ramp
GRIPS, GRIP_R = 18, 1.2


def cam_r(deg):
    if deg <= OFF_DEG:
        return FREE_R
    if deg <= ON_DEG:
        t = (deg - OFF_DEG) / (ON_DEG - OFF_DEG)
        return FREE_R + (BORE_R - FREE_R) * (1 - math.cos(math.pi * t)) / 2   # smooth start and end
    return BORE_R


ring = Part.makeCylinder(OUT_R, H).cut(Part.makeCylinder(BORE_R, H))

# cam pocket: polar outline r(θ) from the OFF slot to the ramp end, closed through the axis
steps = 120
pts = [V(0, 0, 0)]
start = -OFF_DEG / 2
for i in range(steps + 1):
    deg = start + (ON_DEG + OFF_DEG / 2) * i / steps
    r = cam_r(deg - start)
    pts.append(V(r * math.cos(math.radians(deg)), r * math.sin(math.radians(deg)), 0))
pts.append(V(0, 0, 0))
pocket = Part.Face(Part.makePolygon(pts)).extrude(V(0, 0, CAM_H))
ring = ring.cut(pocket)

for i in range(GRIPS):
    a = 2 * math.pi * (i + 0.5) / GRIPS
    ring = ring.cut(Part.makeCylinder(GRIP_R, H, V((OUT_R + 0.3) * math.cos(a), (OUT_R + 0.3) * math.sin(a), 0)))

# marks on the top face: line over the OFF slot (θ = 0), dot over the ON end of the ramp
mark_r = (BORE_R + OUT_R) / 2
line = Part.makeBox(4.0, 1.2, 0.6, V(mark_r - 2.0, -0.6, H - 0.6))
ring = ring.cut(line)
on = math.radians(ON_DEG - OFF_DEG / 2)
ring = ring.cut(Part.makeCylinder(1.0, 0.6, V(mark_r * math.cos(on), mark_r * math.sin(on), H - 0.6)))

ring = ring.removeSplitter()
ring.exportStep(os.path.join(HERE, "laser-switch.step"))
mesh = Mesh.Mesh(ring.tessellate(0.02))
mesh.write(os.path.join(HERE, "laser-switch.stl"))
print("ok", ring.isValid(), round(ring.Volume, 1), "mm3")
