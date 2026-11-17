"""STEP -> STL meshes for the rendering scripts. Run with FreeCAD: freecadcmd step2stl.py

Writes every STEP under hardware/ to $MOBILEAIM_RENDER_DIR/<folder>__<name>.stl
(default /tmp/mobileaim-render).
"""
import glob
import os

import FreeCAD  # noqa: F401  (needed before Part under freecadcmd)
import MeshPart
import Part

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.environ.get("MOBILEAIM_RENDER_DIR", "/tmp/mobileaim-render")
os.makedirs(OUT, exist_ok=True)
for f in sorted(glob.glob(os.path.join(ROOT, "*", "*.step"))):
    folder = os.path.basename(os.path.dirname(f))
    s = Part.Shape(); s.read(f)
    MeshPart.meshFromShape(Shape=s, LinearDeflection=0.05, AngularDeflection=0.2).write(
        os.path.join(OUT, "%s__%s.stl" % (folder, os.path.basename(f)[:-5])))
    print("ok", folder, os.path.basename(f))
