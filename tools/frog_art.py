"""frog poses for empty states. writes vector drawables and svg previews from the same shapes.
usage: python tools/frog_art.py app/src/main/res/drawable <svg preview dir>"""
import math, sys, os

E = [(40, 38), (68, 38)]
MOUTH = ((33.5, 58.5), (54, 69.5), (74.5, 58.5))
C = dict(body='#72CC6A', belly='#8FDB84', eye='#FFFFFF', pupil='#0E2214', line='#0E2214', tongue='#FF7C93',
         groove='#D9526D', shine='#FFB8C5', ink='#7C8594', cup='#E5484D', cupdark='#B8323A', stick='#C8894B',
         drop='#7CC4FF', c1='#FACC15', c2='#60A5FA', c3='#F472B6', c4='#4ADE80')

def circ(cx, cy, r):
    return f'M{cx-r:g},{cy:g} a{r:g},{r:g} 0 1,0 {2*r:g},0 a{r:g},{r:g} 0 1,0 {-2*r:g},0 Z'
def ell(cx, cy, rx, ry):
    return f'M{cx-rx:g},{cy:g} a{rx:g},{ry:g} 0 1,0 {2*rx:g},0 a{rx:g},{ry:g} 0 1,0 {-2*rx:g},0 Z'
def f(p): return f'{p[0]:.2f},{p[1]:.2f}'
def poly(pts): return 'M' + ' L'.join(f(p) for p in pts) + ' Z'
def rect(cx, cy, w, h, deg):
    a = math.radians(deg)
    pts = [(-w/2, -h/2), (w/2, -h/2), (w/2, h/2), (-w/2, h/2)]
    return poly([(cx + x*math.cos(a) - y*math.sin(a), cy + x*math.sin(a) + y*math.cos(a)) for x, y in pts])

mouth_d = f'M{f(MOUTH[0])} Q{f(MOUTH[1])} {f(MOUTH[2])}'
clip_d = f'M0,37 L{f(MOUTH[0])} Q{f(MOUTH[1])} {f(MOUTH[2])} L108,37 V108 H0 Z'
tj_d = 'M60,61 V75 A6.8,6.8 0 0,1 46.4,75'
cavity_d = 'M49,62.6 Q59,66.4 67.5,61.2 Q61,71 49,62.6 Z'

# a shape: (d, fill, stroke, width, alpha); a clip group: ('clip', clip_d, [shapes])
def F(d, c): return (d, c, None, None, None)
def S(d, c, w, a=None): return (d, None, c, w, a)

def head():
    return [F(ell(54, 58, 29, 17.5) + ' ' + ' '.join(circ(x, y, 12.5) for x, y in E), C['body'])]

def open_eyes(dx=0.8, dy=1.4, r=4.9):
    out = [F(circ(x, y, 8.6), C['eye']) for x, y in E]
    out += [F(circ(x+dx, y+dy, r), C['pupil']) for x, y in E]
    out += [F(circ(x+dx+1.8, y+dy-2, 1.6), C['eye']) for x, y in E]
    return out

def tongue():
    return [('clip', clip_d, [F(cavity_d, C['line']), S(tj_d, C['line'], 10.4), S(tj_d, C['tongue'], 7),
                             S('M60,67.5 V74', C['groove'], 1.3), S('M62.3,68.5 V72.8', C['shine'], 1.3, 0.8)])]

def zee(x, y, s, w):
    return S(f'M{x:g},{y:g} h{s:g} l{-s:g},{s:g} h{s:g}', C['ink'], w)

