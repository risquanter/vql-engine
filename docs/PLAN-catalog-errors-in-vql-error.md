# Implementation Plan: The Catalog's Errors Are Declared in `vql.error`

**Status:** APPROVED (user, 2026-11-17), and implemented the same day. No
decision is open (§2). Approval of this document rules the settled points,
and each one is reversible.
**Version this was read at:** 0.21.0.
**Target version:** 0.22.0. Under early-semver a pre-1.0 breaking change bumps
the minor version. Two public types change package, so a consumer that names
either one must change its import. `PLAN-predicate-satisfying-set.md` also
targets 0.22.0. Both breaking changes can ship in that one release.
**Plan code:** `CEIVE`, the initials of the filename slug
`catalog-errors-in-vql-error`. Decisions are `CEIVE-D-<n>`.
**Origin:** a ruling by the user on 2026-11-17 to make the move. The ruling
also asks that the error's shape be checked against the project's other error
designs and plans. §4 holds that check.
**Records this plan amends:** `docs/ADR-023.md` §2 and its implementation
table, and the implementation table of `docs/ADR-006.md`. It creates no
record.

---

## 0. Executive summary

**What is there today.** `TypeCatalog.apply` validates a catalog and returns
`Either[List[TypeCatalogError], TypeCatalog]`. `TypeCatalogError` and the enum
its cases hold, `DeclarationSite`, are declared in
`core/src/main/scala/vql/typed/TypeCatalog.scala`. Every other error enum the
typed pipeline reports is declared in `vql.error`: `TypeCheckError` for the
binder and `RuntimeModelError` for model validation.

**What changes.**

1. **The move.** `TypeCatalogError` and `DeclarationSite` move to a new file,
   `core/src/main/scala/vql/error/TypeCatalogError.scala`. No alias stays in
   `vql.typed`.
2. **The sentence.** `TypeCatalogError` gains the derived `message` that
   `TypeCheckError` and `RuntimeModelError` already have. It reads
   `DeclarationSite.label`, which nothing reads today.
3. **The startup failure.** `TypeCatalog.unsafe` builds its exception text
   from each error's `message` in place of the case class's `toString`.

**What a consumer sees.** A consumer that names `TypeCatalogError` or
`DeclarationSite` imports them from `vql.error`. A catalog that
`TypeCatalog.unsafe` refuses throws with readable sentences. Every catalog
that validates today validates unchanged.

---

## 1. Requirements

1. `TypeCatalogError` and `DeclarationSite` are declared in `vql.error`, once.
2. `vql.typed` declares neither of them, and neither has an alias or a
   re-export there.
3. `vql.error` still imports nothing from `vql.typed`.
4. `TypeCatalogError` has a derived `message`, one sentence per case, built
   from the case's fields.
5. `TypeCatalog.unsafe`'s exception text holds each error's `message`.
6. Validation reports the same errors as today, in the same order.

### Not in scope

- Wrapping `TypeCatalogError` in a `QueryError` case. `TypeCatalog.apply`
  returns the error list itself, as `RuntimeModel.validateAgainst` returns
  `Either[List[RuntimeModelError], RuntimeModel]`. §4, point 4 shows the two
  factories have the same shape.
- Changing `DeclarationSite.Constant`'s `String` field. §4, point 6 gives the
  reason it stays.

---

## 2. Decisions

There are no open decisions. Each point below follows from a record or from
the code, and approval of the plan rules it.

### Settled by derivation

1. **No alias in `vql.typed`.** ADR-023, "Alternatives Rejected", rejects a
   second path to a moved error: "a second path for one type stays in the
   interface with no user once consumers move to `vql.error`."
2. **A file of its own.** Each error enum in `vql.error` has its own file,
   holding the error and the site enum it uses. `TypeCheckError.scala` holds
   `MismatchSite`, `SymbolKind` and `TypeCheckError`.
   `RuntimeModelError.scala` holds `RuntimeModelError`. The new file follows
   that pattern.
3. **A derived `message`.** §4, point 2 derives it from ADR-006 §3, ADR-023's
   code smell "A stored sentence beside the fields", and the failure family's
   naming pattern.
4. **`unsafe` reads `message`.** The sentence exists to be read, and `unsafe`
   is the library's one place that turns these errors into text. The
   exception's type and the catalogs it refuses are unchanged. Only the text
   changes.

---

## 3. ADR compliance review (planning phase)

