---
name: vql-supply-chain
description: "Supply-chain and versioning rules for vql-engine. Load before adding or updating any dependency (sbt library, sbt plugin, GitHub Action), bumping the version, or preparing a release, and when a review touches build.sbt, project/plugins.sbt or a workflow. Covers: exact pins, the adoption cooldown, the trust policy, what can and cannot be verified, the bots, the version table and who runs a release."
user-invocable: true
---

# Supply Chain and Versioning — vql-engine

The pinned surface is small:
- **`build.sbt`**: the Scala version, `munit` (tests only) and `hdr-rng`, which
  is first-party;
- **`project/plugins.sbt`**: `sbt-scalajs` and `sbt-scalajs-crossproject`;
- **`.github/workflows/`**: every GitHub Action, pinned by commit SHA with its
  version in a comment.

The release pipeline and the bots are described in `docs/RELEASE.md`.

## Rules for every dependency change

1. **Adding a library dependency is a decision.** It is decision trigger 3
   in `CLAUDE.md`. Present it and wait. ADR-003 rules out `scala.util.Random`,
   `java.util.Random` and `commons-math3`.
2. **Pin exactly.** Every sbt library and plugin version is exact, with no
   range and no `latest`. Every `uses:` in a workflow is a full commit SHA,
   with the tag it resolves to in a trailing comment.
3. **Wait 14 days.** Adopt no version published less than 14 days ago; take
   the newest one older than that. Two waivers apply:
   - a version that fixes a disclosed vulnerability affecting this library is
     adopted at once;
   - first-party `com.risquanter` artifacts (`hdr-rng`) are exempt.

   The publish date of a Maven Central artifact is on its page at
   central.sonatype.com. For a GitHub Action, it is the release date of the
   tag. This rule and the next are register's supply-chain policy (register,
   `docs/dev/decision-records/ADR-020-supply-chain-security.md` §10, §11),
   applied here. This repository has no decision record of its own for them.
4. **Prefer established publishers.** A dependency from an individual or an
   unestablished publisher needs the user's approval first. The approval is a
   comment at the pin site: the date, that the user approved it, and the
   reason.
5. **Know what is verified.**
   - **sbt libraries and plugins from Maven Central:** nothing verifies them on
     download. This is a known gap.
   - **GitHub Actions:** the commit SHA fixes the code that runs.
   - **This library's own artifacts:** ci-build GPG-signs and Sigstore-signs
     every artifact and attaches SLSA build provenance. The release workflow
     verifies each Sigstore signature against ci-build's identity on `main`
     before uploading (`docs/RELEASE.md`, "The three workflows").
   - **hdr-rng:** read its source from `/home/danago/projects/hdr-rng/core/`,
     never from a jar.

## The bots

- **Scala Steward** opens pull requests for the sbt libraries, the plugins,
  the Scala version and the sbt version. `.github/.scala-steward.conf` keeps
  sbt on the 1.x line, because the Scala.js plugins do not load under sbt 2.
- **Dependabot** opens pull requests for the GitHub Actions.
- `pr-build.yml` runs the full suite on every pull request. Merging is manual.
- **The cooldown and the trust policy apply to a bot's pull request** as to
  any other change. A review of a bot's pull request checks the publish date.

## Versions and releases

- `build.sbt` `ThisBuild / version` is the version, under early-semver. Which
  part is bumped is `CLAUDE.md`'s table, "Versioning and release".
- **The user runs every version change.** The agent proposes the `sed`
  command with the next version filled in, above the commit block, and never
  edits the version.
- **The user runs every release**, per `docs/RELEASE.md`. Its authorization
  step needs the user's hardware key, so the agent never starts one.
- A change to a workflow, to `.github/allowed_signers` or to the release
  environment is a change to the release gate. It enters the security
  review's scope.