POSES = {
    # nothing on: eyes shut, a calm mouth, z's drifting up
    'frog_sleep': head()
        + [S(f'M{x-6:g},{y+0.5:g} Q{x:g},{y+5.5:g} {x+6:g},{y+0.5:g}', C['line'], 2.6) for x, y in E]
        + [S('M44,61 Q54,65 64,61', C['line'], 2.6), zee(80, 22, 6, 2.2), zee(89, 11, 8, 2.4)],
    # the portal fell over: a plunger stuck on its head, one sweat drop
    'frog_plunger': [S('M54,6 V30', C['stick'], 4.2)]
        + head()
        + [F('M42,34 Q42,19 54,19 Q66,19 66,34 Z', C['cup']), F('M40,34 H68 Q68,37 65,37 H43 Q40,37 40,34 Z', C['cupdark'])]
        + open_eyes(0, 0.4, 3.6)
        + [S('M45,62 Q49.5,59 54,62 Q58.5,65 63,62', C['line'], 2.6),
           F('M24,40 Q20,47 24,49 Q28,47 24,40 Z', C['drop'])],
    # all clear: happy eyes, the blep, confetti
    'frog_party': head()
        + [S(f'M{x-6:g},{y+2.2:g} Q{x:g},{y-5.8:g} {x+6:g},{y+2.2:g}', C['line'], 2.8) for x, y in E]
        + tongue() + [S(mouth_d, C['line'], 3)]
        + [F(rect(20, 18, 6, 3, 30), C['c1']), F(rect(88, 16, 6, 3, -25), C['c2']), F(circ(30, 6, 2.2), C['c3']),
           F(rect(78, 4, 5, 2.6, 60), C['c4']), F(circ(96, 32, 2), C['c1']), F(rect(10, 34, 5, 2.6, -40), C['c3']),
           F(circ(54, 8, 1.8), C['c2'])],
    # nothing found: looking up and away, lips pursed
    'frog_look': head() + open_eyes(2.4, -2.2, 4.6)
        + [F(ell(54, 63, 3.2, 2.6), C['line']),
           S('M84,14 Q84,8 89,8 Q94,8 94,13 Q94,17 89,19 V22', C['ink'], 2.6), F(circ(89, 27.5, 1.6), C['ink'])],
}

def xml_shape(s, ind):
    d, fill, stroke, w, a = s
    at = [f'android:pathData="{d}"']
    if fill: at.append(f'android:fillColor="{fill}"')
    if stroke:
        at += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{w:g}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        if a: at.append(f'android:strokeAlpha="{a:g}"')
    return ind + '<path ' + ('\n' + ind + '    ').join(at) + ' />'

def svg_shape(s):
    d, fill, stroke, w, a = s
    if fill: return f'<path d="{d}" fill="{fill}"/>'
    return f'<path d="{d}" fill="none" stroke="{stroke}" stroke-width="{w:g}" stroke-linecap="round" stroke-linejoin="round"' + (f' stroke-opacity="{a:g}"' if a else '') + '/>'

def to_xml(shapes, comment):
    body = []
    for s in shapes:
        if s[0] == 'clip':
            body += ['    <group>', f'        <clip-path android:pathData="{s[1]}" />'] + [xml_shape(x, '        ') for x in s[2]] + ['    </group>']
        else:
            body.append(xml_shape(s, '    '))
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp"\n    android:height="92dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="92">\n' + f'    <!-- {comment} -->\n' + '\n'.join(body) + '\n</vector>\n')

def to_svg(shapes, bg):
    out, n = [], 0
    for s in shapes:
        if s[0] == 'clip':
            n += 1
            out.append(f'<clipPath id="c{n}"><path d="{s[1]}"/></clipPath><g clip-path="url(#c{n})">' + ''.join(svg_shape(x) for x in s[2]) + '</g>')
        else:
            out.append(svg_shape(s))
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 92" width="324" height="276"><rect width="108" height="92" fill="{bg}"/>' + ''.join(out) + '</svg>'

COMMENTS = {
    'frog_sleep': 'the frog, asleep. nothing scheduled, nothing due',
    'frog_plunger': 'the frog with a plunger on its head, for when the portal falls over',
    'frog_party': 'the frog blepping with confetti, all caught up',
    'frog_look': 'the frog looking around, nothing found',
}

res, prev = sys.argv[1], sys.argv[2]
os.makedirs(prev, exist_ok=True)
for name, shapes in POSES.items():
    open(f'{res}/{name}.xml', 'w').write(to_xml(shapes, COMMENTS[name]))
    for tag, bg in (('light', '#FFFFFF'), ('dark', '#191C20')):
        open(f'{prev}/{name}_{tag}.svg', 'w').write(to_svg(shapes, bg))
