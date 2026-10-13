# Theme sound integration — #270

The feedback owner now selects DROP/CLEAR by the current stable palette ID. Manual selection, random new-round selection and restored preferences update it before the next event. Unknown IDs use Classic. One SoundPool caches each visited pair; events neither load samples nor queue a replay while a sample is pending. Changing the pair stops the old stream. Existing stop-before-next behavior, 0.55 playback gain, sound/vibration separation and no CLEAR replay on resume remain in place.

Exactly 22 approved WAVs were copied from pinned source `77b25f2cd10d6b16ab8b1b8bab23df3d54c682b8`; ZIP SHA-256 `0b93962d71fac8f13a0e93d1a9283de465af6cba61ad837e7e76c09106447114`. All imported hashes match. The original Classic DROP/CLEAR hashes remain `bb8ee4affd0572ca06005a2eac67fd8e435f4f589b67445eea614e9186820cd8` and `683e26791f4cc073755a8e7bc869cf33f5377dab111d002c619fcbe2d7877280`. No clip was processed. The attachment-storage branch is retained.

## Risk-based verification

- Packaged-resource assertions validate all twelve palette names and 24 approved WAV hashes, including the fallback. The compact checksum fixture exists only in the test APK.
- Model tests cover all manual themes, seeded random selection, persisted/resumed selection and command/timed DROP. Existing feedback tests cover single CLEAR precedence, resume without sound replay, settings and independent vibration stops.
- Native SoundPool tests load every pair, require nonzero playback stream IDs, check stop-before-next and mute, reuse the same pool/sample IDs, reject pending-load replay and verify release cleanup/idempotence.
- The first development build failed because a test recorder's property setter collided with its interface method. After correction, nine selected Android cases passed in 88.417 s including build/runner overhead. Debug test assembly, strict lint and detekt subsequently passed in 134 s. Logs are retained under ignored verification output.
- Final exact-head PR evidence and merge status belong in the PR and consolidated completion comment. No extra release is requested by this ticket.

## Packaging measurement

The retained #269 debug baseline has 23,248,459 raw-resource bytes. The only runtime resource additions are 22 WAVs totaling 479,004 bytes; all existing raw-resource hashes are unchanged. The ZIP, full source manifest, documentation, previews and source MP3s are absent from the application APK.

The retained baseline debug APK is 64,842,596 bytes; the development APK is 64,847,909 bytes (archive delta +5,313). Its added ZIP entries contain 479,004 bytes, with changed compressed DEX/resource-table entries also present. The whole archive delta is not the audio payload delta: these are retained incremental-build APK layouts, not a controlled signing/layout-normalized comparison. No signed release size or download-size reduction is inferred. Exact per-entry sizes and SHA-256 values are retained in `issue-270-packaging.json`.

## Listening limits

The opt-in native workload passed in 169.029 s: 144 model-driven events (three DROP and three CLEAR events for each of twelve themes, both without music and with the production Korobeiniki player). It uses deterministic board fixtures and real wall-clock spacing, with native feedback and the existing music owner. Event timestamps are retained in `issue-270-runtime-markers.csv`. This exercises actual playback requests rather than an external concatenation of source clips; it is not human free-play or a subjective audition. The first direct invocation found the target app absent after the Gradle device suite; installing the existing app APK resolved setup without changing source.

Native playback success and exact source bytes are objective checks, not proof of perceived loudness, transient softness or repeated-event fatigue. An attempted recording of only the owned emulator's output was rejected by its gRPC endpoint allowlist; the allowlist was not changed and no host microphone was captured. No subjective listening pass or device-speaker equivalence is claimed. The approved recordings remain unchanged; any subsequently heard balance concern needs a separate owner decision.