| Record | Verdict | Basis |
|---|---|---|
| ADR-001 (many-sorted binding) | Relevant, compliant | `TypeCatalog.apply` keeps its validation and its return shape. §1's code block names `TypeCatalogError` without a package, so it stays true. |
| ADR-002 (parser combinators) | Not relevant | No parser changes. |
| ADR-003 (HDR sampling) | Not relevant | No sampling changes. |
| ADR-004 (layering) | Relevant, compliant | The new file imports `vql.id`, which ADR-004 §2 and ADR-023 §4 allow for `vql.error`. `TypeCatalog.scala` imports `vql.error`, the direction ADR-023 §4 states: "`vql.typed` ← imports `vql.error` and `vql.id`". |
| ADR-006 (enum encoding) | Relevant, compliant, table **amended** | Both types stay pure-data `enum`s. `message` and `label` are derived accessors under §3. The implementation table's row for `TypeCatalogError` names its old file, and Phase 2 moves it. |
| ADR-007 (ported core) | Not relevant | No file in its scope tables changes. |
| ADR-012 (error channel) | Relevant, compliant | `apply` keeps returning `Either` for a catalog that fails validation. `unsafe` keeps throwing `IllegalArgumentException`, which §1 of ADR-001 sanctions for tests and startup. |
| ADR-014 (quantifiability) | Not relevant | No check on domain types changes. |
| ADR-015 (value boundaries) | Not relevant | `literalValidators` is not edited. `DeclarationSite.LiteralValidator` is moved unchanged. |
| ADR-016 (carrier witness) | Proposed, not validated against | No literal payload changes. |
| ADR-017 (formula ranges) | Not relevant | No range or satisfying-set code changes. |
| ADR-018 (fragment membership) | Not relevant | `domainReturningFunctions` is not edited. |
| ADR-019 | Superseded by ADR-023 | Not validated against. |
| ADR-020 (error accumulation) | Relevant, compliant | The catalog keeps accumulating every error it finds. The move does not touch the binder. |
| ADR-021 | Superseded by ADR-023 | Not validated against. |
| ADR-022 (typed refusals) | Relevant, compliant | It governs refusals of a caller's query text. A catalog error reports the consumer's own declarations, so no caller text reaches it. §4, point 6 applies this to `DeclarationSite.Constant`. |
| ADR-023 (errors in `vql.error`) | Relevant, **amended** | The move applies §2's heading, "Each error is declared once, in `vql.error`". §2's body names `TypeCheckError` and `RuntimeModelError` only, so Phase 2 adds `TypeCatalogError` to it and to the implementation table. |
| ADR-024 (parse depth bound) | Not relevant | No parser or recursion changes. |
| ADR-025 (order-independent evaluation) | Not relevant | No evaluation or domain code changes. Error order is §4, point 5. |
| ADR-00X (record template) | Relevant, compliant | The two amendments keep each record's structure. |

**Layer 3 of `vql-adr-constraints`.** The row ADR-004 × ADR-023 applies. It
says that "adding a `vql.typed` import to `vql.error` to reach another type
inverts the layering". The new file imports only `vql.id`, so the layering
holds.

**Layer 4 of `vql-adr-constraints`.** No row applies. register's code names
neither moved type. In the repository `register`,
`modules/common/src/main/scala/com/risquanter/register/common/FolSymbols.scala`
line 248 and
`modules/server/src/main/scala/com/risquanter/register/foladapter/RiskTreeKnowledgeBase.scala`
line 387 call `TypeCatalog.unsafe`, and no register code reads the
exception's text.

**Amending the corpus.** No record is added, retired, or changes status. The
Layer 2 row for ADR-023 in `vql-adr-constraints` says ADR-023 governs "Errors
declared once in `vql.error`", which stays true. The skill therefore needs no
edit. The check in its "Keeping this document correct" section must still
print nothing (§7).

---

## 4. The error's shape, checked against the project's other designs

The ruling asks whether the error's shape matches the project's other error
designs and plans. Each point below compares one property of
`TypeCatalogError` with `TypeCheckError` (`vql/error/TypeCheckError.scala`)
and `RuntimeModelError` (`vql/error/RuntimeModelError.scala`). Those are the
other two errors the typed pipeline reports.

1. **Encoding: conforms.** All three are pure-data `enum`s with no per-case
   bodies, as ADR-006 §1 requires.

