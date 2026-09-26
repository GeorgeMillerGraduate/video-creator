# XML language 1.0

## Document and timing

`<edu-video version="1.0" width="1920" height="1080" fps="60">` contains optional `theme`, zero or more `audio` cues, and one or more `scene` elements. Scene IDs must be unique. Root duration is calculated from scene durations.

`<scene id="name" duration="5" background="#07111F" transition="cut" transitionDuration="0">` contains objects and events. Objects may be nested in groups. Events are direct children of the scene; document ordering of events does not affect their evaluation. IDs are unique within the scene, including group descendants. The reserved ID `camera` always addresses that scene's camera.

All time is seconds. Event times are **scene-local**. Durations must be positive and events must fit the scene. Frame times are `n/fps` for `0 <= n < ceil(totalDuration*fps)`; there is no extra end frame. Tiny floating-point rounding error at an exact frame boundary is tolerated. All numeric values must be finite.

Transitions: `cut`, `fade` (through theme background), `crossfade`, `slide` (leftward). A non-cut transition needs a positive `transitionDuration` no greater than its incoming scene duration. It uses the last sampled image of the outgoing scene. Total video duration remains the sum of scene durations. A transition on the first scene has no effect.

## Shared object properties

All objects require `id`. Common initial attributes include `x`, `y`, `rotation`, `scaleX`, `scaleY`, `opacity`, `color`, `strokeWidth`, `drawProgress`, `z`, and `visible`. Width and height describe bounds for rectangular objects, diameters for ellipses/nodes, endpoints for lines/arrows/vectors, cell size for arrays, and the plotting viewport for axes. They do not resize text or static geometry; use scale for those objects. Irrelevant generic properties have no drawing effect.

Coordinates are pixels, y downward. Rotations are degrees, clockwise in screen space; arcs use Java2D's mathematical angle convention. A group's x/y offset moves its entire local coordinate system. Transforms pivot at an object's local origin. Text x/y is an anchor on its baseline; rectangles/images use top-left; ellipses and nodes use center; lines use their first endpoint. z sorts siblings only, with stable insertion order for ties. Edges default to z=-1 so they appear behind nodes. `visible` is a static boolean; animate opacity to show/hide.

Color syntax is `#RRGGBB` or `#RRGGBBAA` (alpha last). Opacity and reveal/drawProgress are clamped to 0–1 when rendered. Scale may be negative for mirroring. Width/height must initially be nonnegative, except signed line/arrow endpoints. Negative animated shape dimensions collapse to zero. Camera zoom must remain positive.

## Object vocabulary

| Element | Additional attributes / content |
| --- | --- |
| `group` | Contains any visual objects; transform/opacity inherited |
| `text` | `text` attribute or text content; `font`, `size`, `align=left/center/right`, `reveal`, `highlight` |
| `equation` | `value` attribute or content; same font/alignment/reveal/highlight options; Unicode, not TeX |
| `rectangle` | `width`, `height`, `fill=true` |
| `circle`, `ellipse` | Width/height are diameters, `fill=true` |
| `point` | Small filled ellipse, diameter 8 by default |
| `line`, `segment` | Signed `width`,`height` are local endpoint displacement |
| `arrow`, `vector` | Same endpoints, with arrowhead |
| `image` | `src`, relative to XML; defaults to image's native dimensions |
| `axes` | `xmin`, `xmax`, `ymin`, `ymax`, `step=1`, `grid=true`, `xlabel=x`, `ylabel=y`; contains function plots |
| `function` | Must be directly under axes; `expression`, numeric `a=1`,`b=0`,`c=0`; color/stroke/drawProgress |
| `number-line` | `min=-5`, `max=5`, `step=1`, width in pixels |
| `matrix` | `values="1,0;0,1"`; semicolons separate rows; equal row lengths required |
| `polygon` | `points="0,0 100,0 50,80"`, `fill=false` |
| `path` | Same points; `closed=false`, `fill=false`; a polyline, not SVG path data |
| `ray` | `angle=0`; long clipped line from origin in that screen direction |
| `arc` | `radius=60`, `startAngle=0`, `extent=90` |
| `angle` | Same parameters; two rays plus angle-marking arc |
| `array`, `list`, `queue`, `stack` | `values="5,2,8,1,9"`, `vertical=false` (true for stack); fixed initial labels |
| `code` | CDATA/text content; `language=java`, `size=24`; monospaced, numbered lines |
| `variable` | `name`, `value`; changes through `set` events |
| `graph` | Graph-theory container, holds nodes/edges; not a mathematical function graph |
| `tree` | Same explicit layout; validates connected acyclic topology |
| `node` | `label` defaults to ID; x/y, width/height, color |
| `edge` | `from`, `to`, `weight` or `label`, `directed=false`; endpoints must be distinct sibling nodes |

