from pathlib import Path
import colorsys
import hashlib
import json
import re
import sys
from PIL import Image, ImageOps

root = Path(__file__).resolve().parents[4]
if len(sys.argv) != 2:
    raise SystemExit('Usage: prepare-materials.py <directory containing the six approved sheets>')
refs = Path(sys.argv[1])
output = root / 'app/src/main/res/drawable-nodpi'
output.mkdir(parents=True, exist_ok=True)
names = ['classic', 'monokai', 'gruvbox', 'vscode', 'dracula', 'nord', 'solarized_light',
         'solarized_dark', 'github_light', 'tokyo_night', 'catppuccin', 'synthwave_84']
heroes = [(32,97,314,381),(801,95,1083,380),(32,576,313,869),(799,577,1083,870),
          (37,65,393,456),(809,65,1162,456),(37,576,393,968),(806,575,1166,973),
          (24,88,313,458),(793,89,1082,457),(24,592,313,963),(793,594,1083,956)]
backgrounds = [(13,94,250,1489),(266,94,503,1489),(519,94,757,1489),(772,94,1009,1489),
               (24,64,377,992),(403,64,755,992),(781,64,1134,992),(1160,64,1513,992),
               (31,60,412,899),(447,60,829,899),(864,60,1245,899),(1281,60,1659,899)]
text = (root / 'app/src/main/java/ru/itoltec/swypetris/GamePalette.kt').read_text(encoding='utf-8')
base_hues = [0, 0, .08, .55, .83, .53, .12, .51, .35, .58, .07, .55]
manifest = []
for i, name in enumerate(names):
    section = text.split('p("' + name + '"')[1].split('),')[0]
    colors = [int(x,16) for x in re.findall(r'0x([0-9A-Fa-f]+)', section)][-7:]
    material_sheet = refs / f'materials-{i//4+1}.png'
    source = Image.open(material_sheet).convert('RGB').crop(heroes[i])
    side = min(source.size)
    left = (source.width-side)//2
    top = (source.height-side)//2
    source = source.crop((left,top,left+side,top+side)).resize((256,256),Image.Resampling.LANCZOS)
    atlas = Image.new('RGB',(256*7,256))
    for piece, color in enumerate(colors):
        target = tuple(((color >> n) & 255)/255 for n in (16,8,0))
        th, ts, tv = colorsys.rgb_to_hsv(*target)
        pixels = []
        for rgb in source.get_flattened_data():
            h,s,v = colorsys.rgb_to_hsv(*(x/255 for x in rgb))
            if i == 1 and s > .28:  # Keep the approved multicolored mineral chips.
                result = tuple(x/255 for x in rgb)
            elif i == 3 and .055 < h < .20 and s > .35:  # Copper tracks and pads.
                result = tuple(x/255 for x in rgb)
            elif i in (9,11):  # The colored examples use one pigment with local diffraction/reflections.
                delta = (h-base_hues[i]+.5)%1-.5
                result = colorsys.hsv_to_rgb((th+delta*(.12 if i == 11 else .06))%1, s, v)
            else:
                # Preserve photographic value/relief while applying the existing piece pigment.
                light = max(rgb)/255
                if i == 1:
                    light = min(1, light*2.7)
                if i == 7:
                    light = min(1, light*1.8)
                if i in (6,8,10):
                    result = colorsys.hsv_to_rgb(th, ts*(.65+.35*s), min(1,light*tv))
                else:
                    result = colorsys.hsv_to_rgb(th, min(1,ts*(.75+.25*s)), min(1,light*(.45+.55*tv)))
            pixels.append(tuple(round(x*255) for x in result))
        tile = Image.new('RGB',source.size)
        tile.putdata(pixels)
        atlas.paste(tile,(piece*256,0))
    atlas_file = output / f'material_{name}.webp'
    atlas.save(atlas_file,lossless=True,method=6)
    background_sheet = refs / f'backgrounds-{i//4+1}.png'
    background = Image.open(background_sheet).convert('RGB').crop(backgrounds[i])
    background_file = output / f'field_{name}.webp'
    background.save(background_file,lossless=True,method=6)
    assert Image.open(background_file).convert('RGB').tobytes() == background.tobytes()
    assert len({hashlib.sha256(atlas.crop((p*256,0,(p+1)*256,256)).tobytes()).hexdigest() for p in range(7)}) == 7
    manifest.append(dict(style=name,heroCrop=heroes[i],backgroundCrop=backgrounds[i],
                         materialSheetSha256=hashlib.sha256(material_sheet.read_bytes()).hexdigest(),
                         backgroundSheetSha256=hashlib.sha256(background_sheet.read_bytes()).hexdigest(),
                         atlasSha256=hashlib.sha256(atlas_file.read_bytes()).hexdigest(),
                         backgroundSha256=hashlib.sha256(background_file.read_bytes()).hexdigest()))
(root / 'app/build/verification/epic-236/asset-manifest-189.json').write_text(json.dumps(manifest,indent=2))
print(f'Prepared {len(manifest)} lossless seven-color atlases and exact-pixel background interiors.')
