---
version: 1
slug: "app-src-com-glyphcountdown-app-widgetrenderer-java"
primary_target: "app/src/com/glyphcountdown/app/WidgetRenderer.java"
related_targets: ["app/res/layout/widget_2x2.xml","app/res/layout/widget_2x1.xml","app/res/layout/widget_1x1.xml"]
---

# Home-screen widgets (2×2, 2×1, 1×1)

Scope: Android app widgets drawn as bitmaps by WidgetRenderer. Visitor mode: Operate (read the time at a glance, tap to start/pause or open the app). Audience: Nothing Phone owners. Constraints: taps only, no swipes; Widget colour picks a hue from the wallpaper; light and dark themes; translucent option.

## Direction contract

THESIS: The time is an engineering drawing, not a block: a hairline wireframe solid whose depth is the time left, so the digits flatten into the page as the timer runs. Refuses the category's filled card, big number and progress ring.

OWN-WORLD: Isometric drafting pad: near-white pad (#EEF2F3) ruled with a non-repro-blue isometric grid (#A9D3E0), graphite seven-segment lettering (#1F2427), back edges and connectors in pale blue (#7FB3C4), one drafting red (#D64B2A) for the dimension. Dark theme: graphite pad, deep blue grid, white lettering. Widget colour retints grid, back edges and dimension from the wallpaper hue.

STORY: The visitor reads 3:20 instantly from the heavy front edge, sees how deep the time still is, and taps the time to start or pause.

FIRST VIEWPORT: 2×2: extruded time across the top half with a red Z dimension on its depth; a ruled two-row table below (LEFT time, SET duration). 2×1: extruded time left, SET right. 1×1: one extruded number (minutes, seconds in the last minute).

FORM: Isometric Pad, grounded candidate from the safer re-roll (round 4), picked over Blueprint, Drawing Sheet and Schematic; seed: seed:7d51163035479110.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