2. **The derived sentence: does not conform, and Phase 1 fixes it.**
   `TypeCheckError` and `RuntimeModelError` each declare
   `def message: String`. ADR-006 §3 shows `RuntimeModelError.message` as the
   pattern for "a logical accessor" that all cases share. The failure family's
   pattern in `vql-code-quality-review`, "Family search", is "one case per
   sentence; a derived `message`". `TypeCatalogError` has no `message`. So
   `TypeCatalog.unsafe` builds its text from `toString`, and a refused catalog
   throws this:

   ```
   Invalid TypeCatalog: UnknownType(Ghost,FunctionReturn(p95))
   ```

   `DeclarationSite.label` was written to name the site in such a sentence. A
   search of `core/src/main/scala` finds no reader of it. `MismatchSite.label`,
   its counterpart, is read by `TypeCheckError.message` for its `TypeMismatch`
   sentence. Giving `TypeCatalogError` a `message` puts `label` to work. After
   Phase 1 the same catalog throws this:

   ```
   Invalid TypeCatalog: unknown type 'Ghost' in function 'p95' return type
   ```

3. **Typed fields: conforms.** A sort is a `TypeId` and a symbol is a
   `SymbolName`, as ADR-023 §2 requires for `vql.error`.
   `SymbolKind` is the existing enum from `TypeCheckError.scala`.

4. **How the error is returned: conforms.** `TypeCatalog.apply` returns
   `Either[List[TypeCatalogError], TypeCatalog]`.
   `RuntimeModel.validateAgainst` returns
   `Either[List[RuntimeModelError], RuntimeModel]`. Both are validated
   factories, and both return every error they find, as a list. The facade
   wraps the model's list in `QueryError.ModelValidationError`, because
   `FolModel` performs that validation. No facade entry builds a catalog. The
   consumer builds it and passes it in. So no `QueryError` case for the
   catalog is needed.

5. **Accumulation and order: conforms.** Both validations report every error
   found. `collectErrors` walks the catalog's `Map`s, whose order is fixed by
   the keys' hash codes. `String.hashCode` is defined in the Java
   specification, and Scala.js implements the same function. The same catalog
   therefore yields the same list on every run and on both platforms, which
   ADR-025's reproducibility requirement needs. The move leaves the order
   unchanged.

6. **Raw primitives: conforms, with the reasons stated.**
   - `DeclarationSite.Constant(name: String)`. The key is matched against
     `Term.Const.name`, which ADR-007 C8 keeps as a `String`. A consumer may
     declare a key outside the literal alphabet, so `LiteralText` would throw
     while an error is being reported. `MismatchSite.Constant` holds a
     `LiteralText` because its name comes from the caller's query, which the
     lexer has already restricted to that alphabet.
   - `DeclarationSite.Parameter.index: Int`. It is the zero-based position
     `zipWithIndex` yields. No opaque type for a position exists, and
     `TypeCheckError.ArityMismatch` holds its counts as `Int`.

7. **Names: conform.** `TypeCatalogError` follows the failure family's
   `<Subject>Error` pattern. `DeclarationSite` follows `MismatchSite`: a
   `<Thing>Site` enum with a derived `label`, declared in the file of the
   error that holds it.

8. **The scaladoc: does not conform, and Phase 1 fixes it.** Both other enums
   carry a scaladoc that says what refused what. `TypeCheckError`'s is
   "Why `QueryBinder` refused a query or a satisfying-set formula."
   `TypeCatalogError` has none.

**The plans.** These are the plans not yet archived:
`PLAN-node-returning-functions.md`, `PLAN-order-independent-evaluation.md`,
`PLAN-parse-depth-bound.md`, `PLAN-predicate-satisfying-set.md` and
`PLAN-refusal-positions.md`. A search finds none that edits
`TypeCatalog.scala` or `TypeCatalogError`, except
`PLAN-node-returning-functions.md`. That plan is implemented. Its "Not in
scope" list says `TypeCatalogError` "stays in `vql.typed`", and its ADR-023
verdict row says the same. That reading took ADR-023 §2's body as the
complete list. This plan's ruling replaces it, so Phase 2 adds a pointer
under both passages. `PLAN-refusal-positions.md` is a stub about refusal
positions, which a catalog error has none of.

---

## 5. Target behaviour

### 5.1 The errors — `core/src/main/scala/vql/error/TypeCatalogError.scala`, new

