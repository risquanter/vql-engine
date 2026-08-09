# Release guide

How vql-engine is built, verified, and published to Maven Central.

## Published artifacts

- `com.risquanter:vql-engine_3` (JVM, Scala 3)
- `com.risquanter:vql-engine_sjs1_3` (Scala.js 1.x, Scala 3)

Consumers write `"com.risquanter" %%% "vql-engine" % "<version>"`.

## The three workflows

| Workflow | Trigger | What it does |
|---|---|---|
| `pr-build.yml` ("PR build") | pull requests to main | Tests only (JVM + Scala.js). References no secrets, so runs from fork PRs are safe by construction. |
| `ci-build.yml` ("CI Build + Sigstore provenance") | push to main | Tests, then stages a Maven-layout bundle (`sbt publish` → `target/bundle`), GPG-signs and Sigstore-signs every jar/pom, and uploads the zipped bundle as a run artifact (7-day retention). Each run's ID identifies a releasable bundle. |
| `release.yml` ("Release") | manual dispatch | Two jobs. `verify-authorization` checks the supplied TOTP code against the org secret (60-second period, YubiKey OATH credential). `sign-and-publish` (gated on the first job, runs in the `release` environment restricted to main) downloads the named ci-build run's bundle, verifies every artifact's Sigstore signature against ci-build's OIDC identity on main, refuses SNAPSHOT versions, uploads to the Central Portal as a staged `USER_MANAGED` deployment, and creates the git tag plus GitHub release. |

The split exists so that no human-triggered run ever builds the artifacts it publishes: release.yml only republishes what ci-build.yml already built and signed on main, and PR runs never touch signing at all.

Do not rename `ci-build.yml`: release.yml verifies Sigstore certificates against that literal workflow path.

## Required configuration (already in place)

- Org-level secrets: `TOTP_ORG`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `CENTRAL_PORTAL_ORG_TOKEN_NAME`, `CENTRAL_PORTAL_ORG_TOKEN`.
- Repo environment `release` with a deployment branch rule allowing only `main`.

## Cutting a release

1. Set a non-SNAPSHOT version in `build.sbt` (`ThisBuild / version := "X.Y.Z"`), commit, push to main.
2. Wait for the resulting ci-build run to go green and note its run ID (Actions → the run → the number in the URL, or `gh run list --workflow ci-build.yml`).
3. Generate a fresh TOTP code from the YubiKey. The credential period is 60 seconds and the release run spends time installing tools before verifying, so use a code from the start of a window. This command counts down to the next window boundary and then prints a full-life code:

   ```bash
   t=$(( $(date +%s) / 60 * 60 + 60 )); while (( r = t - $(date +%s), r > 0 )); do printf '\rfresh code in %2ds ' "$r"; sleep 1; done; printf '\r\033[K'; ykman oath accounts code -s '60/risquanter/simulation-util:release'
   ```

   The single org-wide OATH credential is named `60/risquanter/simulation-util:release`; the same code authorizes releases in every risquanter repo.
4. Actions → "Release (Maven Central Publisher Portal, GPG-signed)" → Run workflow; enter the ci-build run ID and the TOTP code.
5. Both jobs green means the deployment is staged. Finish at <https://central.sonatype.com/publishing/deployments>: review and click Publish (or Drop). Publication to Central is irreversible.

Do not push another version change to main between steps 2 and 4: release.yml reads the version from main's build.sbt at dispatch time and it must match the bundle being released.

## Verifying a published artifact

Artifacts carry detached GPG signatures (`.asc`) and Sigstore bundles (`.sigstore.json`):

```bash
cosign verify-blob --bundle vql-engine_3-<version>.jar.sigstore.json \
  --certificate-identity-regexp="https://github.com/risquanter/vql-engine/.github/workflows/ci-build.yml@refs/heads/main" \
  --certificate-oidc-issuer=https://token.actions.githubusercontent.com \
  vql-engine_3-<version>.jar
```
