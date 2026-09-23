"""the jpoop frog: launcher foreground, themed mono icon and the splash avd.
needs shapely 2. usage: python tools/frog_icon.py app/src/main/res/drawable"""
import math, sys
from shapely.geometry import Point, LineString, Polygon, MultiPolygon
from shapely.ops import unary_union
from shapely import affinity

E = [(40, 38), (68, 38)]
MOUTH = ((33.5, 58.5), (54, 69.5), (74.5, 58.5))
C = dict(body='#72CC6A', eye='#FFFFFF', pupil='#0E2214', line='#0E2214', tongue='#FF7C93', groove='#D9526D', shine='#FFB8C5')

def circ(cx, cy, r):
    return f'M{cx-r:g},{cy:g} a{r:g},{r:g} 0 1,0 {2*r:g},0 a{r:g},{r:g} 0 1,0 {-2*r:g},0 Z'
def ell(cx, cy, rx, ry):
    return f'M{cx-rx:g},{cy:g} a{rx:g},{ry:g} 0 1,0 {2*rx:g},0 a{rx:g},{ry:g} 0 1,0 {-2*rx:g},0 Z'
def quad(p0, p1, p2, n=64):
    return [((1-t)**2*p0[0]+2*t*(1-t)*p1[0]+t*t*p2[0], (1-t)**2*p0[1]+2*t*(1-t)*p1[1]+t*t*p2[1]) for t in (i/n for i in range(n+1))]
def fmt(p): return f'{p[0]:.2f},{p[1]:.2f}'

mouth_d = f'M{fmt(MOUTH[0])} Q{fmt(MOUTH[1])} {fmt(MOUTH[2])}'
clip_d = f'M0,37 L{fmt(MOUTH[0])} Q{fmt(MOUTH[1])} {fmt(MOUTH[2])} L108,37 V108 H0 Z'
tj_d = 'M60,61 V75 A6.8,6.8 0 0,1 46.4,75'
cavity_d = 'M49,62.6 Q59,66.4 67.5,61.2 Q61,71 49,62.6 Z'

def path(d, fill=None, stroke=None, w=None, alpha=None):
    a = [f'android:pathData="{d}"']
    if fill: a.append(f'android:fillColor="{fill}"')
    if stroke:
        a += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{w:g}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        if alpha: a.append(f'android:strokeAlpha="{alpha:g}"')
    return '        <path ' + '\n            '.join(a) + ' />'

def vector(body, comment):
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            f'    <!-- {comment} -->\n' + body + '\n</vector>\n')

def foreground():
    head = ell(54, 58, 29, 17.5) + ' ' + ' '.join(circ(x, y, 12.5) for x, y in E)
    out = ['    <group>', path(head, fill=C['body'])]
    out += [path(circ(x, y, 8.6), fill=C['eye']) for x, y in E]
    out += [path(circ(x+0.8, y+1.4, 4.9), fill=C['pupil']) for x, y in E]
    out += [path(circ(x+2.6, y-0.6, 1.6), fill=C['eye']) for x, y in E]
    out.append('    </group>')
    out.append('    <!-- the tongue only exists below the lip, so it grows out of the mouth -->')
    out.append('    <group>')
    out.append(f'        <clip-path android:pathData="{clip_d}" />')
    out.append(path(cavity_d, fill=C['line']))
    out.append(path(tj_d, stroke=C['line'], w=10.4))
    out.append(path(tj_d, stroke=C['tongue'], w=7))
    out.append(path('M60,67.5 V74', stroke=C['groove'], w=1.3))
    out.append(path('M62.3,68.5 V72.8', stroke=C['shine'], w=1.3, alpha=0.8))
    out.append('    </group>')
    out.append('    <group>')
    out.append(path(mouth_d, stroke=C['line'], w=3))
    out.append('    </group>')
    return vector('\n'.join(out), 'the jpoop frog, blepping a j')

