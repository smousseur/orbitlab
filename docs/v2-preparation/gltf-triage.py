#!/usr/bin/env python3
"""Triage a candidate launcher mesh for AST-1.

Usage:
    python gltf-triage.py <file.gltf|.glb> [...]
    python gltf-triage.py --piece <detached-part.gltf>

Answers one question: does this mesh decompose by nodes, without touching the
geometry? The fatal defect is a single welded shell spanning the whole vehicle
-- invisible in a preview, permanent once the model is adopted.

Seven checks, in the order in which they disqualify a candidate:

  C1  shells      how many connected components, and does one of them span the
                  whole height (the monolith)
  C2  nodes       is there a level of the tree where the groups are separated
  C3  axis        after the root transform, does the nose point along +Y
  C4  scale       is the stack roughly one unit tall
  C5  origin      is the base at y ~ 0
  C6  triangles   total budget, and how it is spread across the groups
  C7  resources   materials, and whether the referenced texture files exist

C1 and C2 are the only hard gates. C3 to C5 are fixed by one transform at export
time, C6 by decimating the nozzles, C7 by recovering the missing texture.

Pass --piece for a DETACHED PART: C1, C3, C4 and C5 are whole-stack criteria
and mean nothing on a fragment -- a stage has no nose, must NOT be one unit
tall, and a booster is legitimately one welded shell spanning its own height.
C3 to C5 are replaced by the world-space bounds, to be compared against those of
the matching group in the stack; C1 is reported without a verdict. C2 still
counts: it is what says whether a fairing can be detached from the stage it
ships with.

Output is ASCII on purpose. The default Windows console encodes in cp1252 and
raises UnicodeEncodeError on an accented character, which would make the triage
fail on its own report rather than on the mesh.

No dependency outside the standard library.
"""

import json
import math
import os
import struct
import sys
from collections import defaultdict

COMPONENT_TYPES = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2),
                   5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}
NUM_COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}

OK, WARN, BAD = "PASS", "WARN", "FAIL"


# --------------------------------------------------------------------------- io

def load(path):
    """Returns (gltf_json, [buffers]) for either a .gltf or a .glb."""
    base = os.path.dirname(os.path.abspath(path))
    if path.lower().endswith(".glb"):
        raw = open(path, "rb").read()
        magic, _, _ = struct.unpack_from("<III", raw, 0)
        if magic != 0x46546C67:
            raise ValueError("invalid GLB header")
        gltf, chunks, off = None, [], 12
        while off < len(raw):
            length, kind = struct.unpack_from("<II", raw, off)
            payload = raw[off + 8: off + 8 + length]
            if kind == 0x4E4F534A:
                gltf = json.loads(payload.decode("utf-8"))
            elif kind == 0x004E4942:
                chunks.append(payload)
            off += 8 + length + (-length % 4)
        return gltf, chunks

    gltf = json.load(open(path, encoding="utf-8"))
    buffers = []
    for buf in gltf.get("buffers", []):
        uri = buf.get("uri")
        if uri is None:
            buffers.append(b"")
        elif uri.startswith("data:"):
            import base64
            buffers.append(base64.b64decode(uri.split(",", 1)[1]))
        else:
            from urllib.parse import unquote
            buffers.append(open(os.path.join(base, unquote(uri)), "rb").read())
    return gltf, buffers


def accessor(gltf, buffers, index):
    acc = gltf["accessors"][index]
    if "bufferView" not in acc:
        return [(0,) * NUM_COMPONENTS[acc["type"]]] * acc["count"]
    view = gltf["bufferViews"][acc["bufferView"]]
    data = buffers[view.get("buffer", 0)]
    offset = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    fmt, size = COMPONENT_TYPES[acc["componentType"]]
    count = NUM_COMPONENTS[acc["type"]]
    stride = view.get("byteStride") or size * count
    spec = "<" + fmt * count
    return [struct.unpack_from(spec, data, offset + i * stride)
            for i in range(acc["count"])]


# ------------------------------------------------------------------- transforms

def quaternion_matrix(q):
    x, y, z, w = q
    return [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]


def multiply(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)]
            for i in range(4)]


def local_matrix(node):
    if "matrix" in node:
        m = node["matrix"]
        return [[m[j * 4 + i] for j in range(4)] for i in range(4)]
    t = node.get("translation", [0, 0, 0])
    r = quaternion_matrix(node.get("rotation", [0, 0, 0, 1]))
    s = node.get("scale", [1, 1, 1])
    return ([[r[i][j] * s[j] for j in range(3)] + [t[i]] for i in range(3)]
            + [[0, 0, 0, 1]])


