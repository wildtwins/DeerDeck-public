"""Package genuine PublicGalleryTest captures; requires Python 3 and Pillow."""
import hashlib
import json
import shutil
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = root / 'app/build/public-gallery'
destination = root / 'docs/gallery'
destination.mkdir(parents=True, exist_ok=True)
names = ['01-observe', '02-count', '03-stats', '04-history', '05-profiles']
frames = []
for name in names:
    path = source / f'{name}.png'
    with Image.open(path) as image:
        image.load()
        if image.width < 400 or image.height < 800:
            raise ValueError(f'Unexpected capture dimensions: {name}')
        frames.append(image.convert('RGB'))
    if name != '02-count':
        shutil.copyfile(path, destination / path.name)

# This is a timed sequence of actual captures, not a recorded video or live interaction.
frames[0].save(destination / 'walkthrough.gif', save_all=True,
               append_images=frames[1:], duration=[2500, 2500, 4000, 4000, 4000],
               loop=0, optimize=False)
manifest = {
    'renderer': 'Actual MainActivity under Robolectric 4.14.1, API 35, native graphics',
    'qualifiers': 'en-rUS-w412dp-h892dp-xhdpi',
    'data': 'Fictional test-only records; no coordinates, media, accounts, or backup destinations',
    'demo': '17-second slideshow of five real UI captures; not a device screen recording',
    'files': {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
              for p in sorted(destination.iterdir()) if p.suffix in ('.png', '.gif')},
}
(destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(json.dumps(manifest, indent=2))
