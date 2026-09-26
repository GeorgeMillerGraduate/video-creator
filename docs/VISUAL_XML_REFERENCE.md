# Visual XML additions (version 1.0 vocabulary retained)

All time values are scene-local seconds. Shared properties include `x`, `y`, `width`, `height`, `opacity`, `color`, `strokeWidth`, `scaleX`, `scaleY`, `rotation`, `drawProgress`, and the new `glow` (0–1). Matrix and array indices are zero-based; code line numbers are one-based. Unknown attributes and invalid values produce source-located errors.

## Palettes and backgrounds

```xml
<scene id="lesson" duration="12" theme="midnight-blue">
  <background style="midnight-blue" grid="true" curves="true"
              dots="true" vignette="true" intensity="0.5"/>
</scene>
```

Names: `midnight-blue`, `emerald-green`, `deep-purple`; `dark` retains the original palette. Scene `theme` enables a procedural background. `<theme name="emerald-green"/>` at project level selects the default palette; a scene `<background/>` enables its procedural backdrop. The original seven theme colour/font overrides remain supported. Theme semantic methods additionally expose panel, panelBorder, backgroundSecondary, muted, grid, axis, glow and highlight.

A scene has at most one `<background>`. Optional `src="../backgrounds/custom.png"` decodes a PNG/JPEG once, stretches it to the output canvas, then overlays the selected procedural layers. Asset paths are relative to the XML file. Backgrounds are screen-fixed; the camera transforms educational objects. An explicit background style takes palette precedence over scene theme; a procedural backdrop paints over a legacy solid `background` colour.

## Panels, steps and results

```xml
<step id="working" number="1" title="Factorise" x="80" y="300"
      width="650" height="150" padding="24" glow="0.25">
  <equation id="factor" x="20" y="85" value="(x − 2)(x − 3) = 0"/>
</step>
<result id="answer" x="80" y="480" width="650" height="90"
        text="x = 2 or x = 3" style="success" size="36"/>
```

`panel` and `step` are real groups, so all children inherit opacity and transforms. Child coordinates start inside `padding` (24 by default); reserve the first 40 content pixels for a step heading. Children are not clipped. Panel options: `radius` (corner diameter), `fillColor`, `bottomColor`, `borderColor`, `borderWidth`, `shadow`, `padding`. Use eight-digit `#RRGGBBAA` for transparency.

`result` styles: `success`, `answer`, `warning`, `info`. Answers are centered plain Unicode; use an equation inside a panel for structured fractions.

## Matrix events

```xml
<matrix id="A" x="100" y="280" values="1,2;3,4"
        cellWidth="90" cellHeight="70" size="30"/>
<matrix id="C" x="700" y="280" values="7,10;15,22" initiallyVisible="false"/>
<highlight-row target="A" row="0" start="1" duration="3" color="#24D4FF"/>
<highlight-column target="A" column="1" start="1" duration="3" color="#FFD278"/>
<reveal-cell target="C" row="0" column="0" start="3"/>
<highlight-cell target="C" row="0" column="0" start="3" duration="1" color="#5CF2B1"/>
<hide-cell target="C" row="0" column="0" start="6"/>
```

Highlights use `[start,start+duration)`. If several apply to a cell, the last configured active highlight wins. Reveal/hide changes persist until the next event; an event at exactly the same time replaces the earlier visibility assignment. Hidden cells show a subtle placeholder dot. All events must fit the scene duration. Java methods expose the same operations plus `cell()`, `highlightAt()`, `isCellVisible()` and `eventEnd()`.

## Graphs

```xml
<axes id="axes" x="100" y="260" width="650" height="450"
      xmin="0" xmax="3" ymin="-1" ymax="6" step="1"
      gridOpacity="0.8" tickSize="20" axisColor="#D8E9FA">
  <area id="area" expression="x^2" from="0" to="2" color="#259DFE"/>
  <function id="curve" expression="x^2" color="#24D4FF" glow="0.8"/>
  <plot-point id="p" px="2" py="4" label="(2, 4)" guides="true"
              labelDx="-35" labelDy="-24" color="#B7F6FF"/>
</axes>
```

`area` optionally uses `rectangles="12"` for midpoint Riemann rectangles. `drawProgress` reveals the integration interval left to right. Areas use a fixed expression and omit sampled strips at severe discontinuities. They do not calculate an exact integral. Functions retain animated `a`, `b`, `c` expression parameters.

`plot-point` coordinates `px`/`py` are mathematical coordinates; `labelDx`/`labelDy` are pixel offsets. Author-supplied markers can represent roots, intersections or extrema. These three object types must be direct children of `axes`; ordinary axes children still use pixel coordinates. Extrema/intersections are not automatically solved.

## Geometry and arrows

```xml
<triangle id="tri" points="300,100 100,500 650,500"
          vertices="A,B,C" sides="c = 8,a = ?,b"
          sideMask="2" angleMask="4"/>
<curved-arrow id="callout" x="500" y="300" width="120" height="80"
              controlX="90" controlY="-20" openHead="false"
              color="#24D4FF" glow="0.4"/>
<line id="construction" x="100" y="200" width="200" height="100" dashed="true"/>
```

Triangle points are copied and non-collinearity is validated. Angle labels are computed from geometry, not supplied arbitrary values. Side labels correspond to edges 0→1, 1→2, 2→0. Static masks use bits 1, 2 and 4. Animate `highlight` to select edge 1–3, or `angleHighlight` to select angle 1–3; zero means no extra emphasis. Existing polygons, arcs, angles, vectors, points and paths remain available for custom constructions. Shape outlines support `dashed`; glow applies to solid outlines.

## Mathematical text and effects

```xml
<equation id="fraction" x="900" y="400" font="Serif" size="42"
          typeset="true" value="\frac{x^{3}}{3} + \sqrt{x}"/>
<effect target="fraction" name="fadeIn" start="2" duration="0.8"/>
```

Opt-in `typeset` supports Unicode, `\frac{a}{b}`, `\sqrt{x}`, `^{...}` and `_{...}`. It is a compact typesetter, not full LaTeX. Plain equations preserve old behaviour. Structured typewriter reveal fades the entire expression, avoiding broken partial markup. Plain text supports `bold="true"`.

Effects: `fadeIn`, `fadeOut`, `drawOn`, `popIn`, `slideIn`, `glowPulse`. `slideIn` accepts `distance` (40 default). They create standard timeline tracks; overlapping effects on the same property are rejected. The first/last values hold outside normal tracks; fades therefore keep delayed objects hidden until their entrance. Pop-in scales about the object's local origin.

## Code and graphs

```xml
<code id="code" x="80" y="300" title="Search.java" lineNumbers="true" size="24"><![CDATA[
int low = 0;
return low;
]]></code>
<highlight target="code" line="1" start="1" duration="2"/>
<select-code target="code" line="1" from="4" to="7" start="1" duration="2"/>
```

Selections use zero-based UTF-16 columns `[from,to)` within a one-based source line. Line highlights include an execution pointer. Syntax highlighting remains replaceable through the Java API; built-in XML syntax is Java.

Graph `node` accepts `state="default|selected|visited|active"`; nodes and edges accept transient `highlight` tracks and outline `glow`. Arrays retain timed swaps and slot highlights with the shared card surface. Comparisons, pointers and labels can be composed from ordinary arrows/text/groups. The Java `Layout` helper offers configure-time horizontal/vertical arrangement and centering; it does not reflow during animation.
