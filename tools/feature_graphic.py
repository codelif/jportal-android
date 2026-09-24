"""play store feature graphic: the launcher frog next to the name, 1024x500.
usage: python tools/feature_graphic.py store/feature-1024x500.png (needs rsvg-convert and pillow)"""
import io, subprocess, sys

from PIL import Image, ImageDraw, ImageFont

from play_icon import bg, root, svg

W, H = 1024, 500
FONT = 'app/src/main/res/font/sans_flex.ttf'

# the viewbox hugs the frog, the adaptive icon's padding would waste the banner
frog = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="22 22 64 64">{svg(root)}</svg>'
png = subprocess.run(['rsvg-convert', '-w', '330', '-h', '330'], input=frog.encode(), capture_output=True, check=True).stdout

img = Image.new('RGB', (W, H), bg)
img.paste(im := Image.open(io.BytesIO(png)).convert('RGBA'), (96, (H - im.height) // 2), im)


def font(size, weight):
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_axes([weight, 100])
    return f


d = ImageDraw.Draw(img)
x = 490
d.text((x, 150), 'JPortal', font=font(128, 800), fill='#EAF7E7')
d.text((x, 305), 'Attendance, marks and exams', font=font(36, 500), fill='#8FDB84')
d.text((x, 350), 'from the JIIT webportal', font=font(36, 500), fill='#8FDB84')
img.save(sys.argv[1], optimize=True)