def mono():
    R = 256
    head = unary_union([affinity.scale(Point(54, 58).buffer(1, R), 29, 17.5)] + [Point(x, y).buffer(12.5, R) for x, y in E])
    stroke = lambda pts, w: LineString(pts).buffer(w/2, R)
    eyes = unary_union([stroke(quad((x-6, y+2.2), (x, y-5.8), (x+6, y+2.2)), 3) for x, y in E])
    clip = Polygon([(0, 37)] + quad(*MOUTH) + [(108, 37), (108, 108), (0, 108)])
    arc = [(53.2 + 6.8*math.cos(t), 75 + 6.8*math.sin(t)) for t in (i/64*math.pi for i in range(65))]
    tj = [(60, 61), (60, 75)] + arc[1:]
    cavity = Polygon(quad((49, 62.6), (59, 66.4), (67.5, 61.2)) + quad((67.5, 61.2), (61, 71), (49, 62.6))[1:])
    outline = unary_union([stroke(tj, 10.4), cavity]).intersection(clip)
    tongue = stroke(tj, 7).intersection(clip)
    g = head.difference(eyes).difference(outline).union(tongue)
    g = g.difference(stroke([(60, 67.5), (60, 74)], 1.3)).difference(stroke(quad(*MOUTH), 3))
    g = g.simplify(0.01)
    polys = list(g.geoms) if isinstance(g, MultiPolygon) else [g]
    d = []
    for p in polys:
        for ring in [p.exterior, *p.interiors]:
            c = list(ring.coords)[:-1]
            d.append('M' + ' L'.join(fmt(q) for q in c) + ' Z')
    body = ('    <path\n        android:fillColor="#FFFFFFFF"\n        android:fillType="evenOdd"\n'
            f'        android:pathData="{" ".join(d)}" />')
    return vector(body, 'themed icon: one alpha layer, tinted light or dark by the system. closed eyes so it never looks back at you inverted')

out = sys.argv[1]
open(f'{out}/ic_launcher_foreground.xml', 'w').write(foreground())
open(f'{out}/ic_launcher_monochrome.xml', 'w').write(mono())

def splash():
    """the launcher frog as an animated vector: blink, blep, bob. the base pose is the final one so older androids show the real icon"""
    def eye(i, x, y):
        return (f'        <group android:name="eye_{i}" android:pivotX="{x}" android:pivotY="{y}">\n'
                + path(circ(x, y, 8.6), fill=C['eye']) + '\n'
                + path(circ(x+0.8, y+1.4, 4.9), fill=C['pupil']) + '\n'
                + path(circ(x+2.6, y-0.6, 1.6), fill=C['eye']) + '\n        </group>')
    head = ell(54, 58, 29, 17.5) + ' ' + ' '.join(circ(x, y, 12.5) for x, y in E)
    body = '\n'.join([
        '    <group android:name="frog" android:pivotX="54" android:pivotY="76">',
        path(head, fill=C['body']),
        eye('l', *E[0]), eye('r', *E[1]),
        '        <group>',
        f'            <clip-path android:pathData="{clip_d}" />',
        '            <group android:name="tongue">',
        path(cavity_d, fill=C['line']).replace('        <path', '                <path'),
        path(tj_d, stroke=C['line'], w=10.4).replace('        <path', '                <path'),
        path(tj_d, stroke=C['tongue'], w=7).replace('        <path', '                <path'),
        path('M60,67.5 V74', stroke=C['groove'], w=1.3).replace('        <path', '                <path'),
        path('M62.3,68.5 V72.8', stroke=C['shine'], w=1.3, alpha=0.8).replace('        <path', '                <path'),
        '            </group>',
        '        </group>',
        path(mouth_d, stroke=C['line'], w=3),
        '    </group>',
    ])
    vec = ('<vector android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
           + body + '\n</vector>')
    def anim(prop, frames):
        # frames: (start_ms, duration_ms, from, to, interpolator)
        items = ''.join(
            f'<objectAnimator android:propertyName="{prop}" android:startOffset="{s}" android:duration="{d}" '
            f'android:valueFrom="{a}" android:valueTo="{b}" android:valueType="floatType" android:interpolator="{i}" />'
            for s, d, a, b, i in frames)
        return f'<set>{items}</set>'
    FOSI = '@android:interpolator/fast_out_slow_in'
    blink = anim('scaleY', [(80, 80, 1, 0.1, FOSI), (160, 90, 0.1, 1, FOSI)])
    targets = [
        ('eye_l', blink), ('eye_r', blink),
        # tucked behind the lip from the first frame, then out with a bit of wobble
        ('tongue', anim('translateY', [(0, 1, -16, -16, FOSI), (250, 320, -16, 0, '@android:anim/overshoot_interpolator')])),
        ('frog', anim('translateY', [(520, 90, 0, -2.5, FOSI), (610, 110, -2.5, 0, FOSI)])),
    ]
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- the splash frog: a blink, then the blep, then a little bob. about 0.7s -->',
           '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt">',
           '    <aapt:attr name="android:drawable">',
           vec.replace('<vector ', '<vector xmlns:android="http://schemas.android.com/apk/res/android" '),
           '    </aapt:attr>']
    for name, a in targets:
        out.append(f'    <target android:name="{name}">\n        <aapt:attr name="android:animation">\n            {a}\n        </aapt:attr>\n    </target>')
    out.append('</animated-vector>')
    return '\n'.join(out) + '\n'

open(f'{out}/avd_frog_splash.xml', 'w').write(splash())
