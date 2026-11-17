"""Models shared by parts.py and k2c1-mount.py: the Olight Picatinny rail mount (built from trimesh
primitives) and the two knob screws on the laser holder's rear socket.
"""
import os

import numpy as np
import trimesh

M = "/tmp/mobileaim-render/"
# holder as printed (laser-holder - printed.step: Ø22.6 front bore, no side boss), not the unprinted knob version
HOLDER_STL = "laser-holder__laser-holder"
R = trimesh.transformations.rotation_matrix
OLIGHT_COLOR = "#2e3135"                  # black anodized aluminium
OLIGHT_HW_COLOR = "#6b7078"               # steel dial and screws
# laser boresighter: black PLA prints in slightly different tones so the parts stay apart, dark metal pointer
HOLDER_COLOR = "#2a2c2f"                  # laser holder (black PLA)
KNOB_COLOR = "#43464b"                    # knob screws (lighter charcoal PLA)
SWITCH_COLOR = "#18191b"                  # rotary switch (near-black PLA)
POINTER_COLOR = "#4f5864"                 # Ø23 laser pointer (dark gunmetal)
SWITCH_STL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "laser-switch",
                          "laser-switch.stl")
SWITCH_Z = 115.0     # switch ring bottom along the pointer axis (holder frame), around the button, past the holder


def rotary_switch():
    """Rotary switch ring (hardware/laser-switch) around the Ø23 pointer, in the holder frame (z along the bore).
    The STL's hole axis is already its z axis through x = y = 0 (Ø~23 bore), so it only moves along z:
    the pointer passes through the ring's hole."""
    sw = trimesh.load(SWITCH_STL)
    sw.apply_translation([0, 0, SWITCH_Z - sw.bounds[0][2]])
    return sw


def _box(x0, x1, y0, y1, z0, z1):
    b = trimesh.creation.box([x1 - x0, y1 - y0, z1 - z0])
    b.apply_translation([(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2])
    return b


def olight_mount():
    """Olight Picatinny rail mount in the 35.5 x 31 x 79 envelope (x across the rail, y up, z along the rail),
    rail top at y = 0, mount from y = -9 to 22, centered on x = 0 and z = 0.

    - bottom: two jaws hanging over the rail sides, each with an inward lip that bites the rail groove
    - top: flat seat with a T slot along z (5.6 mm neck, 10.6 mm flange cavity) for the case T pillar
    - +x side: clamp plate with the locking dial (slotted) and two hex socket screws
    Returns (body, hardware): the hardware is the dial and the screw heads.
    """
    L = 79 / 2
    body = _box(-17.75, 14.25, 4, 22, -L, L)
    jaw_l = _box(-17.75, -10.8, -9, 4, -L, L)
    lip_l = _box(-10.8, -8.6, -6.5, -2.5, -L, L)
    jaw_r = _box(10.8, 14.25, -9, 4, -L, L)
    lip_r = _box(8.6, 10.8, -6.5, -2.5, -L, L)
    plate = _box(14.25, 15.75, -9, 13, -26, 26)
    m = trimesh.boolean.union([body, jaw_l, lip_l, jaw_r, lip_r, plate])
    # T slot along z, open at both ends
    m = m.difference(_box(-2.8, 2.8, 18.5, 23, -L - 1, L + 1))
    m = m.difference(_box(-5.3, 5.3, 15.5, 18.5, -L - 1, L + 1))
    # chamfers on the top long edges
    for sx in (-1, 1):
        c = trimesh.creation.box([4, 4, 2 * L + 2])
        c.apply_transform(R(np.pi / 4, [0, 0, 1]))
        c.apply_translation([sx * (17.75 if sx < 0 else 14.25), 22, 0])
        m = m.difference(c)
    # locking dial on the side plate (axis along x) with a screwdriver slot, two hex socket screws
    dial = trimesh.creation.cylinder(radius=7.5, height=2.0, sections=64)
    dial.apply_transform(R(np.pi / 2, [0, 1, 0]))
    dial.apply_translation([16.75, 2, 0])
    dial = dial.difference(_box(17.2, 18, 1.3, 2.7, -5, 5))
    hw = [dial]
    for z in (-19, 19):
        h = trimesh.creation.cylinder(radius=3, height=0.8, sections=48)
        h.apply_transform(R(np.pi / 2, [0, 1, 0]))
        h.apply_translation([16.15, 2, z])
        hexa = trimesh.creation.cylinder(radius=1.4, height=1.0, sections=6)
        hexa.apply_transform(R(np.pi / 2, [0, 1, 0]))
        hexa.apply_translation([16.5, 2, z])
        hw.append(h.difference(hexa))
    return m, trimesh.util.concatenate(hw)


SOCKET_R = 15.0      # outer radius of the holder's rear socket
KNOB_R = 24.0        # boss face on the socket where each knob sits (laser-holder/socket-knob-version.py)
KNOB_Z = -20.0       # knob screw position along the bore (socket spans z = -40 .. 0)


def socket_knobs():
    """Two knob screws on the laser holder's rear socket, in the holder frame (z along the bore, socket at
    z < 0): radial, on opposite sides (+y and -y), knob face on the socket surface, M4 shank through the
    socket wall pressing the flash hider, knob on the boss face (laser-holder/socket-knob-version.py).
    Returns [(knob, shank)]."""
    out = []
    for ang in (-np.pi / 2, np.pi / 2):                 # knob +z -> +y, -> -y
        knob = trimesh.load(M + "laser-holder__knob.stl")
        shank = trimesh.creation.cylinder(radius=2, height=12)
        shank.apply_translation([0, 0, -6])
        for m in (knob, shank):
            m.apply_transform(R(ang, [1, 0, 0]))
            m.apply_translation([0, 0, KNOB_Z])
            sign = 1 if ang < 0 else -1
            m.apply_translation([0, sign * KNOB_R, 0])
        out.append((knob, shank))
    return out
