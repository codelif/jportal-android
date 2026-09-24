"""play store icon: the adaptive launcher icon flattened to a 512px square, play masks it itself.
usage: python tools/play_icon.py store/icon-512.png (needs rsvg-convert)"""
import re, subprocess, sys
import xml.etree.ElementTree as ET

A = '{http://schemas.android.com/apk/res/android}'
res = 'app/src/main/res'
bg = re.search(r'name="launcher_background">(#[0-9A-Fa-f]+)<', open(f'{res}/values/colors.xml').read()).group(1)
root = ET.parse(f'{res}/drawable/ic_launcher_foreground.xml').getroot()

def svg(el):
    out = []
    for c in el:
        tag = c.tag
        if tag == 'group':
            out.append('<g>' + svg(c) + '</g>')
        elif tag == 'clip-path':
            # a clip applies to the rest of its group
            cid = f'c{id(c)}'
            rest = list(el)[list(el).index(c) + 1:]
            return ''.join(out) + f'<clipPath id="{cid}"><path d="{c.get(A + "pathData")}"/></clipPath><g clip-path="url(#{cid})">' + svg_list(rest) + '</g>'
        elif tag == 'path':
            a = [f'd="{c.get(A + "pathData")}"', f'fill="{c.get(A + "fillColor", "none")}"']
            if c.get(A + 'strokeColor'):
                a += [f'stroke="{c.get(A + "strokeColor")}"', f'stroke-width="{c.get(A + "strokeWidth")}"',
                      f'stroke-linecap="{c.get(A + "strokeLineCap", "butt")}"', f'stroke-linejoin="{c.get(A + "strokeLineJoin", "miter")}"']
                if c.get(A + 'strokeAlpha'): a.append(f'stroke-opacity="{c.get(A + "strokeAlpha")}"')
            out.append('<path ' + ' '.join(a) + '/>')
    return ''.join(out)

def svg_list(items):
    holder = ET.Element('g')
    holder.extend(items)
    return svg(holder)

doc = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512"><rect width="108" height="108" fill="{bg}"/>{svg(root)}</svg>'
subprocess.run(['rsvg-convert', '-w', '512', '-h', '512', '-o', sys.argv[1]], input=doc.encode(), check=True)
