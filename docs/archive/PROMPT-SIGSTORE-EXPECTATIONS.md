# Sigstore release expectations for vql-engine (from the register consumer)

Audience: anyone (human or agent) changing this repository's release
pipeline. The register repository verifies every first-party Maven artifact
it consumes against the Sigstore bundle published next to it on Maven
Central, using a pinned certificate-identity policy
(register: `docs/dev/PLAN-SIGSTORE-VERIFICATION.md`). This repository is a
trust root of that chain: what its CI signs is what register accepts.

## Current state (verified against repo1.maven.org, 2026-08-11)

Coverage is complete. For both consumed versions (0.10.2, 0.11.0) and both
cross-builds (`vql-engine_3`, `vql-engine_sjs1_3`), every file — main jar,
sources jar, javadoc jar, pom — carries:

- a `.sigstore.json` bundle (spec format v0.3,
  `application/vnd.dev.sigstore.bundle.v0.3+json`), and
- a `.asc` GPG signature.

Signing happens in `ci-build.yml` on push to `main` (`cosign sign-blob
--bundle` over every jar and pom in `target/bundle`); `release.yml` verifies
those bundles before uploading to the Central Portal. The Fulcio certificate
identity inside the published bundles is:

```
https://github.com/risquanter/vql-engine/.github/workflows/ci-build.yml@refs/heads/main
```

issued by `https://token.actions.githubusercontent.com`.

## Expectations (the contract with register)

1. **Non-regression on coverage.** Every release must keep publishing a
   spec-format `.sigstore.json` bundle and a `.asc` signature for every
   artifact file of every classifier and every cross-build, plus the pom.
   A release missing any bundle blocks register from bumping its pin to
   that version (register verifies at pin-bump time and in CI, fail
   closed).

2. **Identity stability.** Register pins the exact identity string above.
   The following changes break register's verification and must be
   coordinated with a register policy update BEFORE they land here:
   - renaming or moving `.github/workflows/ci-build.yml`,
   - changing the branch that triggers the signing build,
   - moving the `cosign sign-blob` step into a different workflow.

3. **Bundle format stability.** Keep emitting the Sigstore bundle spec
   format (currently v0.3). Do not switch to cosign's legacy
   `.bundle` JSON for the files uploaded to Central.

4. **Pin GitHub Actions by commit SHA.** Every action in every workflow is
   pinned to a full commit SHA with the moving tag kept as a trailing comment.
   A tag is mutable; a compromised action in this repository would reach every
   downstream consumer. Dependabot (`.github/dependabot.yml`, `github-actions`
   ecosystem) opens a review-and-approve pull request when a new version ships,
   so the pins stay current under review.

5. **GPG signature: presence-checked, not verified.** `release.yml` confirms
   a `.asc` exists for each artifact but deliberately does not run
   `gpg --verify`. Maven Central's keyserver validation is the effective GPG
   control for the binary; the Sigstore identity check is the
   binary-authenticity control register relies on.

## How register verifies (for reference)

```
cosign verify-blob \
  --bundle <artifact>.sigstore.json \
  --certificate-identity "https://github.com/risquanter/vql-engine/.github/workflows/ci-build.yml@refs/heads/main" \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  <artifact>
```

After any workflow change, confirm on the next release that this command
succeeds for every published file of the new version.
