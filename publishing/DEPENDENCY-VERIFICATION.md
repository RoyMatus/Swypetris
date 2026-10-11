# Dependency verification

Gradle verifies resolved dependency artifacts and metadata against SHA-256 values
in `gradle/verification-metadata.xml`. Normal builds use strict verification.
Dependencies, plugin versions and repositories remain declared in the existing
Gradle files; verification does not lock or upgrade the dependency graph.

## Reviewing an intentional dependency change

1. Review the dependency/version change and publisher source first. Keep the
   original metadata and resolved graph available for comparison.
2. On a trusted machine, generate candidate entries using Gradle 9.3.1's
   `--refresh-dependencies --write-verification-metadata sha256` options.
   Refresh is necessary because warmed metadata caches can omit parent POM/BOM
   checksums during bootstrap ([Gradle issue #19228](https://github.com/gradle/gradle/issues/19228)).
   It reuses unchanged artifact files; do not delete caches. `help` covers resolvable
   configurations; execute the affected build/tool tasks as well because some
   dependencies resolve only during task execution. Include Windows and Linux
   build artifacts where applicable, including AAPT2 classifiers.
3. Review every added or changed hash against the publisher's HTTPS repository
   checksum or a separately downloaded artifact. Investigate any mismatch.
   Record the source, comparison and limitations; generated hashes alone are
   not evidence that the bootstrap cache was trustworthy.
4. Remove the metadata-writing option and run the affected checks in strict
   mode, retaining `--refresh-dependencies` for this validation of refreshed
   resolution. Ordinary builds keep normal caching. Preserve required PR CI and
   signed release gates. Review the metadata
   diff together with the dependency change before merging.

Do not automatically regenerate hashes in CI or when verification fails. Do not
add broad trusted-artifact exceptions or switch to lenient/off mode to make a
build pass. A publisher reusing a version with different bytes needs explicit
investigation, not automatic acceptance of an additional hash.

## Trust boundary

SHA-256 detects changes from the reviewed baseline. HTTPS repository checksums
and downloads corroborate cache contents but share the repository's trust;
they do not independently authenticate publisher identity or protect against
an already compromised repository. PGP signature verification is not enabled.

The Gradle distribution has a separate checksum in wrapper properties. Android
SDK downloads, GitHub Actions and Sonar scanner engine/analyzer downloads outside
Gradle's resolver are outside this metadata's coverage and retain their existing
controls. No claim of complete supply-chain verification is made.

Reference: [Gradle 9.3.1 dependency verification](https://docs.gradle.org/9.3.1/userguide/dependency_verification.html).
