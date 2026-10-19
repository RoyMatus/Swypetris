# Issue #320: provisional effect calibration

**Status: candidate; listening acceptance is not performed. Do not treat this report as release approval.**

The original 24 theme recordings and eight music files are unchanged. The existing ThemeSounds pair owns separate DROP/CLEAR playback gains; AndroidGameFeedback passes the selected gain to both SoundPool channels. Music stays at its existing 0.4 gain. Stop-before-next, event timing, sample loading, settings and vibration behavior are unchanged.

The largest measured outlier was Classic CLEAR: true peak -9.8 dBFS and maximum momentary loudness -17.9 LUFS at the previous 0.55 gain. This candidate attenuates outliers without boosting quieter effects. It does **not** make all recordings equal in measured energy or establish equal perceived loudness.

## Measurement method and limits

Read-only FFmpeg 8.1.2 analysis; binary SHA-256 `1326dde4c84ff1f96fe6b8916c5bed29e163e9b5dccf995f6f3db069d143ec5e`. Official filter contract: [FFmpeg ebur128](https://ffmpeg.org/ffmpeg-all.html), [volume](https://ffmpeg.org/ffmpeg-all.html). One local decoder process at a time, one decoder thread. No native-output capture, microphone recording, system volume change, asset processing or dependency installation.

Mono effects are duplicated into stereo, multiplied by the actual pair gain and zero-padded to one second for the 400-ms momentary meter. Music is decoded in its original channel layout at 0.4. Reported true peak and momentary values have the scanner's 0.1-unit precision. Short impulses, spectral differences and hardware acoustics limit comparisons; these values are not a listening assessment.

For active music, exclude the first/last two seconds and moments more than 10 LU below that track's integrated level. The matrix compares each effect's maximum 400-ms loudness to each track's selected active median; it does not assert that an effect is quieter than every note, fade or silence.

Provisional attenuation ceilings: existing effect median true peak -17.85 dBFS; quietest selected music median minus an initial 3-LU tuning margin gives -28.1 LUFS. The margin is an engineering choice for this candidate, not a universal perceptual target. Gains are rounded down to four decimals. A real audition may require different values.

Observed offline measurement durations: before 29.561 s; after 28.109 s. No causal performance comparison or active-work duration is inferred. Raw commands, full input hashes and scanner logs remain in ignored issue-320/before and after evidence.

## Per-sample candidate

| Sample | Gain (was 0.55) | Peak before / after, dBFS | Max M before / after, LUFS |
| --- | ---: | ---: | ---: |
| catppuccin_clear | 0.4901 | -17.2 / -18.2 | -27.1 / -28.1 |
| catppuccin_drop | 0.5500 | -20.7 / -20.7 | -34.2 / -34.2 |
| clear_soft | 0.1699 | -9.8 / -20.1 | -17.9 / -28.1 |
| dracula_clear | 0.5500 | -19.7 / -19.7 | -35.7 / -35.7 |
| dracula_drop | 0.5500 | -21.1 / -21.1 | -40.0 / -40.0 |
| drop_soft | 0.4294 | -15.7 / -17.8 | -29.0 / -31.2 |
| github_light_clear | 0.5500 | -18.8 / -18.8 | -39.6 / -39.6 |
| github_light_drop | 0.5500 | -21.1 / -21.1 | -36.9 / -36.9 |
| gruvbox_clear | 0.5103 | -17.2 / -17.9 | -37.7 / -38.3 |
| gruvbox_drop | 0.5500 | -18.8 / -18.8 | -37.9 / -37.9 |
| monokai_clear | 0.5500 | -18.0 / -18.0 | -37.5 / -37.5 |
| monokai_drop | 0.5103 | -17.2 / -17.8 | -39.7 / -40.3 |
| nord_clear | 0.5500 | -17.9 / -17.9 | -36.3 / -36.3 |
| nord_drop | 0.5222 | -17.4 / -17.8 | -30.5 / -30.9 |
| solarized_dark_clear | 0.5468 | -17.8 / -17.8 | -38.0 / -38.0 |
| solarized_dark_drop | 0.5500 | -21.2 / -21.2 | -40.2 / -40.2 |
| solarized_light_clear | 0.4901 | -17.2 / -18.2 | -27.1 / -28.1 |
| solarized_light_drop | 0.5103 | -17.2 / -17.8 | -36.0 / -36.6 |
| synthwave_84_clear | 0.4220 | -17.5 / -19.8 | -25.8 / -28.1 |
| synthwave_84_drop | 0.4681 | -18.1 / -19.5 | -26.7 / -28.1 |
| tokyo_night_clear | 0.5103 | -17.2 / -17.8 | -29.5 / -30.1 |
| tokyo_night_drop | 0.5500 | -22.3 / -22.3 | -33.5 / -33.5 |
| vscode_clear | 0.5313 | -21.2 / -21.5 | -27.8 / -28.1 |
| vscode_drop | 0.5222 | -17.4 / -17.9 | -39.2 / -39.6 |

## Unchanged music and 24-by-8 comparison

| Track | Integrated, LUFS | Active M p10 / median / p90, LUFS | True peak, dBFS |
| --- | ---: | ---: | ---: |
| barynya | -23.4 | -24.8 / -23.5 / -21.9 | -13.7 |
| kalinka | -23.8 | -25.3 / -23.8 / -22.6 | -12.9 |
| kamarinskaya | -23.5 | -25.5 / -23.4 / -22.2 | -12.6 |
| korobeiniki | -23.7 | -25.8 / -23.4 / -22.5 | -13.1 |
| sugar_plum | -24.6 | -28.3 / -25.1 / -22.4 | -10.2 |
| svetit_mesyat | -23.8 | -25.7 / -23.8 / -22.5 | -12.4 |
| trepak | -23.3 | -25.1 / -23.5 / -21.8 | -12.3 |
| vo_sadu | -23.7 | -25.3 / -23.6 / -22.4 | -13.5 |

Numbers below are effect max M minus music active median, in LU (negative means lower in this digital model). All 192 subjective comparisons remain **not performed**.

| Effect | barynya | kalinka | kamarinskaya | korobeiniki | sugar_plum | svetit_mesyat | trepak | vo_sadu |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| catppuccin_clear | -4.6 | -4.3 | -4.7 | -4.7 | -3.0 | -4.3 | -4.6 | -4.5 |
| catppuccin_drop | -10.7 | -10.4 | -10.8 | -10.8 | -9.1 | -10.4 | -10.7 | -10.6 |
| clear_soft | -4.6 | -4.3 | -4.7 | -4.7 | -3.0 | -4.3 | -4.6 | -4.5 |
| dracula_clear | -12.2 | -11.9 | -12.3 | -12.3 | -10.6 | -11.9 | -12.2 | -12.1 |
| dracula_drop | -16.5 | -16.2 | -16.6 | -16.6 | -14.9 | -16.2 | -16.5 | -16.4 |
| drop_soft | -7.7 | -7.4 | -7.8 | -7.8 | -6.1 | -7.4 | -7.7 | -7.6 |
| github_light_clear | -16.1 | -15.8 | -16.2 | -16.2 | -14.5 | -15.8 | -16.1 | -16.0 |
| github_light_drop | -13.4 | -13.1 | -13.5 | -13.5 | -11.8 | -13.1 | -13.4 | -13.3 |
| gruvbox_clear | -14.8 | -14.5 | -14.9 | -14.9 | -13.2 | -14.5 | -14.8 | -14.7 |
| gruvbox_drop | -14.4 | -14.1 | -14.5 | -14.5 | -12.8 | -14.1 | -14.4 | -14.3 |
| monokai_clear | -14.0 | -13.7 | -14.1 | -14.1 | -12.4 | -13.7 | -14.0 | -13.9 |
| monokai_drop | -16.8 | -16.5 | -16.9 | -16.9 | -15.2 | -16.5 | -16.8 | -16.7 |
| nord_clear | -12.8 | -12.5 | -12.9 | -12.9 | -11.2 | -12.5 | -12.8 | -12.7 |
| nord_drop | -7.4 | -7.1 | -7.5 | -7.5 | -5.8 | -7.1 | -7.4 | -7.3 |
| solarized_dark_clear | -14.5 | -14.2 | -14.6 | -14.6 | -12.9 | -14.2 | -14.5 | -14.4 |
| solarized_dark_drop | -16.7 | -16.4 | -16.8 | -16.8 | -15.1 | -16.4 | -16.7 | -16.6 |
| solarized_light_clear | -4.6 | -4.3 | -4.7 | -4.7 | -3.0 | -4.3 | -4.6 | -4.5 |
| solarized_light_drop | -13.1 | -12.8 | -13.2 | -13.2 | -11.5 | -12.8 | -13.1 | -13.0 |
| synthwave_84_clear | -4.6 | -4.3 | -4.7 | -4.7 | -3.0 | -4.3 | -4.6 | -4.5 |
| synthwave_84_drop | -4.6 | -4.3 | -4.7 | -4.7 | -3.0 | -4.3 | -4.6 | -4.5 |
| tokyo_night_clear | -6.6 | -6.3 | -6.7 | -6.7 | -5.0 | -6.3 | -6.6 | -6.5 |
| tokyo_night_drop | -10.0 | -9.7 | -10.1 | -10.1 | -8.4 | -9.7 | -10.0 | -9.9 |
| vscode_clear | -4.6 | -4.3 | -4.7 | -4.7 | -3.0 | -4.3 | -4.6 | -4.5 |
| vscode_drop | -16.1 | -15.8 | -16.2 | -16.2 | -14.5 | -15.8 | -16.1 | -16.0 |

The sum-of-separate-true-peaks upper bound is at most -7.2 dBFS for these files/gains, with one effect stream. This supports digital mixing headroom; it does not prove device DSP/speaker behavior or repeated-event comfort.

## Acceptance still required

No listener, documented comfortable Pixel 7 speaker/headphone level or actual subjective result is available. The phone is reachable but its keyguard is locked; installation/player success cannot replace hearing. The existing emulator capture endpoint was previously denied, and its allowlist was not changed. No new recording tooling was built.

Required before merge/release: actual Pixel 7 speaker and headphone audition across all effects/tracks, repeated drops and multi-line clears, with music on/off at a recorded fixed comfortable level. Confirm recognizability, balance and fatigue; revise the provisional gains if needed. Keep these results separate from automated checks. Final exact-head and signed/public release gates remain required.

## Automated development checks

Five JVM gain-routing/safety tests observed three expected behavior assertion failures before implementation, then passed; the final fixture uses real raw-resource IDs and passed in 25.140 s. Initial lint failed six ResourceType errors in that fixture; the IDs were corrected without changing assertions. Final strict debug lint and detekt passed (88 s Gradle). Eighteen fresh native audio/settings/vibration cases passed in 169.502 s, including all 24 original recording hashes and SoundPool load/cache/stop/release behavior. These are objective checks, not listening evidence. Final exact-head/full and release evidence are recorded separately in the PR; the mandatory subjective acceptance above remains unknown.