```scala
package vql.error

import vql.id.{SymbolName, TypeId}

/** The place in a catalog declaration that names a type. */
enum DeclarationSite:
  case Constant(name: String)
  case Parameter(symbol: SymbolName, kind: SymbolKind, index: Int)
  case FunctionReturn(function: SymbolName)
  case LiteralValidator

  def label: String = this match
    case Constant(name)                             => s"constant '$name'"
    case Parameter(symbol, SymbolKind.Function, i)  => s"function '${symbol.value}' parameter $i"
    case Parameter(symbol, SymbolKind.Predicate, i) => s"predicate '${symbol.value}' parameter $i"
    case FunctionReturn(function)                   => s"function '${function.value}' return type"
    case LiteralValidator                           => "literal validator"

/** Why `TypeCatalog.apply` refused a catalog. Validation returns every error it finds. */
enum TypeCatalogError:
  /** `typeId` is named at `site` and is not declared in the catalog's types. */
  case UnknownType(typeId: TypeId, site: DeclarationSite)

  /** `symbol` is declared both as a function and as a predicate. */
  case NameCollision(symbol: SymbolName)

  /** The one human-readable sentence for this error. */
  def message: String = this match
    case UnknownType(typeId, site) => s"unknown type '${typeId.value}' in ${site.label}"
    case NameCollision(symbol)     =>
      s"'${symbol.value}' is declared as both a function and a predicate"

end TypeCatalogError
```

**Family of each type.** Both join the failure family of `vql.error`. The
file the family lives in is `core/src/main/scala/vql/error/TypeCheckError.scala`,
whose member `enum MismatchSite:` is the counterpart of `DeclarationSite` and
whose member `enum TypeCheckError:` is the counterpart of `TypeCatalogError`.
Neither type is new: each keeps its cases and fields exactly and changes only
its package. `TypeCatalogError.message` follows `RuntimeModelError.message`
in `core/src/main/scala/vql/error/RuntimeModelError.scala`.

**Raw primitives.** §4, point 6. `message` and `label` return `String`,
because a sentence is text and no type bounds it, as with
`TypeCheckError.message`.

The scaladoc of `UnknownType` drops its link to `TypeCatalog.types`. From
`vql.error` the link would name a member of `vql.typed`, the package
`vql.error` may not depend on. The sentence says "the catalog's types"
instead.

### 5.2 The catalog — `core/src/main/scala/vql/typed/TypeCatalog.scala`

The two enums are removed from this file. The import becomes:

```scala
import TypeDecl.*
import vql.error.{DeclarationSite, SymbolKind, TypeCatalogError}
```

`apply`'s signature is unchanged, and `TypeCatalogError` now names
`vql.error.TypeCatalogError`:

```scala
def apply(
  types: Set[TypeDecl],
  constants: Map[String, TypeId] = Map.empty,
  functions: Map[SymbolName, FunctionSig] = Map.empty,
  predicates: Map[SymbolName, PredicateSig] = Map.empty,
  literalValidators: Map[TypeId, String => Option[Any]] = Map.empty,
): Either[List[TypeCatalogError], TypeCatalog]
```

`apply`'s body tests the error list's shape with `isEmpty`. Under the
`boyscout` skill's obligation 7 that test becomes a `match`:

```scala
collectErrors(types, constants, functions, predicates, literalValidators) match
  case Nil    => Right(new TypeCatalog(types, constants, functions, predicates, literalValidators))
  case errors => Left(errors)
```

`unsafe`'s signature is unchanged. Its exception text reads `message`:

```scala
errors => throw IllegalArgumentException(s"Invalid TypeCatalog: ${errors.map(_.message).mkString("; ")}")
```

The separator becomes `"; "`, the one `QueryError.BindError` and
`QueryError.ModelValidationError` use between sentences. `unsafe`'s scaladoc
gains one sentence: the exception's text holds each error's sentence.

---

## 6. Implementation phases

### Phase 1 — the move and the sentence

1. Create `core/src/main/scala/vql/error/TypeCatalogError.scala` as §5.1
   states it.
2. Edit `core/src/main/scala/vql/typed/TypeCatalog.scala` as §5.2 states it.
3. Edit `core/src/test/scala/vql/typed/TypeCatalogSpec.scala`:
   - its import becomes `import vql.error.{DeclarationSite, SymbolKind, TypeCatalogError}`;
   - the tests §7 lists are added.
4. Run `sbt test`.

### Phase 2 — the records and the documents

