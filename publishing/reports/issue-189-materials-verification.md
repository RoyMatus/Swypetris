# Materials, fields and score verification (#189)

All twelve themes use approved material artwork and exact approved background interiors. The existing seven pigments, portrait geometry, UI layout and outline semantics remain owned by the existing palette/game paths. Light-theme score digits are black without a backdrop; selected photo mode retains its existing white score/dark backdrop.

[Visual evidence and asset provenance](../design/block-materials-189/implementation/README.md) pair every theme with its approved hero, colored examples and background, two actual portrait gameplay sizes and every Settings card. The additional `<style>-activity.webp` files capture all twelve actual MainActivities with their production clock and a dense test board. These are agent-reviewed implementation evidence; passing tests alone does not establish aesthetic conformity.

## Development checks

- Assembly and detekt passed. Two new native rendering cases passed in 29.054 seconds and produced 48 portrait/card captures. They check seven distinct pigment samples, black/white score contrast, transparent light-score corners and unchanged game state at 240x580 dp/font 2 and 393x873 dp/font 1.
- The initial focused HUD/photo run took 99.847 seconds: five BackgroundRenderingTest/BorderlessHudTest cases passed; HudLayeringTest had one failure from a one-byte green-channel difference between composited bitmap captures. All logs/XML and emulator diagnostics were preserved. The comparison now allows one RGB quantization step with exact alpha; its drawing-order contract remains enforced. Both HudLayeringTest cases and both material cases then passed together in 70.503 seconds.
- Real Activity measurement passed in 54.176 seconds. The same scenario on the pre-189 APK passed in 54.558 seconds. Each theme warms for 500 ms, observes three seconds of alternating movement with 90 settled cells, and exports native frames and gfxinfo.
- A first measurement inside ComposeTestRule produced zero frames because its virtual clock did not drive recomposition during wall-clock waits. A separate native test initially stalled waiting for idle in the animated Activity; lifecycle, process/gfx and emulator diagnostics were retained before stopping only the task-owned Swypetris process. The final native helper uses the repository's focused-Activity polling pattern without ComposeTestRule or idle synchronization. Failed/invalid measurements are excluded from successful frame evidence.
- The committed preparation script was rerun with Pillow 12.3.0. All twelve decoded background crops remained pixel-identical, all seven tiles per atlas distinct, and the output/source manifest hashes reproducible. The mandatory clean exact-head/current-base PR gate is reported separately in the PR.

## Measured rendering and size evidence

The API35 headless emulator uses SwiftShader on the shared Windows host (four-worker budget, one Gradle build and one emulator). High delays were also observed before the gameplay measurement. The following are observed process-level gfxinfo values, not controlled causal estimates or physical-phone performance claims. Each row has 80-99 rendered frames; no bitmap-upload delay was reported in the measured current windows.

Raw graphics statistics: [current artwork](../design/block-materials-189/implementation/performance/current), [pre-189 APK](../design/block-materials-189/implementation/performance/previous). The previous APK comes from validated #264 evidence for head `5cecd65b470678b4df38c15624ebf21a7a003b00`; the current measurement was taken from the implementation workspace before the final commit. The exact signed release remains a separate verification gate.

| Style | Current p50 / p95 ms | Pre-189 p50 / p95 ms | Current janky frames | Pre-189 janky frames |
| --- | --- | --- | --- | --- |
| Classic | 73 / 89 | 69 / 89 | 62 / 82 | 65 / 81 |
| Monokai | 69 / 89 | 65 / 77 | 72 / 87 | 69 / 94 |
| Gruvbox | 69 / 93 | 69 / 77 | 68 / 84 | 73 / 95 |
| VS Code | 73 / 97 | 65 / 85 | 70 / 80 | 74 / 95 |
| Dracula | 69 / 85 | 65 / 81 | 71 / 87 | 75 / 94 |
| Nord | 61 / 73 | 69 / 77 | 69 / 98 | 73 / 91 |
| Solarized Light | 61 / 77 | 61 / 81 | 69 / 98 | 63 / 94 |
| Solarized Dark | 69 / 85 | 69 / 89 | 72 / 89 | 75 / 91 |
| GitHub Light | 69 / 85 | 61 / 85 | 76 / 91 | 73 / 96 |
| Tokyo Night | 69 / 89 | 61 / 77 | 76 / 92 | 63 / 97 |
| Catppuccin | 61 / 77 | 61 / 73 | 67 / 95 | 71 / 99 |
| SynthWave | 65 / 81 | 69 / 77 | 74 / 96 | 76 / 94 |

Software drawing used the production filled-cell owner with already decoded atlases: ten warmup frames, then twenty frames of 200 cells at each size. Observed means were 22.649-67.421 ms/frame at 74 px and 2.664-13.844 ms/frame at 24 px. [Raw software timings](../design/block-materials-189/implementation/software-drawing-times.csv) include total nanoseconds, frame/cell counts and theme. Software sampling is not GPU presentation. There is no per-frame generation/decode, and no speedup or phone frame-rate claim.

Assets add 10,682,724 compressed bytes (6,832,584 materials; 3,850,140 fields). The measured debug APK grew from 53,137,648 to 63,394,182 bytes, a 10,256,534-byte increase; this is not a signed release size. At ARGB8888, atlas pixel storage is calculated as 1.75 MiB per theme / 21 MiB for the twelve-card picker, before allocator/renderer overhead. Gameplay shares one atlas. These memory amounts are geometry-derived estimates, not measured process heap totals.

The noisy software-emulator results do not isolate a rendering regression or establish physical-device smoothness. Keep the artwork quality; inspect the signed candidate on the physical device as part of release acceptance. No physical-device test is claimed here.
