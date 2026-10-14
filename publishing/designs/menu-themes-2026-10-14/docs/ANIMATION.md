# Deterministic scene animation contract

The JSON files are the authoritative authored animation data. `preview/preview.js` is an executable browser drawing reference. It is not Android implementation code. The implementing agent must port the small sampler/draw contract below without inventing trajectories, colors, particle counts or timings.

## Composition and mapping

Draw in this order: selected clean plate, timeline tracks in array order, existing wordmark and interactive UI. Clip decoration to the scene viewport. All effects are behind controls. Architecture, walls, towers, lamps, pavement perspective and the sun/moon disk stay fixed.

The common authoring plane is **941×1672**. Eleven plates are exactly that size; Solarized Light is 941×1671. Normalize that plate into the common plane during drawing (one-pixel height difference, under 0.06%). Do not crop/resample the source file.

For an actual portrait viewport W×H use uniform cover scale s=max(W/941,H/1672), tx=(W-941s)/2 and ty=(H-1672s)/2. Apply this same transform to plate and all effect coordinates. Preserve the current inset-aware UI geometry independently. Any existing brief launch-intro approach transform, if retained, must transform the entire scene consistently; the settled camera is fixed. The cropped peripheral art is decorative; the cathedral remains centered. Check tall portrait framing.

Each track defines name, kind, color, width, height, period, phase, frames and optional stroke/glow/bevel/sprite/tint/framePeriod/frameEvents. Period and phase are seconds. Coordinates and stroke widths are scene units, rotations are degrees.

## Sampling

A frame is `[u,x,y,scaleX,scaleY,rotation,alpha,atlasFrame]`. Frame times u are ordered from 0 to 1.

For elapsed visible-menu time t, compute p=((t+phase)%period+period)%period. Divide by period to get u. Find adjacent frames a,b with a.u<=u<b.u. Interpolate x,y,scales,rotation and alpha linearly by (u-a.u)/(b.u-a.u). Keep atlasFrame from a, without numeric interpolation. Clamp alpha to [0,1].

For a track with framePeriod, atlasFrame=floor(frac(t/framePeriod)*4). For frameEvents, in array order, replace atlasFrame by event.index while positiveModulo(t,event.period) is in [event.start,event.start+event.duration). Events affect pose only. Otherwise use the sampled base pose. No random numbers or frame-count-based movement are used.

Translate to x,y, rotate, then scale. Rectangles and ellipses are centered at the origin. A line runs from (-width/2,-height/2) to (width/2,height/2); negative line width encodes direction and is intentional. Use the provided stroke when present; otherwise fill. Glow is a radial gradient of the track color at center to transparent at radius max(width,height)/2, clipped to the ellipse. Bevel rectangles receive the preview's 1-unit pale inner outline.

For sprites, read the frame's source rect [left,top,width,height] and anchor [x,y] from menu_sprite_registration.json. Anchors are local to that rect. Draw at destination [-anchorX/rectWidth*track.width,-anchorY/rectHeight*track.height] with track.width×track.height. Use bilinear low filtering consistently with the background. Atlas row positions differ slightly; using the explicit anchors is necessary to keep bat bodies and cat paws registered.

Tint=true means replace RGB with the track color while preserving sprite alpha, then multiply by track alpha. Only cloud/fog wisps use tint. Cat, bat and leaf pixels retain their original colors. Cache tinted variants rather than creating them per frame.

## Authored effects

| Theme | Exact track count | Timing/detail |
|---|---:|---|
| classic | 8 | Floor-row assembly and fade in a 26 s cycle |
| monokai | 13 | 9 rising squares, 24–36 s; 4 dash glints, 8–11 s |
| gruvbox | 8 | 6 leaves, 20–30 s; 2 lamp glows, 8/9 s |
| vscode | 6 | 3 seam glints, 12/15/18 s; 3 outlines, 28/33/38 s |
| dracula | 5 | 3 bats on 14 s paths, 0.36 s wing cycle; fog 28/36 s |
| nord | 38 | 24 far + 12 near flakes; 2 subtle aurora glows |
| solarized_light | 3 | Clouds 66/58 s; shadow 38 s |
| solarized_dark | 8 | Clouds 56/64 s; 5 stars; reflection 12 s |
| github_light | 4 | Clouds 72/64 s; 2 floor inlays 14/18 s |
| tokyo_night | 46 | 42 rain strokes, 2–3.25 s; 3 ripples 1.4/1.56/1.72 s; reflection |
| catppuccin | 7 | Cat breath 4 s; ear at t=3 for 0.35 s every 23 s; tail at t=14 for 0.55 s every 29 s; cloud and 5 stars |
| synthwave_84 | 6 | Lasers 14/18 s; 3 floor scans 5 s; sun halo 8 s |

Total: 152 tracks across twelve themes; maximum 46 active track definitions in one theme. Distant painted cloud banks, settled snow and the Nord aurora shape are part of the clean plate. Separate moving wisps/snow/glow provide motion. Do not attempt to distort or animate architectural pixels.

## Lifecycle and reduced motion

Reuse the existing menu clock/lifecycle owner; one clock per visible menu, never one coroutine per particle. Decode only the selected background and its required atlases, cache while selected, release obsolete theme references. Parse JSON once per theme. Read time in the draw phase so ordinary text/buttons do not recompose every frame.

Advance t only while MENU is visible and lifecycle is RESUMED. Clamp a single frame delta to 100 ms to avoid large jumps after pauses, and reset the frame timestamp on resume. Cancel frame scheduling when leaving MENU or backgrounding; do not merely set alpha to zero. Preserve gameplay state independently of decorative time.

Respect the current system animation setting and LocalMenuSkyAnimations override. If motion is disabled, use staticTime=0 and request no frames. Theme selection resets decorative t to 0. The cat remains present; moving airborne effects may be absent at that static moment. Preserve native required focus/progress feedback without decorative loops.

Verification frames: t=0,3.1,8,14.2,26,60 seconds, plus each track's period boundary. Test finite sampling, atlas bounds, determinism and hidden-screen scheduling. Browser visuals are a reference; actual Android draw alignment and performance must be checked after integration.