1. `docs/ADR-023.md` §2: the first sentence becomes "`TypeCheckError`,
   `RuntimeModelError` and `TypeCatalogError` are declared in `vql.error`."
   The implementation table gains the row
   `| vql/error/TypeCatalogError.scala | TypeCatalogError, DeclarationSite |`.
2. `docs/ADR-006.md`, implementation table: the row
   `| vql/typed/TypeCatalog.scala | enum TypeCatalogError (§1) |` becomes
   `| vql/error/TypeCatalogError.scala | enum TypeCatalogError with derived message (§1, §3) |`.
3. `docs/Architecture.md`, package map: the `error/` line lists
   `TypeCatalogError` after `RuntimeModelError`.
4. `CHANGELOG.md`, the 0.22.0 section. If `PLAN-predicate-satisfying-set.md`
   has not created it yet, this phase creates it.
   - Under "Changed (breaking)": "`TypeCatalogError` and `DeclarationSite`
     are declared in `vql.error`. A consumer that names either one imports it
     from there. See ADR-023."
   - Under "Added": "`TypeCatalogError.message`."
   - Under "Changed": "`TypeCatalog.unsafe` reports each refused declaration
     as a sentence."
5. `.claude/skills/vql-code-quality-review/SKILL.md`, the "Failure, refusal
   or error" row of the family-search table: "and `TypeCatalogError` in
   `vql/typed/TypeCatalog.scala`" is removed, because `vql/error/` already
   names the file.
6. `docs/PLAN-node-returning-functions.md`: below the "Not in scope" item and
   below the ADR-023 verdict row, one sentence each: "`PLAN-catalog-errors-in-vql-error.md`
   moves `TypeCatalogError` to `vql.error`."

---

## 7. Verification plan

**Tests added to `core/src/test/scala/vql/typed/TypeCatalogSpec.scala`:**

1. `message` of `UnknownType` at each of the four `DeclarationSite` cases,
   both `SymbolKind`s of `Parameter` included. Expected, for one:
   `UnknownType(TypeId("Ghost"), DeclarationSite.FunctionReturn(SymbolName("p95"))).message`
   is `"unknown type 'Ghost' in function 'p95' return type"`.
2. `message` of `NameCollision(SymbolName("foo"))` is
   `"'foo' is declared as both a function and a predicate"`.
3. `TypeCatalog.unsafe` on a catalog with one unknown function return type
   throws `IllegalArgumentException` whose message is
   `"Invalid TypeCatalog: unknown type 'Ghost' in function 'p95' return type"`.

The existing tests keep every assertion. Only their import changes. No new
public entry is added, so no end-to-end test through parse, bind and evaluate
is required.

**Commands, run from the repository root:**

| Command | Expected |
|---|---|
| `sbt test` | pass, on the JVM and on Node |
| `grep -rn "import vql.typed" core/src/main/scala/vql/error` | no output |
| `grep -rn "enum TypeCatalogError\|enum DeclarationSite" core/src/main/scala` | one line each, both in `core/src/main/scala/vql/error/TypeCatalogError.scala` |
| `for f in docs/ADR-*.md; do grep -q "\`$(basename "$f")\`" .claude/skills/vql-adr-constraints/SKILL.md \|\| echo "MISSING $f"; done` | no output |

---

## File inventory

- `core/src/main/scala/vql/error/TypeCatalogError.scala`
- `core/src/main/scala/vql/typed/TypeCatalog.scala`
- `core/src/test/scala/vql/typed/TypeCatalogSpec.scala`
- `docs/ADR-023.md`
- `docs/ADR-006.md`
- `docs/Architecture.md`
- `CHANGELOG.md`
- `.claude/skills/vql-code-quality-review/SKILL.md`
- `docs/PLAN-node-returning-functions.md`
- `build.sbt` (the version line, run by the user)

---

## 9. Consumer-document impact

- `docs/Architecture.md`: the package map's `error/` line (Phase 2, step 3).
- `README.md`: no passage names `TypeCatalogError` or `DeclarationSite`.
- `docs/VagueQuantifiers.md`: no passage names either type.
- Scaladoc: the two moved enums' scaladoc moves with them, as §5.1 states.
  `unsafe`'s scaladoc gains one sentence (§5.2).

---

## 10. What register must do, named once

Nothing. register's code names neither moved type, and it reads
`TypeCatalog` from `vql.typed`, which does not move. Moving register's pin to
0.22.0 compiles with no change for this plan. No entry is written into
register's task list.
