# File inventory — PLAN-targeting-formula-inspection.md

The approval hook reads this file to decide whether a gated edit is
permitted. A gated edit is allowed only when the edited file appears on a
bullet line below, written as a full repo-relative path. Text that is not a
bullet line authorizes nothing.

Only the user writes this file, through `.claude/bin/approve-inventory`.

- core/src/main/scala/vql/fragment/Fragment.scala
- core/src/main/scala/vql/fragment/FragmentCheck.scala
- core/src/test/scala/vql/fragment/FragmentCheckSpec.scala
- core/.jvm/src/test/scala/parser/NestingBoundStackSpec.scala
- docs/ADR-018.md
- docs/Architecture.md
- README.md
- CHANGELOG.md
- build.sbt
