# File inventory — PLAN-catalog-errors-in-vql-error.md

The approval hook reads this file to decide whether a gated edit is
permitted. A gated edit is allowed only when the edited file appears on a
bullet line below, written as a full repo-relative path. Text that is not a
bullet line authorizes nothing.

Only the user writes this file, through `.claude/bin/approve-inventory`.

- core/src/main/scala/vql/error/TypeCatalogError.scala
- core/src/main/scala/vql/typed/TypeCatalog.scala
- core/src/test/scala/vql/typed/TypeCatalogSpec.scala
- docs/ADR-023.md
- docs/ADR-006.md
- docs/ADR-004.md
- docs/Architecture.md
- CHANGELOG.md
- docs/PLAN-node-returning-functions.md
- build.sbt
