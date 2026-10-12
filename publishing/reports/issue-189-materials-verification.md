# Materials, fields and score verification (#189)

All twelve themes use approved material artwork and exact approved background interiors. The existing seven pigments, portrait geometry, UI layout and outline semantics remain owned by the existing palette/game paths. Light-theme score digits are black without a backdrop; selected photo mode retains its existing white score/dark backdrop.

[Visual evidence and asset provenance](../design/block-materials-189/implementation/README.md) pair every theme with its approved hero, colored examples and background, two actual portrait gameplay sizes and every Settings card. The additional `<style>-activity.webp` files capture all twelve actual MainActivities with their production clock and a dense test board. These are agent-reviewed implementation evidence; passing tests alone does not establish aesthetic conformity.

## Development checks

- Assembly and detekt passed. Two new native rendering cases passed in 29.054 seconds and produced 48 portrait/card captures. They check seven distinct pigment samples, black/white score contrast, transparent light-score corners and unchanged game state at 240x580 dp/font 2 and 393x873 dp/font 1.
- The initial focused HUD/photo run took 99.847 seconds: five BackgroundRenderingTest/BorderlessHudTest cases passed; HudLayeringTest had one failure from a one-byte green-channel difference between composited bitmap captures. All logs/XML and emulator diagnostics were preserved. The comparison now allows one RGB quantization step with exact alpha; its drawing-order contract remains enforced. Both HudLayeringTest cases and both material cases then passed together in 70.503 seconds.
- Verified gameplay Activity measurement passed in 55.926 seconds. The same scenario on the pre-189 APK passed in 51.028 seconds. Each theme warms for 500 ms, observes three seconds of alternating movement with 90 settled cells, and exports native frames and gfxinfo. The fixture stays below the round-victory threshold, and the helper asserts PLAYING/no pending victory after observation.
- A first measurement inside ComposeTestRule produced zero frames because its virtual clock did not drive recomposition during wall-clock waits. A separate native test initially stalled waiting for idle in the animated Activity; lifecycle, process/gfx and emulator diagnostics were retained before stopping only the task-owned Swypetris process. The final native helper uses the repository's focused-Activity polling pattern without ComposeTestRule or idle synchronization. Image review also caught that the first native fixture's score triggered Victory, invalidating its gameplay measurements (54.176/54.558 s). Those runs/screenshots were retained in ignored diagnostics and excluded from this report. Only the corrected, scene-checked gameplay measurements appear below.
- The committed preparation script was rerun with Pillow 12.3.0. All twelve decoded background crops remained pixel-identical, all seven tiles per atlas distinct, and the output/source manifest hashes reproducible. The mandatory clean exact-head/current-base PR gate is reported separately in the PR.

## Measured rendering and size evidence

The API35 headless emulator uses SwiftShader on the shared Windows host (four-worker budget, one Gradle build and one emulator). The following are observed process-level gfxinfo values, not controlled causal estimates or physical-phone performance claims. Current windows rendered 74-85 frames; pre-189 windows rendered 19-70. Movement input is dispatched for a fixed wall-clock interval, so completed input/frame counts vary with responsiveness. VS Code and SynthWave each reported one slow bitmap upload in the current windows; the other current windows reported zero.

Raw graphics statistics: [current artwork](../design/block-materials-189/implementation/performance/current), [pre-189 APK](../design/block-materials-189/implementation/performance/previous). The previous APK comes from validated #264 evidence for head `5cecd65b470678b4df38c15624ebf21a7a003b00`; the current measurement was taken from the implementation workspace before the final commit. The exact signed release remains a separate verification gate.

| Style | Current p50 / p95 ms | Pre-189 p50 / p95 ms | Current janky frames | Pre-189 janky frames |
| --- | --- | --- | --- | --- |
| catppuccin | 38 / 65 | 101 / 150 | 73 / 85 | 51 / 54 |
| classic | 48 / 85 | 101 / 129 | 64 / 74 | 56 / 57 |
| dracula | 36 / 53 | 133 / 150 | 78 / 85 | 42 / 42 |
| github_light | 48 / 57 | 85 / 150 | 71 / 83 | 54 / 56 |
| gruvbox | 53 / 89 | 150 / 350 | 65 / 74 | 30 / 30 |
| monokai | 48 / 69 | 61 / 89 | 69 / 80 | 61 / 70 |
| nord | 48 / 65 | 97 / 150 | 72 / 82 | 55 / 55 |
| solarized_dark | 48 / 57 | 97 / 133 | 73 / 82 | 53 / 55 |
| solarized_light | 40 / 53 | 129 / 150 | 65 / 84 | 47 / 47 |
| synthwave_84 | 48 / 69 | 101 / 150 | 69 / 77 | 50 / 50 |
| tokyo_night | 36 / 53 | 250 / 600 | 71 / 83 | 19 / 19 |
| vscode | 48 / 57 | 93 / 150 | 70 / 82 | 54 / 58 |

Software drawing used the production filled-cell owner with already decoded atlases: ten warmup frames, then twenty frames of 200 cells at each size. Observed means were 22.649-67.421 ms/frame at 74 px and 2.664-13.844 ms/frame at 24 px. [Raw software timings](../design/block-materials-189/implementation/software-drawing-times.csv) include total nanoseconds, frame/cell counts and theme. Software sampling is not GPU presentation. There is no per-frame generation/decode, and no speedup or phone frame-rate claim.

Assets add 10,682,724 compressed bytes (6,832,584 materials; 3,850,140 fields). The measured debug APK grew from 53,137,648 to 63,394,182 bytes, a 10,256,534-byte increase; this is not a signed release size. At ARGB8888, atlas pixel storage is calculated as 1.75 MiB per theme / 21 MiB for the twelve-card picker, before allocator/renderer overhead. Gameplay shares one atlas. These memory amounts are geometry-derived estimates, not measured process heap totals.

Current observed p50/p95 durations are below the corresponding pre-189 values in this sequential run. Shared-host load, caching and differing completed input counts prevent attributing a speedup to the change or establishing physical-device smoothness. Keep the artwork quality; inspect the signed candidate on the physical device as part of release acceptance. No physical-device rendering test is claimed here.