IDENTITY = [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def transform(matrix, p):
    return [sum(matrix[i][j] * p[j] for j in range(3)) + matrix[i][3]
            for i in range(3)]


# ------------------------------------------------------------------- collection

class Piece:
    """A subtree, with its vertices already transformed into world space."""

    def __init__(self, name, node=None):
        self.name = name
        self.node = node
        self.points = []
        self.triangles = 0


def walk(gltf, buffers, index, matrix, piece, pieces, split_parent):
    node = gltf["nodes"][index]
    world = multiply(matrix, local_matrix(node))
    if node.get("mesh") is not None:
        for prim in gltf["meshes"][node["mesh"]]["primitives"]:
            if prim.get("mode", 4) != 4:
                continue
            positions = accessor(gltf, buffers, prim["attributes"]["POSITION"])
            for p in positions:
                piece.points.append(transform(world, p))
            if "indices" in prim:
                piece.triangles += gltf["accessors"][prim["indices"]]["count"] // 3
            else:
                piece.triangles += len(positions) // 3
    for child in node.get("children", []):
        target = piece
        if index == split_parent:
            name = gltf["nodes"][child].get("name") or f"node{child}"
            target = pieces.setdefault(name, Piece(name, child))
        walk(gltf, buffers, child, world, target, pieces, split_parent)


def parent_matrices(gltf):
    """node -> world matrix of its PARENT, so a walk can restart mid-tree."""
    result = {}

    def visit(index, matrix):
        result[index] = matrix
        world = multiply(matrix, local_matrix(gltf["nodes"][index]))
        for child in gltf["nodes"][index].get("children", []):
            visit(child, world)

    for root in gltf["scenes"][gltf.get("scene", 0)]["nodes"]:
        visit(root, IDENTITY)
    return result


def groups_under(gltf, buffers, parent, above):
    """The direct children of `parent` that carry geometry, largest first."""
    pieces = {}
    walk(gltf, buffers, parent, above, Piece("<ignore>"), pieces, parent)
    return sorted((p for p in pieces.values() if p.points),
                  key=lambda p: -p.triangles)


def subtree_stats(gltf):
    """Triangles per subtree, used to locate the separation level."""
    counts = {}

    def visit(index):
        if index in counts:
            return counts[index]
        node = gltf["nodes"][index]
        total = 0
        if node.get("mesh") is not None:
            for prim in gltf["meshes"][node["mesh"]]["primitives"]:
                if prim.get("mode", 4) != 4:
                    continue
                if "indices" in prim:
                    total += gltf["accessors"][prim["indices"]]["count"] // 3
                else:
                    total += gltf["accessors"][prim["attributes"]["POSITION"]]["count"] // 3
        for child in node.get("children", []):
            total += visit(child)
        counts[index] = total
        return total

    scene = gltf["scenes"][gltf.get("scene", 0)]
    for root in scene["nodes"]:
        visit(root)
    return counts


def find_split_parent(gltf, counts):
    """The deepest node carrying >=95 % of the triangles through >=2 children.

    That is the level at which the decomposition already exists. Returning None
    does NOT mean there is none: the separation may live at the scene-root
    level instead, which the caller handles (see the C2 block).
    """
    total = sum(counts[r] for r in gltf["scenes"][gltf.get("scene", 0)]["nodes"])
    if total == 0:
        return None, total
    best = None
    for index, node in enumerate(gltf["nodes"]):
        if index not in counts or counts[index] < 0.95 * total:
            continue
        children = [c for c in node.get("children", []) if counts.get(c, 0) > 0]
        if len(children) >= 2:
            if best is None or counts[index] <= counts[best]:
                best = index
    return best, total


# -------------------------------------------------------------------- geometry

def connected_components(gltf, buffers):
    """(shell count, vertical span of the widest one, its triangle count).

    Two precautions, without which the measurement lies:
    - vertices are transformed into world space first, otherwise a shell's
      vertical span is compared against a scale that is not its own;
    - weld by position first, because a mesh exported from an OBJ duplicates
      its vertices at UV seams, which would report one shell as many.
    """
    shells = []
    above = parent_matrices(gltf)
    for index, node in enumerate(gltf["nodes"]):
        if node.get("mesh") is None or index not in above:
            continue
        world = multiply(above[index], local_matrix(node))
        for prim in gltf["meshes"][node["mesh"]]["primitives"]:
            if prim.get("mode", 4) != 4 or "indices" not in prim:
                continue
            positions = [transform(world, p) for p
                         in accessor(gltf, buffers, prim["attributes"]["POSITION"])]
            indices = [i[0] for i in accessor(gltf, buffers, prim["indices"])]
            welded = {}
            representative = [0] * len(positions)
            for i, v in enumerate(positions):
                key = (round(v[0], 6), round(v[1], 6), round(v[2], 6))
                representative[i] = welded.setdefault(key, i)
            parent = list(range(len(positions)))

            def find(a):
                while parent[a] != a:
                    parent[a] = parent[parent[a]]
                    a = parent[a]
                return a

            for t in range(0, len(indices) - 2, 3):
                a, b, c = (representative[indices[t + k]] for k in range(3))
                for u, v in ((a, b), (b, c)):
                    ru, rv = find(u), find(v)
                    if ru != rv:
                        parent[ru] = rv
            groups = defaultdict(lambda: [0, 1e30, -1e30])
            for t in range(0, len(indices) - 2, 3):
                g = groups[find(representative[indices[t]])]
                g[0] += 1
                for k in range(3):
                    y = positions[indices[t + k]][1]
                    g[1] = min(g[1], y)
                    g[2] = max(g[2], y)
            shells.extend(groups.values())
    if not shells:
        return 0, 0.0, 0
    lo = min(s[1] for s in shells)
    hi = max(s[2] for s in shells)
    span = hi - lo if hi > lo else 1.0
    widest = max(shells, key=lambda s: s[2] - s[1])
    return len(shells), (widest[2] - widest[1]) / span, widest[0]


def bounds(points):
    lo = [min(p[i] for p in points) for i in range(3)]
    hi = [max(p[i] for p in points) for i in range(3)]
    return lo, hi


def nose_axis(points):
    """(long axis, is the tip on the + side, slenderness).

    The tip is whichever end has the thinner section: the maximum radius in the
    bottom and top 8 % of the long axis are compared.
    """
    lo, hi = bounds(points)
    extents = [hi[i] - lo[i] for i in range(3)]
    axis = extents.index(max(extents))
    others = [i for i in range(3) if i != axis]
    length = extents[axis] or 1.0
    band = 0.08 * length
    low = high = 0.0
    for p in points:
        radius = math.hypot(p[others[0]], p[others[1]])
        if p[axis] <= lo[axis] + band:
            low = max(low, radius)
        elif p[axis] >= hi[axis] - band:
            high = max(high, radius)
    width = max(extents[others[0]], extents[others[1]]) or 1.0
    return axis, high < low, length / width


# ---------------------------------------------------------------------- report

def verdict(flag, label, detail):
    print(f"  [{flag}] {label:<26} {detail}")


def triage(path, piece_mode=False):
    print("=" * 78)
    print(os.path.basename(path))
    print("=" * 78)
    gltf, buffers = load(path)

    counts = subtree_stats(gltf)
    split_parent, total_triangles = find_split_parent(gltf, counts)

    pieces = {}
    scene = gltf["scenes"][gltf.get("scene", 0)]
    whole = Piece("<tout>")
    for root in scene["nodes"]:
        target = whole
        if split_parent is None and len(scene["nodes"]) > 1:
            label = gltf["nodes"][root].get("name") or f"node{root}"
            target = pieces.setdefault(label, Piece(label, root))
        walk(gltf, buffers, root, IDENTITY, target, pieces, split_parent)
    everything = whole.points + [p for piece in pieces.values() for p in piece.points]
    if not everything:
        print("  no triangulated geometry")
        return

    # C1 -- shells
    #
    # Whole-stack criterion only. A detached part SHOULD be one welded shell
    # spanning its own height -- a booster that failed C1 here would be failing
    # for being exactly what it is -- so in piece mode the shell count is
    # reported without a verdict.
    shells, widest_span, widest_tris = connected_components(gltf, buffers)
    if piece_mode:
        verdict(OK, "C1 welded shells",
                f"{shells} shells, the widest spans {widest_span:.0%} of this "
                "part -- not a criterion on a detached part")
    elif widest_span > 0.85:
        verdict(BAD, "C1 welded shells",
                f"{shells} shells, but one spans {widest_span:.0%} of the height "
                f"({widest_tris} tri.) -- MONOLITH, no node split possible")
    elif widest_span > 0.60:
        verdict(WARN, "C1 welded shells",
                f"{shells} shells, the widest spans {widest_span:.0%} of the height")
    else:
        verdict(OK, "C1 welded shells",
                f"{shells} shells, the widest spans {widest_span:.0%} of the height")

    # C2 -- nodes
    #
    # The separation can live in two places, and looking for only one of them
    # yields a false FAIL: either under a single node carrying the whole stack,
    # or directly at the scene-root level, when the export lays several roots
    # side by side. The second case is the common one for Blender exports done
    # per collection.
    groups = sorted((p for p in pieces.values() if p.points),
                    key=lambda p: -p.triangles)
    above = parent_matrices(gltf)

    def show(piece, indent):
        lo, hi = bounds(piece.points)
        share = piece.triangles / total_triangles if total_triangles else 0
        print(f"{' ' * indent}{piece.name:<{34 - indent}} "
              f"y[{lo[1]:+.3f},{hi[1]:+.3f}] {piece.triangles:>7} tri. ({share:5.1%})")

    if not groups:
        verdict(BAD, "C2 node separation",
                "neither the scene roots nor any node separate the stack")
    else:
        where = ("the scene roots" if split_parent is None else
                 "'" + (gltf["nodes"][split_parent].get("name")
                        or f"node{split_parent}") + "'")
        deep = sum(len(groups_under(gltf, buffers, p.node, above[p.node]))
                   for p in groups
                   if p.node is not None and p.triangles > 0.30 * total_triangles)
        verdict(OK if len(groups) + deep >= 3 else WARN, "C2 node separation",
                f"under {where}: {len(groups)} groups carrying geometry")
        for piece in groups:
            show(piece, 8)
            if piece.node is None or piece.triangles <= 0.30 * total_triangles:
                continue
            for child in groups_under(gltf, buffers, piece.node, above[piece.node]):
                show(child, 12)

    lo, hi = bounds(everything)
    if piece_mode:
        verdict(OK, "C3-C5 (piece mode)",
                f"y[{lo[1]:+.4f},{hi[1]:+.4f}] height {hi[1] - lo[1]:.4f} "
                f"x[{lo[0]:+.4f},{hi[0]:+.4f}] z[{lo[2]:+.4f},{hi[2]:+.4f}] "
                "-- compare with the group bounds in the stack")
        verdict(OK if total_triangles <= 120_000 else WARN, "C6 triangle budget",
                f"{total_triangles} triangles")
        _textures(gltf, path)
        print()
        return

    # C3 -- axis
    axis, tip_positive, slenderness = nose_axis(everything)
    axis_name = "XYZ"[axis]
    if axis == 1 and tip_positive:
        verdict(OK, "C3 nose along +Y", f"slenderness {slenderness:.1f}:1")
    else:
        verdict(BAD, "C3 nose along +Y",
                f"long axis = {axis_name}, tip towards "
                f"{'+' if tip_positive else '-'}{axis_name} -- fix at export "
                f"(slenderness {slenderness:.1f}:1)")

    # C4 / C5 -- scale and origin
    height = hi[1] - lo[1]
    verdict(OK if 0.9 <= height <= 1.1 else WARN, "C4 normalised height",
            f"{height:.4f} unit" + ("" if 0.9 <= height <= 1.1 else
                                    f" -- apply a factor of {1 / height:.4f}"))
    verdict(OK if abs(lo[1]) < 0.02 else WARN, "C5 base at origin",
            f"base at y = {lo[1]:+.4f}")

    # C6 -- triangles
    verdict(OK if total_triangles <= 120_000 else WARN, "C6 triangle budget",
            f"{total_triangles} triangles"
            + ("" if total_triangles <= 120_000 else
               " -- heavy: PHY-5 draws up to 3 objects per mission"))

    _textures(gltf, path)
    print()


def _textures(gltf, path):
    base = os.path.dirname(os.path.abspath(path))
    missing = []
    for image in gltf.get("images", []):
        uri = image.get("uri")
        if uri and not uri.startswith("data:"):
            from urllib.parse import unquote
            if not os.path.exists(os.path.join(base, unquote(uri))):
                missing.append(uri)
    verdict(OK if not missing else BAD, "C7 textures",
            f"{len(gltf.get('materials', []))} materials, "
            f"{len(gltf.get('images', []))} images"
            + ("" if not missing else f" -- MISSING: {', '.join(missing)}"))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    arguments = sys.argv[1:]
    piece_mode = "--piece" in arguments
    for argument in [a for a in arguments if a != "--piece"]:
        triage(argument, piece_mode)
