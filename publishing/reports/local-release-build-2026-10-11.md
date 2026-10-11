# Local release build measurements

Measured on the same Windows computer, JBR 21.0.11, Gradle 9.3.1, version 1.4.14/code 21. The application Git tree remained `c68ddf4974b01e2040e101a0d1afd395d33924c6`; Gradle properties and wrapper were unchanged. No clean or forced rerun was used.

| Invocation | Total seconds | APP role seconds | UPLOAD role seconds |
| --- | ---: | ---: | ---: |
| Original builder, initial release compilation | 186.777 | Not separately measured | Not separately measured |
| Original builder, warm repeat (`--no-daemon`) | 36.187 | Not separately measured | Not separately measured |
| Updated builder, first successful invocation | 10.070 | 4.030 | 5.482 |
| Updated builder, consecutive warm repeat | 9.038 | 3.508 | 5.179 |

The comparable warm repeat decreased by 27.149 seconds (75.0%; approximately 4.0 times faster). This is one before/after pair, not a statistical estimate or a guarantee for other machines. It measures the combined daemon/cache changes; it does not isolate their individual effects. The initial 186.777-second compilation is not a comparable baseline for a warm build.

Both updated invocations verified APK and AAB signatures and matched the checked-in APP/UPLOAD certificates. APK SHA-256 remained `fc45ecc7e143572a791c65989b53be80bfe665d023c215c6bbf0b0ac3e4e4cef`; AAB remained `09ea54c2b75b611e5ded4f5d99f654c826169dc1c42da4e2f2c2b2aa96c99228`. The JVM task reused 149 passing results (`UP-TO-DATE`); these builds did not execute those tests again. Required release tasks and publication gates remain unchanged.

Two earlier updated invocations failed certificate-output parsing: locally installed build-tools 37 uses `V2 Signer:` whereas 36.1 uses `Signer #1`. The parser now covers both observed formats and tests both. Failure was recorded and did not produce a successful verification summary. A failure fixture also exposed absent signing environment variables being restored as empty strings; the builder now removes originally absent variables and tests restoration.

Raw timing JSON and build logs are retained in ignored `app/build/verification/` in the implementation worktree. Credentials are excluded from summaries. A cold build or changed application still requires compilation and meaningful tests; daemon reuse cannot remove that work.
