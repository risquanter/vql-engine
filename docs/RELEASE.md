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
| `release.yml` ("Release") | manual dispatch | Two jobs. `verify-authorization` checks the supplied TOTP code against the org secret (60-second period, YubiKey OATH credential) with the runner's preinstalled `python3`, accepting the current window and the two before it. `sign-and-publish` (gated on the first job, runs in the `release` environment restricted to main) refuses SNAPSHOT versions, then checks that the named run is a successful ci-build run and that the tag `v<version>` points at the commit it built and carries a signature from `.github/allowed_signers`. Only then does it download the bundle, verify every artifact's Sigstore signature against ci-build's OIDC identity on main, upload to the Central Portal as a staged `USER_MANAGED` deployment, and create the GitHub release on the existing tag. It never creates the tag. |

The split exists so that no human-triggered run ever builds the artifacts it publishes: release.yml only republishes what ci-build.yml already built and signed on main, and PR runs never touch signing at all.

Do not rename `ci-build.yml`: release.yml verifies Sigstore certificates against that literal workflow path.

## Required configuration (already in place)

- Org-level secrets: `TOTP_ORG`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `CENTRAL_PORTAL_ORG_TOKEN_NAME`, `CENTRAL_PORTAL_ORG_TOKEN`.
- Repo environment `release` with a deployment branch rule allowing only `main`.

## Cutting a release

Run every command in one terminal, from the repository checkout. Each step sets
shell variables that a later step reads, so a new terminal starts again from
step 2.

Prerequisites on the maintainer's machine:

- `gh` is logged in (`gh auth status`).
- git signs with the hardware SSH key: `gpg.format` is `ssh`, and
  `user.signingkey` names the key. Its public half is a signing key on the
  GitHub account and a line in `.github/allowed_signers`.
- `ykman` reaches the YubiKey.

**1. Set the version, commit with a signature, push to main.**

```bash
sed -i 's/^ThisBuild \/ version      := .*/ThisBuild \/ version      := "X.Y.Z"/' build.sbt
git add build.sbt            # and the release's other files
git commit -S -m "…"
git push
```

The version must not be a SNAPSHOT. On `main` with upstream `origin/main`,
`git push` pushes `main` to `origin`.

**2. Wait for the ci-build run of that push, and read the commit it built.**

```bash
RUN_ID=$(gh run list --workflow ci-build.yml --branch main --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status
HEAD_SHA=$(gh run view "$RUN_ID" --json headSha -q .headSha)
echo "run $RUN_ID built $HEAD_SHA"
```

`--exit-status` makes the watch fail when the run fails, and a failed run is
not released. The printed commit is the one step 1 pushed.

**3. Sign the release tag on exactly that commit, check it, push it.**

```bash
VERSION=$(sed -n 's#^ThisBuild / version[[:space:]]*:=[[:space:]]*"\([^"]*\)".*#\1#p' build.sbt)
git tag -s "v$VERSION" "$HEAD_SHA" -m "v$VERSION"
git tag -v "v$VERSION"
git push origin "v$VERSION"
```

The YubiKey asks for a touch. `release.yml` refuses a tag that is unsigned,
that is signed by a key outside `.github/allowed_signers`, or that points at any
other commit.

**4. Dispatch the release with a fresh TOTP code, and watch it.**

```bash
gh workflow run release.yml -f run_id="$RUN_ID" -f totp_code="$(ykman oath accounts code -s '60/risquanter/simulation-util:release')"
sleep 5
RELEASE_RUN_ID=$(gh run list --workflow release.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RELEASE_RUN_ID" --exit-status
```

`ykman` prints the code of the current 60-second window at once. The workflow
accepts that window and the two before it, and it checks the code seconds
after dispatch, so a fresh code is always valid. The org-wide OATH credential
`60/risquanter/simulation-util:release` authorizes releases in every
risquanter repository. `sleep 5` lets the new run appear before `gh run list`
reads it.

**5. Check that the commit and the tag are verified on GitHub.**

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
TAG_OBJECT=$(gh api "repos/$REPO/git/ref/tags/v$VERSION" -q .object.sha)
gh api "repos/$REPO/commits/$HEAD_SHA" -q .commit.verification
gh api "repos/$REPO/git/tags/$TAG_OBJECT" -q .verification
gh release view "v$VERSION"
```

Both `verification` objects read `"verified": true` and `"reason": "valid"`.
GitHub then shows the Verified badge on the commit and on the release's tag.

**6. Publish.** With both release jobs green, the deployment is staged. Finish
at <https://central.sonatype.com/publishing/deployments>: review it and click
Publish, or Drop. Publication to Central is irreversible.

Do not push another version change to main between steps 2 and 4.
`release.yml` reads the version from main's `build.sbt` when it is dispatched,
and that version must match the bundle and the tag.

## Dependency automation

Two bots keep dependencies current. Neither can publish a release — that stays the manual, TOTP-gated flow above; they only open PRs.

| Bot | Scope | Config |
|---|---|---|
| Dependabot | GitHub Actions (the SHA-pinned `uses:` in every workflow) | `.github/dependabot.yml` |
| Scala Steward | sbt library and plugin dependencies, the Scala version, the sbt version | `.github/workflows/scala-steward.yml`, `.github/.scala-steward.conf` |

Both open one PR per update against `main`, and `pr-build.yml` runs the full suite on each before it can merge. Merging stays manual.

Scala Steward authenticates with a fine-grained personal access token (`STEWARD_PAT`), not the workflow `GITHUB_TOKEN`. GitHub suppresses workflow runs on PRs opened by `GITHUB_TOKEN`, so `pr-build.yml` would not run; a PAT-opened PR triggers it, so every dependency bump is tested before merge.

Its commits are signed with a dedicated GPG key so they satisfy the signed-commit rule on `main`. GitHub marks them Verified because the key's public half and its committer email are both registered on the account that owns `STEWARD_PAT`. Signing this way requires a PAT rather than a GitHub App: the tool takes the committer identity from the token's owner, and a GitHub App's bot identity cannot hold a GPG key or a verified email.

Required repo secrets for Scala Steward:

- `STEWARD_PAT` — fine-grained PAT, resource owner `risquanter`, scoped to this repository only, with Contents and Pull requests read and write. Creating it needs an org owner to approve the token.
- `STEWARD_GPG_PRIVATE_KEY` — the dedicated signing key as an ASCII-armored private key.
- `STEWARD_GPG_PASSPHRASE` — the key's passphrase (only if the key is passphrase-protected).

The signing key's id, committer email, and name are read from the imported key at runtime, so they need no secrets of their own.

The key's public half must be added to the GitHub account that owns `STEWARD_PAT`, and its committer email must be a verified email on that account, or GitHub will not mark the commits Verified.

## Verifying a published artifact

Artifacts carry detached GPG signatures (`.asc`) and Sigstore bundles (`.sigstore.json`):

```bash
cosign verify-blob --bundle vql-engine_3-<version>.jar.sigstore.json \
  --certificate-identity-regexp="https://github.com/risquanter/vql-engine/.github/workflows/ci-build.yml@refs/heads/main" \
  --certificate-oidc-issuer=https://token.actions.githubusercontent.com \
  vql-engine_3-<version>.jar
```
