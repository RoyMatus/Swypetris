# Exact soft sounds for the 11 non-Classic themes

This handoff contains the exact final processed WAV files auditioned in the conversation.
Use these files directly; do not regenerate, synthesize replacements, re-record, trim,
normalize, equalize, pitch-shift or re-encode them. Verify their SHA-256 values in
SHA256SUMS.txt / manifest.json after copying them into the application.

**Classic is excluded.** Keep the project's existing drop_soft.wav and clear_soft.wav
and preserve Classic's existing event selection, gain and playback behavior.
Do not use the generated classic_drop.wav or classic_clear.wav from the earlier preview.

Copy only the 22 files under android/app/src/main/res/raw/ into the matching project directory.
Keep unrelated music and sound resources. Do not package this ZIP, previews, README, or
manifest as runtime resources. Total runtime bytes are recorded in manifest.json.

Each stable theme ID maps to <theme_id>_drop.wav for landing and <theme_id>_clear.wav
for destruction/line clear:
monokai, gruvbox, vscode, dracula, nord, solarized_light, solarized_dark,
github_light, tokyo_night, catppuccin, synthwave_84.

The design is intentionally quiet and short. Preserve these recordings and their timbre.
Use existing sound settings and lifecycle behavior; do not stack one sound per cube/shard.
Route by the actually selected palette, including automatic/random palette changes.

These are sound-design assets, not evidence of Android runtime acceptance.
Asset preparation checked the 22 hashes, file format, non-silence, peak levels and fades.
The application integration, repeated listening in real gameplay and required local delivery
gates are future implementation work.
