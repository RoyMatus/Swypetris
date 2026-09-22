"""Read the transparent logo; emit source rectangles for Canvas without modifying any image.

The letter grid follows the existing artwork, including the offset S and W blocks.
Run with Python, Pillow and NumPy from the repository root. Alpha coverage is checked:
assembling all rectangles reproduces every nontransparent pixel exactly once.
"""
from pathlib import Path
import hashlib
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/src/main/res/drawable-nodpi/swypetris_logo.png'
TARGET = ROOT / 'app/src/main/java/ru/itoltec/swypetris/LogoPieces.kt'
alpha = np.asarray(Image.open(SOURCE).getchannel('A'))
height, width = alpha.shape
assert (width, height) == (1536, 1024), 'Update the letter-grid annotations for a new composition'

# Separate horizontal cathedral bands using their transparent gaps.
ink_rows = (alpha[:726] > 40).any(axis=1).astype(int)
edges = np.flatnonzero(np.diff(np.pad(ink_rows, (1, 1))))
runs = list(zip(edges[::2], edges[1::2]))
stripe_edges = [0] + [(int(a[1]) + int(b[0])) // 2 for a, b in zip(runs, runs[1:])] + [726]
rects = []
owners = []
coverage = np.zeros_like(alpha, dtype=np.uint8)


def add(left, top, right, bottom, stripe, owner=-1):
    """Crop only the rectangle metadata to the local alpha bounds; retain the original pixels."""
    ys, xs = np.nonzero(alpha[top:bottom, left:right])
    if len(xs) == 0:
        return
    x0, y0 = left + int(xs.min()), top + int(ys.min())
    x1, y1 = left + int(xs.max()) + 1, top + int(ys.max()) + 1
    rects.append((x0, y0, x1 - x0, y1 - y0, stripe))
    owners.append(owner)
    coverage[y0:y1, x0:x1] += 1


for top, bottom in zip(stripe_edges, stripe_edges[1:]):
    add(0, top, width, bottom, True)
stripe_count = len(rects)
letter_edges = [0, 237, 425, 582, 732, 880, 1038, 1210, 1290, width]
rows = [726, 787, 823, 859, 895, height]
grids = [
    [[155, 189], [], [163], [], [138, 172]],
    [[288, 310, 348, 371]] * 3 + [[280, 309, 347, 378], [312, 346]],
    [[482, 524]] * 5,
    [[638, 675]] * 5,
    [[786, 823]] * 5,
    [[937, 977]] * 5,
    [[1099, 1139]] * 3 + [[1099, 1133, 1172], [1099, 1147, 1187]],
    [[]] * 5,
    [[1354, 1388], [], [1365], [], [1340, 1374]],
]
for letter, (left, right) in enumerate(zip(letter_edges, letter_edges[1:])):
    for row, (top, bottom) in enumerate(zip(rows, rows[1:])):
        columns = [left] + grids[letter][row] + [right]
        for x0, x1 in zip(columns, columns[1:]):
            add(x0, top, x1, bottom, False, letter)
assert np.all(coverage[alpha > 0] == 1), 'Lost or overlapping artwork pixels'

# Keep tiny antialiased edge fragments attached to their nearest real cube.
# A moving cube can contain several source rectangles, so no source pixels are lost.
main = [i for i, (x, y, w, h, stripe) in enumerate(rects)
        if not stripe and np.count_nonzero(alpha[y:y+h, x:x+w] > 40) >= 300]
groups = []
for index, (x, y, w, h, stripe) in enumerate(rects):
    if stripe:
        groups.append(-1)
        continue
    nearest = min((i for i in main if owners[i] == owners[index]),
                  key=lambda i: (x + w / 2 - rects[i][0] - rects[i][2] / 2) ** 2 +
                                (y + h / 2 - rects[i][1] - rects[i][3] / 2) ** 2)
    groups.append(main.index(nearest))
digest = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
lines = [
    'package ru.itoltec.swypetris', '',
    '/** Область исходного PNG: полоса собора либо отдельный кубик надписи. */',
    'internal data class LogoPiece(val x: Int, val y: Int, val width: Int, val height: Int, val stripe: Boolean, val cube: Int)', '',
    '/** Опорный прямоугольник кубика; к нему прикреплены его мелкие сглаженные края. */',
    'internal data class LogoCube(val x: Int, val y: Int, val width: Int, val height: Int)', '',
    '/** Геометрия прозрачного логотипа; создаётся tools/branding/decompose_logo.py без изменения изображения. */',
    'internal object LogoPieces {',
    f'    const val SOURCE_SHA256 = "{digest}"',
    f'    const val stripeCount = {stripe_count}',
    '    val cubes = listOf(',
]
lines += [f'        LogoCube({rects[i][0]}, {rects[i][1]}, {rects[i][2]}, {rects[i][3]}),' for i in main]
lines += ['    )', '    val parts = listOf(']
lines += [f'        LogoPiece({x}, {y}, {w}, {h}, {str(stripe).lower()}, {groups[i]}),'
          for i, (x, y, w, h, stripe) in enumerate(rects)]
lines += ['    )', '}', '']
TARGET.write_text('\n'.join(lines), encoding='utf-8')
print(f'{stripe_count} stripes, {len(main)} cubes in {len(rects) - stripe_count} regions; all alpha pixels covered exactly once')