Unknown elements/attributes, malformed literals, duplicate IDs, invalid references and unsupported properties are rejected. Errors include XML source lines. Relational errors detected during scene validation identify the scene and offending object. A DTD or external entity is rejected before parsing.

## Property animation

```xml
<animate target="box" property="x" from="100" to="900"
         start="2" duration="3" easing="easeInOut"/>
<animate target="box" property="color" from="#38BDF8" to="#A78BFA"
         start="0" duration="2"/>
```

Named easings: `linear`, `easeIn`, `easeOut`, `easeInOut`, `smoothStep`, `bounce`, `elastic`. Elastic can overshoot numeric endpoints. Colors interpolate RGBA channels and clamp easing overshoot.

A track holds its first value before it starts and its final value afterward. Thus an opacity animation from 0 to 1 starting at second 2 keeps the object invisible before second 2. Multiple tracks on the same property cannot overlap. The most recently started track applies; gaps hold the preceding track's final value. Different properties and different targets animate concurrently.

```xml
<animation target="ball" property="x">
  <keyframe time="0" value="100"/>
  <keyframe time="2" value="500" easing="easeInOut"/>
  <keyframe time="4" value="900"/>
</animation>
```

At least two strictly increasing times. Easing belongs to the **destination** keyframe. Keyframes also support colors.

```xml
<follow-path target="ball" points="100,200 300,100 500,200"
             start="0" duration="4" easing="easeInOut"/>
```

Movement is interpolated by polyline arc length, creating coordinated x/y tracks. Points are in the object's parent's coordinate system. No simultaneous overlapping x/y tracks may target that same object.

## Text and highlights

```xml
<typewriter target="title" start="0" duration="2" mode="character"/>
<typewriter target="subtitle" start="1" duration="3" mode="word"/>
<highlight target="equation" start="3" duration="1"/>
<highlight target="code" line="3" start="2" duration="1"/>
<highlight target="numbers" index="0" start="2" duration="1" color="#24607A"/>
```

Code lines are 1-based after trimming indentation and outer blank lines; array indices are 0-based. Highlights end at `start + duration`. Text highlights use theme warning color. Array highlighting is attached to the source slot during a swap, and to the current slot afterward. Word reveal normalizes whitespace; character reveal preserves newlines and Unicode code points. Reveal is by code point, not complex grapheme cluster.

## Sorting and variables

```xml
<swap target="numbers" a="0" b="1" start="2" duration="0.55"/>
<set target="status" time="2" value="swap these two values"/>
```

A swap shows values moving around one another and commits the new order at its end. Swaps on one array cannot overlap, even if indices are disjoint. Rendering a later frame reconstructs all completed swaps from the original values; no earlier frames need to have been rendered. Set is a discrete assignment on a variable; it persists until the next assignment. To retain a highlight to the scene end, give it the remaining scene duration.

## Functions

Operators `+ - * / ^`, parentheses, unary signs; power is right associative and higher precedence than unary minus (`-2^2 = -4`). Functions: `sin`, `cos`, `tan`, `sqrt`, `abs`, `exp`, `log` (natural logarithm). Variables: `x`, `a`, `b`, `c`; constants `pi`, `e`. Angles are radians. Use explicit multiplication: `2*x`, not `2x`.

```xml
<axes id="plane" x="100" y="150" width="800" height="450"
      xmin="-5" xmax="5" ymin="-1" ymax="10">
  <function id="curve" expression="a*(x-b)^2+c"
            color="#38BDF8" strokeWidth="4"/>
</axes>
<animate target="curve" property="b" from="0" to="2" start="1" duration="2"/>
```

Grid lines include five minor subdivisions per major step. Function paths are clipped to the plot viewport. Non-finite samples, large jumps and midpoint deviations split the path. Reveal uses accumulated path length across valid subpaths. Sampling is capped at 20,000 segments per curve. Graphs of discontinuous functions are numerical approximations.

## Camera, theme, and future audio

```xml
<animate target="camera" property="zoom" from="1" to="1.5" start="2" duration="2"/>
<animate target="camera" property="x" from="0" to="100" start="2" duration="2"/>
```

Camera properties are x/y pan offsets, zoom and rotation. Zoom/rotation pivot at canvas center. Positive camera x moves the world left. Camera affects every object, including titles; put overlay content in a separate scene if it should remain stationary.

One root-level theme may override `background`, `foreground`, `primary`, `secondary`, `success`, `warning`, and `font`. Individual object color/font attributes take precedence. Panel, muted-text and grid colors are derived from the palette.

`<audio src="voice.wav" start="0" volume="1"/>` is root-level **metadata only**, with video-global start. There is no playback, validation of audio media contents, or muxing in this version.
