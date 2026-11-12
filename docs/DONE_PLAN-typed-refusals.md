# Implementation Plan: Typed Refusals

**Status:** APPROVED 2026-11-12. All six phases implemented 2026-11-12; the suite is green on both platforms. Written 2026-11-11 and
revised 2026-11-12. Decisions `TR-D-1`
to `TR-D-7` were ruled on 2026-11-11, and `TR-D-8` and `TR-D-9` on 2026-11-12.
None is open.
**Version this was read at:** 0.17.0.
**Target version:** 0.18.0. Under early-semver a pre-1.0 breaking change bumps
the minor version, and this plan removes and reshapes public error fields.
**Plan code:** `TR`, the initials of the filename slug `typed-refusals`.
Decisions are `TR-D-<n>`.
**Origin:** register, the downstream consumer, in the repository `register`:
`docs/dev/plans/PLAN-UES-UNIFIED-ERROR-SHAPE.md`, its steps 5.6 and 5.7. Those
steps wait on the release this plan produces. Register's requirements are
restated in §1 as this plan's own, so no label of that plan is used here.
**Companion stub:** [PLAN-refusal-positions.md](PLAN-refusal-positions.md),
which analyses how far positions can be filled. Register does not wait on it.
**New ADRs created by this plan:** `docs/ADR-022.md` and `docs/ADR-023.md`,
which do not exist yet. The implementing session writes them in Phase 6.

---

## 0. Executive summary

**What changes.** A refusal is a `Left` the engine returns because of what the
caller typed. Five kinds of refusal are still carried as composed sentences:
the lexer's, the formula parser's, the vague-query parser's, the two variable
rules of `ParsedQuery.mk`, and the site of a type mismatch. Each becomes a case
of a closed set that holds the values its sentence is built from. The sentence
stays, derived from those values.

Two structural changes carry that through the engine. Each bind error and each
model error is declared once, in `vql.error`, and its copy is removed
(`TR-D-8`). A tree is built only through functions that check its names, so a
name a refusal reads is always one that query text can produce (`TR-D-9`).

**Why.** Register must not pass a composed sentence or the caller's whole
query to a client or a log. It may pass values whose content it can state. A
sentence that embeds `tokensLabel(…)` output or an exception message has no
such content type. With typed cases, register handles an engine refusal with
one `match` arm per case and reads no message text.

**What a consumer sees.**

- `QueryError.ParseError` and `QueryError.ValidationError` change their fields.
  `ParseError.input` and both `context` maps are removed.
- `parser.ParseError`'s three cases carry typed values and a position.
- `BindErrorDetail` is removed. `QueryError.BindError.details` is a
  `List[TypeCheckError]`. A detail's `rendered` string is now
  `TypeCheckError.message`.
- `TypeCheckError` and `RuntimeModelError` are declared in `vql.error`. Their
  old path `vql.typed` no longer holds them.
- `TypeId` and `SymbolName` are declared in a new package, `vql.id`. The paths
  `vql.typed.TypeId` and `vql.typed.SymbolName` still resolve.
- Every sort in an error is a `TypeId`, where 0.17.0 carried `TypeId.value`.
- `TypeCheckError.TypeMismatch.context: String` becomes `site: MismatchSite`.
- `TypeCheckError.ArityMismatch` gains `kind: SymbolKind`, which says whether
  the symbol is a predicate or a function.
- A quoted literal admits only `[A-Za-z0-9 /_.+-]`. Any other character is a
  lexer refusal with the character's offset.
- A quantifier whose fraction lies outside [0, 1], whose denominator is zero,
  or whose tolerance lies outside [0, 1] is refused. The `Quantifier` case
  constructors are closed, so a value outside those bounds cannot be built.
- `VagueQueryParser.parse` and `FOLParser.parseWithLexer` no longer turn an
  arbitrary exception into a parse error.
- `QueryError.ModelValidationError` and `QueryError.EvaluationError` carry
  typed details. Their sentences are unchanged.
- The case constructors of `Term`, of `FOL`, and of `Formula.Forall` and
  `Formula.Exists` are closed. A tree is built through `Term.mkVar`,
  `Term.mkFn`, `Term.mkConst`, `FOL(…)`, `Formula.mkForall` and
  `Formula.mkExists`. Each throws `IllegalArgumentException` for a name no
  query text can produce. Pattern matching is unchanged.
- Every name in a refusal carries a type.
- Every sentence is byte-identical to 0.17.0, with the exceptions listed in
  §5.10.

---

## 1. Requirements

1. Every refusal in scope is a case of a closed set. There is one case per
   sentence template.
2. Each case carries the values its sentence is built from as typed fields.
   The sentence is derived from the fields and is never the only carrier.
3. A token in a refusal is typed by its kind. It carries text only for a word,
   a decimal or a quoted literal. Punctuation and a known operator are cases of
   a closed set. An unknown operator run and a character outside every lexer
   class are a kind with no text.
4. A parse context is a case of the parser's fixed set of contexts.
5. A quoted literal admits a fixed alphabet. A character outside it is a lexer
   refusal of its own kind, with the character's offset.
6. A type-mismatch site is one of three cases: a variable, a constant, a
   function's return.
7. A refusal carries a position, and "no position" is a named case of the
   position type.
8. A boundary converts only the parser's own failure types. Any other
   exception propagates as a defect.
9. The engine's error types keep their own root. Nothing of register's is
   imported.
10. Each refusal case is attributable to one of the engine's three inputs: the
    query text, the catalog with its model, or the answer tuple. The engine
    carries no trust zone, because the zone depends on where the caller got
    the input. §9 states the attribution of every case.
11. Each error is declared once. No error type has a copy in another package,
    and no function translates one declaration into another.
12. A name in a tree is one that query text can produce. The check runs when
    the tree is built, so building a refusal about a name cannot fail.

### Not in scope

- A position on a bind refusal. `TypeCheckError` gains no position field. The stub's §3.8 said "every refusal carries a position"
  and its §4 excluded the bind refusals. This plan follows the exclusion. The
  reading is reversible in the companion plan.
- Positions beyond the lexer refusals. See `TR-D-2` for the one parse site
  that reports a position today.
- A maximum length for query text, a word or a literal.
- `parser/SimpleExpr.scala`, `TermParser.parseFromString` and
  `FOLAtomParser.parseFromString`. Their throws cannot reach either `Either`
  boundary, because `FOLParser.parseWithLexer` and `VagueQueryParser.parse`
  call none of them. They keep `throw new Exception(…)`.
- `StringUtil.alphanumeric` keeps the apostrophe. Removing it would narrow
  what a valid word is and would edit a predicate ported from the OCaml
  original. Register leaves the apostrophe to its own output encoding.
- A typed name inside the tree. `Term.Var(name: String)` and its siblings keep
  `String` as the field type. `TR-D-9` closes the constructors and checks the
  name. It does not change the field, so code that reads a name is unchanged.
- The typed intermediate language beyond one check. `BoundVar` gains the
  variable-name check of §5.13. `BoundTerm`, `BoundAtom` and `BoundFormula`
  are not edited.

---

## 2. Decisions for the user

All nine decisions are ruled. Each of `TR-D-1` to `TR-D-7` was written so the
plan is implementable under either ruling, and its option text is kept as
presented. `TR-D-8` and `TR-D-9` changed what three of them produce, and each
ruling paragraph says how. §5 shows what ships.

### TR-D-1 — how a foundation throw carries its kind (ADR-007 C12)

**Ruled 2026-11-11: Option A.** The user stated the intent of ADR-007 with the
ruling: the frozen style exists primarily to keep the parser-combinator shape,
and a message may be adapted where that fits this plan's intent better. The
note ADR-007 gains under `TR-D-4` records both. This plan uses the permission
only for the sentences listed in §5.10. The option text below is kept as
presented.

**Affected ADR:** ADR-007 (Preserve OCaml-Ported Parser Combinator Core).
**ADR states:** "C12 — `failwith` mapped to `throw new Exception(…)`. OCaml's
`failwith "message"` is consistently translated to `throw new
Exception("message")`. Error messages are often preserved verbatim from the
OCaml source." Its review criterion says "A change that breaks a
characteristic requires explicit justification and approval."
**Deviation:** five sites throw `new Exception("…")` today and would throw a
subclass of `Exception` that also holds a typed value.

**Goal and context.** A typed case can only reach a boundary if the throw
carries it. The foundation parsers signal failure by throwing, and the boundary
sees only what was thrown. Nine throw sites can reach a boundary (§4.2). Four
already throw the subclass `Combinators.ParseFailure`. Five throw a plain
`Exception`.

Register read this change as an element-type refinement under C13. The text of
C13 does not support that. C13 covers "element types within the parser
pipeline (e.g. the lexer token type, intermediate parse-result components)",
and a thrown value is neither. So the change is judged against C12 directly.

**Option A — the throws carry a typed value, and ADR-007 records it.**
`ParseFailure` gains a
`failure: SyntaxFailure` field. A second class, `SyntaxError`, replaces the
plain `Exception` at the five sites. Both take their message from the typed
value, so every OCaml message is word for word what it is today.

- For: every formula refusal reaches register as a case with its fields.
- For: `throw` stays the mechanism and every `try/catch` stays as written, so
  C2 holds. The four `ParseFailure` sites show a subclass with a verbatim
  message is already this codebase's practice.
- Against: C12's letter says `new Exception`. Five ported sites change the
  class they throw.
- Example: you type `(p(x,), q(x))` as a range. Register receives
  `Formula(TermExpected(Found.Token(Symbol(RParen))))` and renders "expected a
  term, found `)`" in its own words.

**Option B — the proposal complies with C12 as written.** The ported files
are not edited. The boundaries catch the plain
`Exception` and return one case with no fields.

- For: no ported throw site changes.
- Against: requirement 8 fails. A boundary that catches plain `Exception`
  cannot tell a parse refusal from a defect.
- Against: register receives "the formula is malformed" with no detail for
  every one of the nine sites.
- Example: you type the same range. Register receives `Formula(Malformed)` and
  can say only that the query did not parse.

**Recommendation, the plan author's: Option A.** It keeps what C12 protects,
the `failwith` to `throw` mapping and the verbatim message. Option B fails two
of the requirements. Under A, ADR-007's review criterion gains a recorded note,
as it has for the `FOLParser` boundary conversion.

**Under Option B:** §5.4 is dropped, `ParseError.Syntax` and
`ParseRefusal.Formula` carry no field, and the two boundaries keep a catch of
`Exception`.

**Question:** Option A or B?

### TR-D-2 — the trailing-tokens position

**Ruled 2026-11-11: Option B.** The exact position is the work of
`PLAN-refusal-positions.md`. The option text below is kept as presented.

**Goal and context.** One parse site reports a position today. When tokens
remain after a complete query, `VagueQueryParser.parse` computes
`s.length - tokensLabel(remaining).length`. The value is exact only when the
leftover text is spaced exactly as `tokensLabel` renders it. Measured against
0.17.0, with the true offset 36 in each case:

| Leftover text | Reported |
|---|---|
| `extra` | 36 |
| `"a b"` | 36 |
| `extra  tokens` (two spaces) | 37 |
| `extra ` (trailing space) | 37 |
| `f(x)` | 33 |

Register shows a reported position to the user as the place of the error.

**Option A — carry the estimate as `SourcePosition.At`.**

- For: no information is lost where the estimate is right.
- Against: a typed position is then sometimes wrong, and nothing marks which.
- Example: you type `… large(x)) f(x)`. The pane points three characters
  before the leftover text.

**Option B — report `SourcePosition.Unknown` at this site.** The companion
analysis decides whether tokens gain offsets. If they do, this site becomes
exact.

- For: every `At` the engine reports is exact.
- Against: the single-leftover-token case loses a position it reports
  correctly today.
- Example: you type `… large(x)) extra`. The pane says tokens remain after the
  query and shows no position.

**Option C — make it exact in this plan.** The lexer returns each token's
start offset beside the token list, and the parser reads the first leftover
token's offset.

- For: the position is exact and present.
- Against: it settles the companion analysis's second question, how tokens
  carry offsets, before that analysis is written. `mergeDecimalTokens` has to
  keep the offsets aligned.
- Example: the pane points at `f` in the case above.

**Recommendation, the plan author's: Option B.** A position that is wrong
without notice is worse than a visible `Unknown`, and Option C decides the
companion's design inside a different plan.

**Under Option A:** §5.7's trailing-tokens arm passes
`SourcePosition.At(s.length - labels.length)`. **Under Option C:** the plan
gains a lexer step that the companion stub's §4 item 2 describes.

**Question:** Option A, B or C?

### TR-D-3 — quantifier parameters out of range

**Ruled 2026-11-11: Option A, in the user's words:** the engine follows the
mathematical meaning of the publication faithfully, enforces it with types, or
with guards where a runtime check is needed, and channels a violation into the
typed refusals of this plan. §5.6 holds the two refusal cases and §5.11 the
closed `Quantifier` constructors. The handoff note tells register that an
example or a test may encode a quantifier with no mathematical meaning, and
asks it to run its integration tests. The option text below is kept as
presented.

**Goal and context.** Reading the parser for its refusals showed three inputs
that are not refused. Measured against 0.17.0:

| Query prefix | Result |
|---|---|
| `Q[>=]^{5/3}` | parses to `AtLeast(5, 3, 0.1)`, target proportion 1.67 |
| `Q[>=]^{1/0}` | parses to `AtLeast(1, 0, 0.1)`, target proportion `Infinity` |
| `Q[~]^{1/2}[2.5]` | parses to `About(1, 2, 2.5)` |

`parseQuantifier` builds the `Quantifier` cases directly. The checks live in
`Quantifier.mkAbout`, `mkAtLeast` and `mkAtMost`, which it does not call. The
stub's scope said the literal alphabet is the only narrowing of what is
accepted, so refusing these is outside it.

**Option A — refuse them in this plan.** `ParseRefusal` gains two cases, shown
in §5.6 as conditional.

- For: the closed set is complete when register writes its translation. A case
  added later is another breaking release and another register change.
- Against: three inputs that parse today are refused. It is a second behaviour
  change beside the literal alphabet.
- Example: you type `Q[>=]^{5/3} x (…)`. Register answers that the fraction
  must lie between 0 and 1.

**Option B — its own plan.** This plan records the finding in `docs/TODOS.md`.

- For: this release changes what is accepted in one place only.
- Against: `ParseRefusal` changes again when that plan lands.
- Example: the same query is answered "false" for every model, with no
  refusal.

**Option C — leave it.** The inputs stay accepted.

- Against: a target proportion of `Infinity` is evaluated with no notice.

**Recommendation, the plan author's: Option A.** The refusals are being
enumerated now, and the fix sits in the function this plan already rewrites.

**Question:** Option A, B or C?

### TR-D-4 — the ADRs this plan makes stale

**Ruled 2026-11-11: Option A.** `TR-D-8` and `TR-D-9` added rows to the table
on 2026-11-12. The user ruled with them that ADR-019 and ADR-021 are
superseded and that ADR-007 C8 is reworded.

**Goal and context.** Accepted ADRs show code or state rules this plan
changes. An ADR is a standing constraint, so overwriting one is the user's
call.

| ADR | Stale passage | Handling |
|---|---|---|
| ADR-001 | the implementation table row "`vql/typed/TypeDefs.scala` \| `TypeId`, `SymbolName` opaque types" | the row names `vql/id/Identifiers.scala` |
| ADR-002 | §2's sketch of `parse` builds `QueryError.ParseError(...)` and catches `QueryException` only | sketch updated to the typed catch arms |
| ADR-004 | §2's diagram lists the `vql/` packages without `id/` | the diagram gains `id/`, and one sentence states that `logic` imports `lexer` |
| ADR-006 | the implementation table names `vql/typed/RuntimeModelError.scala` | the row names `vql/error/RuntimeModelError.scala` |
| ADR-007 | C8 says "The structure is preserved verbatim." The review criterion has no note for typed throws | C8 reworded as `TR-D-9` states; one note added under the review criterion for `TR-D-1` |
| ADR-012 | "The vague layer calls only the `Either`-returning FOL entry, so foundation throws are never caught ad hoc inside vague code." `VagueQueryParser.parseTokens` calls `FormulaParser.parse` directly at 0.17.0, so the sentence is already inaccurate. Line 102's sketch builds `DomainNotFoundError(sort.value, model.domains.keySet.map(_.value))`. Line 111's sketch builds `ValidationError("empty", "name")` | sentence corrected to name the one catch at `VagueQueryParser.parse`; both sketches updated |
| ADR-014 | §2's examples build `TypeNotQuantifiable("Loss")`. The implementation table names `vql/typed/TypeCheckError.scala`, `vql/typed/RuntimeModelError.scala` and a `renderTypeError` arm | examples build `TypeNotQuantifiable(TypeId("Loss"))`; the rows name the new files and `TypeCheckError.message` |
| ADR-015 | §4's sketch builds `TypeCheckError.UnparseableConstant(name, expected, name)` from a raw `String` | sketch updated to the typed name |
| ADR-017 | line 206's sketch builds `QueryError.ValidationError(e.message, "formula")`. The implementation table names a `renderTypeError` arm | sketch updated to a constructible value; the row names `TypeCheckError.message` |
| ADR-019 | the whole record: the primitive-only `BindErrorDetail`, `renderTypeError`, the fold in the facade, and the rejection of alternative E3 | status `Superseded by ADR-023`, with a marker at each section |
| ADR-020 | the context and the cross-ADR section say `BindError` carries `List[BindErrorDetail]` and name the facade fold | the sentences name `List[TypeCheckError]` |
| ADR-021 | the whole record: one `BindErrorDetail` case per variant and the exhaustive fold | status `Superseded by ADR-023`, with a marker at each section |

The routing skill `.claude/skills/vql-adr-constraints/SKILL.md` repeats
ADR-012's sentence in its Layer 3 row ADR-002 × ADR-007 × ADR-012: "vague code
calls only the `Either`-returning foundation entry". The row is corrected with
ADR-012. Its rows and activities that name ADR-019 and ADR-021 are replaced as
Phase 6 states.

**Option A — amend every record in the table in Phase 6, and write the new
records.**
**Option B — write the new records only, and list the rest as known stale.**

**Recommendation, the plan author's: Option A.** Each passage would otherwise
show code that does not compile.

**Question:** Option A or B?

### TR-D-5 — whether a text field has its own type

**Ruled 2026-11-11: Option B, extended the same day to every name read from
a tree.** The user ruled that the supported source of a tree is the parser. A
tree built in code is supported when its names are ones query text could
produce. So every field that holds the caller's text carries a type, in the
parse refusals, the variable rules, the bind errors and the evaluation
failures. §5.1 holds the types. `TR-D-9` moved the check of a name from the
point a refusal is built to the point the tree is built, and §5.13 holds that
rule. §5.9 names the one refusal field that still holds caller text as a
`String`. The option text below is kept as presented.

**Goal and context.** Some fields hold the caller's text: a word, a decimal, a
literal's content, a variable name. The plan gate asks each raw `String` in a
signature for "either the opaque type that replaces it … or the reason none
applies". Register refines every such field into a type of its own at its
boundary, and treats a value that does not refine as a defect.

**Option A — `String`, with the content stated and tested.** Each field's
scaladoc states its content, and `TokenKindSpec` pins it against the lexer's
character classes. The reason no opaque type applies: the text is built only
by `TokenKind.of` from a token the lexer produced, and a consumer cannot share
an engine type across the boundary, so it refines the text itself either way.
In `vql.error` a primitive is the sanctioned form under ADR-019.

- For: no new types. `BindErrorDetail` already carries names this way.
- Against: the content is a statement and a test, and no type holds it.
- Example: a lexer change admits `:` in a word. `TokenKindSpec` fails in the
  engine's build.

**Option B — opaque types in `lexer`.** `WordText`, `DecimalText` and
`LiteralText`, each built through a `require` (ADR-012 §1) and read through
`.value`, as `SymbolName` is.

- For: a field's type says what it holds, and a value outside it cannot exist.
- Against: three types and their constructors, and register still converts
  each into its own type.
- Example: the same lexer change fails a `require` when a refusal is built, in
  the engine, before register sees the value.

**Recommendation, the plan author's: Option A**, on the condition that
register keeps refining at its boundary, which its plan states.

**Under Option B:** the `String` fields of `TokenKind`, `IntegerOutOfRange`,
`ValidationProblem` and `MismatchSite` take the opaque types, and §5.1 gains
their declarations.

**Question:** Option A or B?

### TR-D-6 — the two error cases that still carry sentences

**Ruled 2026-11-11: Option B.** §5.12 holds the signatures that ship.
`TR-D-8` removed the copy `ModelErrorDetail` that the option's code block
shows: `ModelValidationError` holds `RuntimeModelError` itself. The option
text below is kept as presented.

**Goal and context.** After this plan two `QueryError` cases still carry a
composed sentence as their only content. Neither is caused by query text.
Register answers both as a fault of its own knowledge base and logs
`formatted`.

- `ModelValidationError(errors: List[String])` is built at one site,
  `FolModel.apply`, from `RuntimeModelError`. That is an enum of three cases
  with typed fields. The site keeps each case's sentence and drops its fields,
  because `vql.error` may not import `vql.typed`.
- `EvaluationError(message, phase, cause, context)` is built at three sites in
  `TypedSemantics`. Two wrap the text a consumer's dispatcher returned, with
  the phase `predicate:<name>` or `function:<name>`. One composes `Variable
  '<v>' has type <X>, expected <Y>` with the phase `typed_term`. No site passes
  `cause` or `context`.

An earlier version of this decision also listed the `context` map of four
other cases. Each of those maps is derived from the case's typed fields, so it
breaks no requirement. It also said typing these cases would roughly double
the plan. That was not measured. The measured size is four sites and two
enums.

A sentence template alone would make the sentences uniform. The sentence would
still be the only carrier, which is the defect.

**Option A — record both in `docs/TODOS.md`.**

- For: this release changes only what register's waiting steps need.
- Against: `QueryError` breaks a second time when they are typed.
- Example: a dispatcher is missing a predicate. Register's log line is built
  from the engine's sentence, as today.

**Option B — type both in this plan.**

```scala
// vql/error/QueryError.scala
enum ModelErrorDetail:
  case MissingFunctionImplementation(name: String)
  case MissingPredicateImplementation(name: String)
  case MissingDomainForType(sortName: String)

  def message: String

case class ModelValidationError(details: List[ModelErrorDetail]) extends QueryError:
  def errors: List[String] = details.map(_.message)

enum EvaluationFailure:
  case PredicateRefused(name: String, dispatcherMessage: String)
  case FunctionRefused(name: String, dispatcherMessage: String)
  case VariableSortMismatch(variable: String, expectedSort: String, actualSort: String)

  def phase: String
  def message: String

case class EvaluationError(failure: EvaluationFailure) extends QueryError
```

Every `message`, `phase` and `formatted` string is byte-identical to 0.17.0.
The fold from `RuntimeModelError` runs in `FolModel.apply`, which already
imports both packages. `dispatcherMessage` stays a `String`: it is text the
consumer's own dispatcher returned through `Either[String, _]` (ADR-015), and
typing it would change the dispatcher interface. `cause` is removed. No site
has a `Throwable` to put in it, and no consumer reads it.

- For: `QueryError` breaks once. The fields `FolModel.apply` drops today
  reach the consumer.
- Against: four more sites and two more enums in this release. Register does
  not need them for its waiting steps.
- Example: the same log line is built from `MissingPredicateImplementation`
  and its name.

**Recommendation, the plan author's: Option B.** The work is small and sits in
the file this plan already rewrites.

**Under Option B:** the two enums join §5.6, `FolModel.scala` joins the file
inventory, and `MapDispatcherSpec` and `VagueSemanticsTypedSpec` assert the
typed details.

**Question:** Option A or B?

### TR-D-7 — the shape of the type-mismatch site (ADR-019, ADR-021)

**Ruled 2026-11-11: Option B, with the scope extended.** One `TypeMismatch`
case holds an enum field, `MismatchSite`. The ruling also brought one more
field into this plan: the kind of symbol an arity mismatch names, which §5.8
adds as `SymbolKind`. Both stay. `TR-D-8` removed `BindErrorDetail`, so the
widened field rule this option stated has nothing left to govern and no record
states it. The option text below is kept as presented.

**Affected ADRs.** ADR-021 §1 says each `BindErrorDetail` case holds "that
variant's fields as primitives", and its cross-ADR section says "every
`BindErrorDetail` field is a primitive". ADR-019's cross-ADR section says
"`BindErrorDetail` has only `String` fields". ADR-021 §1 also fixes "one
`BindErrorDetail` case per `TypeCheckError` variant".

**Where the rule comes from.** ADR-019's context states its purpose: "The
error layer must not depend on the typed layer, where sort identity (`TypeId`)
and the structured `TypeCheckError` live." The typed package already imports
the error package, in `FolModel` and `TypedSemantics`, so the reverse import
would make the two packages depend on each other. ADR-019 weighed turning the
direction around, as alternative E3, and rejected it: "it inverts the ADR-004
layering". It added: "The refactor size is a real cost but is not what settles
it — the layering inversion and the disproportion-to-need are." It left one
door open: E3 "becomes the natural design only if consumers come to need rich,
structured, multi-sort bind-error analysis".

**Goal and context.** A type mismatch happens at one of three places: a
variable, a constant, or a function's return. Today the place is a sentence
fragment in `TypeMismatch.context`. It has to become a closed set of three. A
closed set of three is an enum, and an enum field is not a primitive.

**Option A — the proposal complies: three variants with primitive fields.**
`TypeCheckError.TypeMismatch` is replaced by three variants, and
`BindErrorDetail` by three matching cases:

```scala
// vql/typed/TypeCheckError.scala
case VariableSortMismatch(name: String, expected: TypeId, actual: TypeId)
case ConstantSortMismatch(name: String, expected: TypeId, actual: TypeId)
case FunctionReturnSortMismatch(name: String, expected: TypeId, actual: TypeId)

// vql/error/QueryError.scala, in BindErrorDetail
case VariableSortMismatch(name: String, expectedSort: String, actualSort: String, rendered: String)
case ConstantSortMismatch(name: String, expectedSort: String, actualSort: String, rendered: String)
case FunctionReturnSortMismatch(name: String, expectedSort: String, actualSort: String, rendered: String)
```

- For: no record changes. Every field stays a primitive, and the fold stays
  one arm per variant, now thirteen.
- Against: one concept is spread over three cases with the same fields. A
  consumer that treats every sort mismatch alike writes three arms.
- Example: register builds its `SortMismatch(site, expected, actual)` from
  three engine cases.

**Option B — the records change: one case with an enum field.** The shape of
§5.8. `MismatchSite` is an enum declared in `vql.error`, and ADR-022 states
that a `BindErrorDetail` field may be a primitive or an enum of primitives
declared in `vql.error`.

- For: one case for one concept. The purpose of the primitive rule still
  holds, because `vql.error` imports nothing from `vql.typed`.
- Against: the first `BindErrorDetail` field that is not a primitive.
  `TypeCheckError`, in `vql.typed`, takes a field type from `vql.error`.
  ADR-019 and ADR-021 gain a pointer to the widened rule.
- Example: register reads `detail.site` and translates the enum with one
  three-arm `match`.

**Recommendation, the plan author's: Option B**, on the condition that the
purpose of the rule is the import direction and not the field type as such.
ADR-019's context states the purpose: "The error layer must not depend on the
typed layer".

**Under Option A:** §5.8 is replaced by the signatures above, `MismatchSite`
is not introduced, `QueryBinder`'s three sites build the three variants, and
`renderTypeError` gains two arms with the same sentences.

**Question:** Option A or B?

### TR-D-8 — whether each error is declared once (ADR-019, ADR-021)

**Ruled 2026-11-12: Option A.** Each error is declared once, in this plan,
ahead of the work it replaces. ADR-019 and ADR-021 are superseded by ADR-023.

**Affected ADRs.** ADR-019 weighed alternative E3, "relocate
`TypeCheckError`/`TypeId` into `vql.error`", and rejected it: "it inverts the
ADR-004 layering". ADR-021 §1 fixes "one `BindErrorDetail` case per
`TypeCheckError` variant".

**Goal and context.** At 0.17.0 a bind error is declared twice.
`TypeCheckError`, in `vql.typed`, holds each sort as a `TypeId`.
`BindErrorDetail`, in `vql.error`, holds the same fields as strings and adds a
stored sentence. `VagueSemantics.toBindErrorDetail` translates the first into
the second with eleven arms. The second declaration exists for one reason:
`vql.error` may not import `vql.typed`, and `TypeId` is declared in
`vql.typed`. `TR-D-6` and `TR-D-7`, as first ruled, each added to the second
declaration: a three-case copy of `RuntimeModelError`, and a rule for which
field types the copy may hold. Register meets both declarations. It reads
`BindErrorDetail` on its query path and `TypeCheckError` where it calls the
binder directly.

**Option A — move the two identifier types below both packages, in this
plan.** `TypeId` and `SymbolName` move to a new package, `vql.id`, which
imports nothing. `vql.error` and `vql.typed` both import it. `TypeCheckError`
and `RuntimeModelError` move to `vql.error` and hold their sorts as `TypeId`.
The copies and the translations are not written.

- For: one declaration per error. A new case is added in one place.
- For: `vql.error` still imports nothing from `vql.typed`. That is the purpose
  ADR-019's context states: "The error layer must not depend on the typed
  layer".
- For: the old path of the identifier types keeps working, through a type
  alias and a forwarding companion in `vql.typed` (§5.14).
- Against: it overrules ADR-019's rejection of E3, and two accepted records
  are superseded.
- Against: register rewrites its bind-error arms from `BindErrorDetail` to
  `TypeCheckError`, and changes three import lines.
- Example: register's `QueryProblem.fromQueryError` and its
  `MitigationScopeResolverLive.fromTypeCheckError` match on the same type, so
  register writes one translation.

**Option B — a later plan.** This plan ships the copies `TR-D-6` and `TR-D-7`
ruled, and a later plan removes them.

- For: this release changes less.
- Against: this plan writes a copy, a translation and a field rule that the
  later plan deletes. `QueryError` breaks a second time, and register changes
  twice.
- Example: register translates `ModelErrorDetail` at 0.18.0 and
  `RuntimeModelError` at the release after it.

**Recommendation, the plan author's: Option A.**

**What the ruling does to earlier rulings.** `TR-D-6`: `ModelErrorDetail` and
`RuntimeModelError.detail` are not written. `TR-D-7`: `MismatchSite` and
`SymbolKind` stay, and the widened field rule is not recorded. `TR-D-5`: the
typed names sit on the one `TypeCheckError`.

**Three choices the plan author made inside the ruling.** Each is reversible
until this document is approved.

1. The surviving name is `TypeCheckError`, because `QueryBinder.bind` returns
   it in ADR-001. `BindErrorDetail` is removed.
2. The new package is named `vql.id`.
3. `TypeCheckError` and `RuntimeModelError` are not re-exported from
   `vql.typed`. Register edits the bind-error code in the same change, so a
   second path for the same type would stay in the interface with no user.

### TR-D-9 — closed tree constructors (ADR-007 C8)

**Ruled 2026-11-12: Option A.** The constructors are closed in this plan. The
user explicitly overruled ADR-007 C8 for this change, and asked that the
record be relaxed so the change is not found later as a conflict.

**Affected ADR.** ADR-007 C8: "`Term`, `FOL`, `Formula[+A]` are direct Scala
`enum`/`case class` equivalents of OCaml's algebraic types … The structure is
preserved verbatim."

**Goal and context.** `TR-D-5` types every name a refusal reads. A name in a
tree is a `String`, and the tree's constructors are public, so at 0.17.0 code
can build `Term.Var("risk-id")`. No query text produces that name. As first
written, this plan found such a name only when a refusal about it was built,
and threw there. The tree itself stayed buildable with any name.

**Option A — close the constructors in this plan.** The case constructors
that hold a name are `private[logic]`. Functions in the companions build the
same values and check the name first. The field types stay `String`.

- For: a wrong name fails on the line that builds the tree. A tree with a
  wrong name cannot exist, so building a refusal cannot throw.
- For: the two documented cases of the late check, and their tests, are not
  written.
- Against: C8's "preserved verbatim" no longer holds for construction. Four
  main files and fourteen test files outside the package `logic` change every
  line that builds a tree.
- Example: `Term.Var("risk-id")` does not compile outside `logic`.
  `Term.mkVar("risk-id")` throws `IllegalArgumentException` on that line.
  `Term.mkVar("x")` builds the same value as today, and `case Term.Var(name)`
  matches it.

**Option B — a later plan.** This plan ships the late check, and a later plan
replaces it.

- Against: the late check and its documented behaviour ship in 0.18.0 and are
  removed in the release after it.

**Option C — a typed name inside the tree.** `Var` holds a `VariableText`.

- For: a name's role is kept apart by the compiler, and an operator is a case
  to match on.
- Against: every reader of a name changes, including `FragmentCheck`, whose
  result register reads. No code builds trees today except the parser and
  tests, and no defect of mixed-up names is known.

**Recommendation, the plan author's: Option A.** Option C is listed under
"Not in scope".

**What C8 keeps.** The cases, their fields and their field types map one to
one to the OCaml definitions, and the OCaml source stays in the scaladoc
(C11). What changes is who may call a case constructor.

---

## 3. ADR compliance review (planning phase)

| ADR | Verdict | Basis |
|---|---|---|
| ADR-001 (many-sorted binding) | Relevant, compliant, table stale | The typed IL and the success path are unchanged. `QueryBinder.bind` still returns `Either[List[TypeCheckError], BoundQuery]`. `BoundVar` gains a construction check. `ModelValidationError.errors`, which a sketch in the record reads, stays as a derived accessor. See `TR-D-4`. |
| ADR-002 (parser-combinator style) | Relevant, compliant, sketch stale | Parsers still return tuples and throw. The single `Either` boundary stays at `VagueQueryParser.parse`. See `TR-D-4`. |
| ADR-003 (HDR sampling) | Not relevant | No sampling code is touched. |
| ADR-004 (layering) | Relevant, compliant, diagram stale | No foundation file imports `vql.*`. `logic` imports `lexer`, and both are foundation packages. `vql.id` imports nothing. `vql.error` imports `vql.id` and the foundation. `vql.typed` imports `vql.error` and `vql.id`. No package imports one that imports it. |
| ADR-006 (enum vs sealed trait) | Relevant, compliant, table stale | The new sets are pure-data `enum`s. `message` and `label` are derived accessors under §3, and `TypeCheckError.message` replaces the stored `rendered`. `QueryError` stays a `sealed trait`. |
| ADR-007 (OCaml-ported core) | Relevant, **two deviations, ruled** | C12, see `TR-D-1`. C8, see `TR-D-9`. C1 to C7, C9 to C11 and C13 hold: signatures, tuple threading, every `try/catch`, mutual recursion and the OCaml scaladoc are unchanged. `StringUtil` is not edited. |
| ADR-012 (error channel) | Relevant, compliant, text stale | Refusals stay on the `Either` left channel. Ported files keep `throw`. A tree name is a construction invariant, checked by `require` (§1). A quantifier bound read from text is refused as a value, and the `require`s in `Quantifier.mk*` stay as the second check of §3. See `TR-D-4`. |
| ADR-014 (quantifiability) | Relevant, compliant, sketches stale | The bind-time check and the startup check both stay. `TypeNotQuantifiable` holds the sort as a `TypeId`. See `TR-D-4`. |
| ADR-015 (value boundaries) | Relevant, compliant, sketch stale | No cast is added. The binder's literal branch is unchanged. A dispatcher's text stays a `String`. |
| ADR-016 (carrier witness) | Not relevant, Proposed | Value typeclasses are untouched. |
| ADR-017 (formula ranges) | Relevant, compliant, sketch stale | The two variable rules keep their meaning and their order. The `TypeNotQuantifiable` check in the satisfying-set path stays. See `TR-D-4`. |
| ADR-018 (fragment membership) | Relevant, compliant | `FragmentCheck` reads a tree by pattern matching, which is unchanged. Its consumers map `parser.ParseError`, which changes shape. |
| ADR-019 (structured bind-error detail) | Relevant, **superseded, ruled** | `TR-D-8`. The purpose its context states is kept: `vql.error` imports nothing from `vql.typed`. |
| ADR-020 (binder error accumulation) | Relevant, compliant, text stale | Accumulation is unchanged. The accumulated list is what `BindError` carries. |
| ADR-021 (per-variant detail) | Relevant, **superseded, ruled** | `TR-D-8`. No fold remains, because there is one enum. |

**Deviations detected:** three, all ruled. ADR-007 C12 was ruled in `TR-D-1`.
ADR-007 C8 was ruled in `TR-D-9`. The copy that ADR-019 and ADR-021 require
was ruled in `TR-D-8`.

**What the review read.** Every record in force was read in full on
2026-11-11: ADR-001, 002, 003, 004, 006, 007, 012, 014, 015, 017, 018, 019,
020 and 021, and the template ADR-00X. ADR-016 is `Proposed`, is not in force,
and was not read. On 2026-11-12, for `TR-D-8` and `TR-D-9`, these were read
again: ADR-004 §2, ADR-007 C8, C11, C13 and its review criterion, and every
line of the records that names `BindErrorDetail`, `TypeCheckError`,
`RuntimeModelError`, `TypeDefs`, `renderTypeError`, `DomainNotFoundError` or
`ModelValidationError`. The activities of `vql-adr-constraints` Layer 1 that
this plan falls under are: editing a file in the ported foundation, changing
`VagueQueryParser`, designing an error type, changing the binder, changing
evaluation, changing a failure, changing the public API, and writing a record.

**Layer 3 rows checked.**

- ADR-002 × ADR-007 × ADR-012: the plan keeps one catch, at
  `VagueQueryParser.parse`, and wraps no foundation throw mid-flight. The
  row's own wording is stale, see `TR-D-4`.
- ADR-007 × ADR-006: an import added to a ported file follows that file's
  existing import style. No import in a ported file is tidied.
- ADR-007 C11 × the comment-style rule: no OCaml scaladoc is trimmed or
  reworded. A ported file's comments are brought to style only where they
  describe a shape this plan replaces.
- ADR-001 × ADR-014, ADR-014 × ADR-017 and ADR-020 × ADR-014 × ADR-017: both
  quantifiability checks, the satisfying-set replica and the order of the
  sequential checks are unchanged.
- ADR-017 × ADR-002: `ParsedQuery.mk` still throws behind the one boundary.
- ADR-006 §3 × ADR-019 × ADR-021: `rendered` is removed. `position` is stored,
  so it is an abstract member. `message` and `label` are derived, and no case
  has a parameter of either name.
- ADR-019 × ADR-021 and ADR-004 × ADR-019 × ADR-021: both rows describe the
  copy and its fold. Phase 6 replaces the three rows that name ADR-019 with
  one row, ADR-004 × ADR-023.

**Layer 4.** Row two says register reads
`BindErrorDetail.UnparseableConstant.sortName`. The field becomes
`TypeCheckError.UnparseableConstant.sort`, a `TypeId`. Register's ADR-028
appendix §5 states the meaning, "Every bind error is a quoted node reference
no literal validator accepts", and that stays true. The skill's row is
reworded in Phase 6. The same appendix lists `Unparseable` as "The text does
not parse", which stays true. Row one rests on the binder's literal branch,
which is not edited. Row three rests on `TypeCatalog`, `MapDispatcher`,
`evaluateTyped`, `extract[A]` and the Scala.js build, which are unchanged.

---

## 4. Current behaviour (grounding)

### 4.1 The lexer

`Lexer.lexStringLit` refuses a missing closing quote and an embedded `\n`. It
admits every other character. `Q[~]^{1/2} x (country(x), large("a<b"))` parses
to `Const("a<b")` at 0.17.0.

`LexerError(msg: String)` carries a message only. `Lexer.lex` recurses on the
remaining characters and does not know the input's length.

A character outside every lexer class becomes a one-character `Token.OpSym`.
`≥` and `≤` reach the parser this way.

### 4.2 The foundation throw sites that reach a boundary

| Site | File | Thrown today | Message |
|---|---|---|---|
| F1 | `FormulaParser.parseAtomicFormula`, `Nil` | `Exception` | `formula expected` |
| F2 | `FormulaParser.parseQuant`, `Nil` | `Exception` | `Body of quantified term expected` |
| F3 | `FormulaParser.parseQuant`, other token | `Exception` | `Body of quantified term expected, got: <token>` |
| A1 | `FOLAtomParser.parseInfixAtom` | `ParseFailure` | `Not an infix atom` |
| A2 | `FOLAtomParser.parseAtom`, `Nil` | `Exception` | `parse_atom: expected atom` |
| A3 | `FOLAtomParser.parseAtom`, other token | `Exception` | `parse_atom` |
| T1 | `TermParser.parseAtomicTerm`, `Nil` | `ParseFailure` | `term expected` |
| T2 | `TermParser.parseAtomicTerm`, other token | `ParseFailure` | `term expected, got: <token>` |
| C1 | `Combinators.parseBracketed` | `ParseFailure` | `Closing bracket '<b>' expected, but got: <token or "end of input">` |

Two `try` blocks backtrack, one in `parseAtom` and one in `parseAtomicFormula`.
Each wraps a call that can throw only A1, T1, T2 and C1, and each catches
`ParseFailure` by name.

### 4.3 The two boundaries

`FOLParser.parseWithLexer` catches `LexerError` and then `NonFatal(e)`.
`VagueQueryParser.parse` catches `QueryException`, `LexerError` and then
`Exception`. Both last arms report any exception as the caller's parse error.
At 0.17.0 the range `(> , large(x))` is reported as "Unexpected error during
parsing: parse_atom".

### 4.4 The vague-query parser

`QueryError.ParseError(message, input, position, context)` is built at fourteen
sites in `vql/parser/VagueQueryParser.scala`. They are ten templates, because
four pairs differ only in "got a token" against "got end of input". `input` is
the whole query at the three sites in `parse` and a `tokensLabel` rendering or
`""` at the other eleven.

### 4.5 `ValidationError`

`QueryError.ValidationError(message, field, context)` is built at four sites.
Two are the variable rules of `ParsedQuery.mk`. Two are in
`TypedSemantics.validateAnswerTuple`: a missing answer value and an answer
value of the wrong sort. The stub named only the first two. A case class has
one shape, so all four sites change together.

### 4.6 The type-mismatch site

`QueryBinder` builds `TypeCheckError.TypeMismatch(expected, actual, context)`
with `s"variable '$name'"`, `s"constant '$name'"` or
`s"function '$name' return"`.

### 4.7 Who reads these types

Register reads `QueryError.ParseError.message` and `.position`,
`QueryError.ValidationError.message`, and `parser.ParseError.message`. In this
repository `examples/VagueDemo.scala` and `examples/FOLDemo.scala` read
`message` and `formatted`, which stay.

### 4.8 The two declarations of a bind error

`TypeCheckError`, in `vql/typed/TypeCheckError.scala`, has eleven cases and
holds each sort as a `TypeId`. `BindErrorDetail`, in
`vql/error/QueryError.scala`, has the same eleven cases with each sort as a
`String` and a stored `rendered` sentence. `VagueSemantics.toBindErrorDetail`
translates one into the other, and `VagueSemantics.renderTypeError` holds the
eleven sentences. `RuntimeModelError`, in `vql/typed/RuntimeModelError.scala`,
has three cases. `FolModel.apply` keeps each case's sentence and drops its
fields. `TypeId` and `SymbolName` are declared in `vql/typed/TypeDefs.scala`.

### 4.9 Who builds a tree

The constructors of `Term`, `FOL` and `Formula` are public. In main source
outside the package `logic`, a tree is built by `TermParser`, `FOLAtomParser`
and `FormulaParser`, by the three examples in `ParsedQuery`, and by
`examples/FOLDemo.scala`. Fourteen test files outside the package `logic`
build trees. Register builds none, in main source or tests.

A scan on 2026-11-12 read every string literal passed to a tree constructor,
to `BoundVar`, to `ParsedQuery`'s variable fields and to the satisfying-set
entries in this repository. It found no name that query text cannot produce.

---

## 5. Target behaviour

Every signature below is new or changed. Scaladoc is abbreviated to what the
implementer needs.

### 5.1 Tokens in a refusal — `core/src/main/scala/lexer/TokenKind.scala` (new)

```scala
package lexer

/** Punctuation and the operators the foundation grammar recognises. `text` is the surface form. */
enum GrammarSymbol(val text: String):
  case LParen       extends GrammarSymbol("(")
  case RParen       extends GrammarSymbol(")")
  case LBracket     extends GrammarSymbol("[")
  case RBracket     extends GrammarSymbol("]")
  case LBrace       extends GrammarSymbol("{")
  case RBrace       extends GrammarSymbol("}")
  case Comma        extends GrammarSymbol(",")
  case Dot          extends GrammarSymbol(".")
  case And          extends GrammarSymbol("/\\")
  case Or           extends GrammarSymbol("\\/")
  case Implies      extends GrammarSymbol("==>")
  case Iff          extends GrammarSymbol("<=>")
  case Not          extends GrammarSymbol("~")
  case Equal        extends GrammarSymbol("=")
  case Less         extends GrammarSymbol("<")
  case LessEqual    extends GrammarSymbol("<=")
  case Greater      extends GrammarSymbol(">")
  case GreaterEqual extends GrammarSymbol(">=")
  case Cons         extends GrammarSymbol("::")
  case Plus         extends GrammarSymbol("+")
  case Minus        extends GrammarSymbol("-")
  case Times        extends GrammarSymbol("*")
  case Divide       extends GrammarSymbol("/")
  case Power        extends GrammarSymbol("^")

object GrammarSymbol:
  def fromText(text: String): Option[GrammarSymbol]

/** What a token is, as a refusal carries it. Text is held only where its content is bounded. */
enum TokenKind:
  /** A run of `StringUtil.alphanumeric` characters. */
  case Word(text: WordText)
  /** `digits.digits`, the token `VagueQueryParser.mergeDecimalTokens` builds. */
  case Decimal(text: DecimalText)
  /** The content of a quoted literal, within the literal alphabet. */
  case Literal(content: LiteralText)
  case Symbol(symbol: GrammarSymbol)
  /** A run of `StringUtil.symbolic` characters that is no `GrammarSymbol`. */
  case UnknownOperator
  /** A character outside every lexer class. */
  case StrayCharacter

  def label: String = this match
    case Word(text)       => text.value
    case Decimal(text)    => text.value
    case Literal(content) => "\"" + content.value + "\""
    case Symbol(symbol)   => symbol.text
    case UnknownOperator  => "unknown operator"
    case StrayCharacter   => "unexpected character"

object TokenKind:
  def of(token: Token): TokenKind

/** What a parser found where its rule needed something else. */
enum Found:
  case Token(kind: TokenKind)
  case EndOfInput

object Found:
  def of(tokens: List[lexer.Token]): Found
```

The three text types are declared in `core/src/main/scala/lexer/TokenText.scala`
(new), in the form `TypeId` has in `vql/typed/TypeDefs.scala`:

```scala
package lexer

import util.StringUtil

/** The text of a word token: one or more `StringUtil.alphanumeric` characters. */
opaque type WordText = String

object WordText:
  def apply(value: String): WordText =
    require(value.nonEmpty && value.forall(StringUtil.alphanumeric), "WordText must be a non-empty alphanumeric run")
    value

  extension (text: WordText) def value: String = text

/** The text of a merged decimal token: digits, `.`, digits. */
opaque type DecimalText = String

object DecimalText:
  def apply(value: String): DecimalText =
    require(StringUtil.isDecimalLiteral(value), "DecimalText must be digits.digits")
    value

  extension (text: DecimalText) def value: String = text

/** The content of a quoted literal: zero or more characters of the literal alphabet. */
opaque type LiteralText = String

object LiteralText:
  def apply(value: String): LiteralText =
    require(value.forall(Lexer.literalChars), "LiteralText must stay within the literal alphabet")
    value

  extension (text: LiteralText) def value: String = text
```

Two enums in the same file type a name read from a tree:

```scala
/** The name of a variable: the text of a `Token.Word`. */
enum VariableText:
  case Word(text: WordText)
  case Decimal(text: DecimalText)

  def value: String = this match
    case Word(text)    => text.value
    case Decimal(text) => text.value

object VariableText:
  /** Whether `name` is text a `Token.Word` can hold. */
  def admits(name: String): Boolean

  /** Throws `IllegalArgumentException` unless `admits(name)`. */
  def of(name: String): VariableText

/** The name of a predicate or function: the text of a `Token.Word`, or an operator. */
enum SymbolText:
  case Word(text: WordText)
  case Decimal(text: DecimalText)
  case Operator(symbol: GrammarSymbol)

  def value: String = this match
    case Word(text)       => text.value
    case Decimal(text)    => text.value
    case Operator(symbol) => symbol.text

object SymbolText:
  /** Whether `name` is a word, a decimal, or one of `=`, `<`, `<=`, `>`, `>=`. */
  def admitsPredicate(name: String): Boolean

  /** Whether `name` is a word, a decimal, or one of `+`, `-`, `*`, `/`, `^`, `::`. */
  def admitsFunction(name: String): Boolean

  /** Throws `IllegalArgumentException` unless `admitsPredicate(name)` or `admitsFunction(name)`. */
  def of(name: String): SymbolText
```

`object LiteralText` gains the same predicate, and its `apply` requires it:

```scala
  /** Whether every character of `value` is in the literal alphabet. */
  def admits(value: String): Boolean = value.forall(Lexer.literalChars)
```

`VariableText.admits(name)` holds when `StringUtil.isDecimalLiteral(name)`, or
when `name` is non-empty and every character satisfies
`StringUtil.alphanumeric`. `VariableText.of` yields `Decimal` in the first
case and `Word(WordText(name))` in the second. `SymbolText.admitsPredicate`
holds when `VariableText.admits(name)` or `name` is the text of one of the five
relations `FOLAtomParser.parseInfixAtom` names an atom by.
`SymbolText.admitsFunction` holds when `VariableText.admits(name)` or `name`
is the text of one of the six operators `TermParser` names a function
application by. `SymbolText.of` yields `Operator` for any of those eleven, and
otherwise decides as `VariableText.of` does. A constant's name is a
`LiteralText`.

**Ruled 2026-11-12, after Phase 2: Option B.** The plan first had one
`SymbolText.admits`, true for a word, a decimal or any `GrammarSymbol`'s text.
It admitted punctuation as a name and did not tell a predicate from a
function, so `FOL("+", …)` and `Term.mkFn("(", Nil)` would have passed
requirement 12's check. The user ruled one check per position.

The four `admits` predicates are what the building functions of §5.13 check.
The two `of` functions and `LiteralText.apply` are what a refusal is built
with.

Each `require` is a construction invariant under ADR-012 §1. For a token,
`TokenKind.of` is the one caller. A token from `Lexer.lex` always passes. A token from a
custom lexer handed to `FOLParser.parseWithLexer` passes only if that lexer
keeps the same character classes, and a violation is a defect of that lexer.

The 24 `GrammarSymbol` cases are the eight punctuation tokens of `Token` and
the sixteen operator strings the foundation parsers match:
`FormulaParser` five, `FOLAtomParser.relOps` five, `TermParser` six. The
quantifier comparators `~#`, `≥` and `≤` belong to the vague grammar, so the
foundation classifies them as `UnknownOperator` and `StrayCharacter`.

`TokenKind.of` maps `Token.Word(s)` to `Decimal` when
`StringUtil.isDecimalLiteral(s)` and to `Word` otherwise. It maps
`Token.OpSym(s)` to `Symbol` when `GrammarSymbol.fromText(s)` is defined, to
`UnknownOperator` when `StringUtil.symbolic(s.head)`, and to `StrayCharacter`
otherwise.

`label` equals `Combinators.tokenLabel` for every token except an unknown
operator and a stray character. `tokenLabel` and `tokensLabel` stay, because
`SimpleExpr`, the two `parseFromString` entries and the test suites call them.

### 5.2 The lexer — `lexer/LexFailure.scala` (new), `lexer/Lexer.scala`

```scala
package lexer

enum LexFailure:
  case UnterminatedLiteral
  case NewlineInLiteral
  case RefusedLiteralCharacter

  def message: String = this match
    case UnterminatedLiteral     => "Unterminated string literal: missing closing '\"'"
    case NewlineInLiteral        => "Unterminated string literal: newline before closing '\"'"
    case RefusedLiteralCharacter => "String literal holds a character outside its alphabet"

/** `remaining` counts the characters from the refused one to the end of the input, 0 when the input ended. */
class LexerError(val failure: LexFailure, val remaining: Int) extends Exception(failure.message)
```

In `Lexer`:

```scala
/** The characters a quoted literal admits. */
private[lexer] val literalChars: Set[Char] =
  ("abcdefghijklmnopqrstuvwxyz" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "0123456789" + " /_.+-").toSet
```

The alphabet's origin, character by character. Letters, digits and `_` are
what a bare word already admits, less the apostrophe. The space, `/` and `-`
are what register's node names hold. `.`, `+` and `-` are what
`LiteralParser[Long]` and `LiteralParser[Double]` read in a number, and the
exponent letter is a letter. No consumer names another character.

**Ruled 2026-11-11: the user approved this alphabet and overruled including
the apostrophe in it.** A quoted literal never holds an apostrophe. An
apostrophe still reaches a consumer inside a word, because
`StringUtil.alphanumeric` keeps it.

`lexStringLit` gains one arm after the closing-quote arm: a character not in
`literalChars` throws `LexerError(RefusedLiteralCharacter, inp.length)`. The
closing quote is outside the alphabet, so the new arm must follow the arm that
ends the literal. The plan first placed it before that arm, which would have
refused every closing quote. The implementation of Phase 2 corrected the
order on 2026-11-12. The two existing arms throw
`LexerError(UnterminatedLiteral, 0)` and `LexerError(NewlineInLiteral,
inp.length)`. `inp.length` is computed only on the failure path.

A boundary turns `remaining` into an offset as `s.length - e.remaining`.
`StringUtil.explode` is `s.toList`, so an offset counts UTF-16 code units from
zero. That is the index a browser uses for the same string.

`lexwhile` and the ported arms of `lex` are not edited.

### 5.3 Position — `parser/SourcePosition.scala` (new)

```scala
package parser

/** Where in the text handed to a parser a refusal points. */
enum SourcePosition:
  /** A UTF-16 code-unit offset from zero, within the input. */
  case At(offset: Int)
  case Unknown
```

It lives in the foundation because `parser.ParseError` uses it (ADR-004).

### 5.4 Formula refusals — `parser/SyntaxFailure.scala` (new), `parser/Combinators.scala`

```scala
package parser

import lexer.{Found, TokenKind}

/** Why a foundation parser could not build a term or formula. One case per throw template. */
enum SyntaxFailure:
  case FormulaExpected
  case QuantifierBodyExpected(found: Found)
  case AtomExpected(found: Found)
  case TermExpected(found: Found)
  case ClosingBracketExpected(bracket: TokenKind, found: Found)
  case NotAnInfixAtom

  def message: String
```

`message`, word for word:

| Case | `EndOfInput` | `Token(kind)` |
|---|---|---|
| `FormulaExpected` | `formula expected` | — |
| `QuantifierBodyExpected` | `Body of quantified term expected` | `Body of quantified term expected, got: <label>` |
| `AtomExpected` | `parse_atom: expected atom` | `parse_atom` |
| `TermExpected` | `term expected` | `term expected, got: <label>` |
| `ClosingBracketExpected` | `Closing bracket '<bracket label>' expected, but got: end of input` | `Closing bracket '<bracket label>' expected, but got: <label>` |
| `NotAnInfixAtom` | `Not an infix atom` | — |

In `Combinators`:

```scala
/** Backtracking failure, the analogue of OCaml's `Failure _` caught by `try`. */
class ParseFailure(val failure: SyntaxFailure) extends Exception(failure.message)

/** A failure no parser backtracks over. */
class SyntaxError(val failure: SyntaxFailure) extends Exception(failure.message)
```

Each site of §4.2 throws the same kind of exception as today, with its case:

| Site | Throws |
|---|---|
| F1 | `SyntaxError(FormulaExpected)` |
| F2, F3 | `SyntaxError(QuantifierBodyExpected(Found.of(inp)))` |
| A1 | `ParseFailure(NotAnInfixAtom)` |
| A2, A3 | `SyntaxError(AtomExpected(Found.of(inp)))` |
| T1, T2 | `ParseFailure(TermExpected(Found.of(inp)))` |
| C1 | `ParseFailure(ClosingBracketExpected(TokenKind.of(cbra), Found.of(rest)))` |

F2 with F3, A2 with A3 and T1 with T2 stay separate `match` arms, as the OCaml
source has them. The two `catch case _: ParseFailure` blocks are not edited.

### 5.5 The foundation boundary — `parser/ParseError.scala`, `parser/FOLParser.scala`

```scala
enum ParseError:
  case Syntax(failure: SyntaxFailure, position: SourcePosition)
  case Trailing(first: TokenKind, rest: List[TokenKind], position: SourcePosition)
  case Lex(failure: LexFailure, position: SourcePosition)

  /** Stored per case, so an abstract member the case parameters implement (ADR-006 §3). */
  def position: SourcePosition

  def message: String = this match
    case Syntax(failure, _)     => failure.message
    case Trailing(first, rest, _) => s"Unparsed input: ${(first :: rest).map(_.label).mkString(" ")}"
    case Lex(failure, _)        => failure.message
```

`FOLParser.parseWithLexer`:

```scala
try
  val tokens         = lexer(explode(s))
  val (result, rest) = parseTokens(tokens)
  rest match
    case Nil           => Right(result)
    case first :: more =>
      Left(ParseError.Trailing(TokenKind.of(first), more.map(TokenKind.of), SourcePosition.Unknown))
catch
  case e: LexerError   => Left(ParseError.Lex(e.failure, SourcePosition.At(s.length - e.remaining)))
  case e: ParseFailure => Left(ParseError.Syntax(e.failure, SourcePosition.Unknown))
  case e: SyntaxError  => Left(ParseError.Syntax(e.failure, SourcePosition.Unknown))
```

`Trailing` holds its first token apart from the rest, so a value with no
leftover token cannot be built. `ParseRefusal.TrailingTokens` has the same
shape. The engine has no non-empty list type, and adding a library for one
would be a new dependency.

The `NonFatal` arm, `detailOf` and the `NonFatal` import are removed. An
exception a custom `lexer` argument throws propagates. C2's "never escape the
parser's public API" speaks of the parsers' own exceptions, and those are
still all caught.

### 5.6 Vague-query refusals — `vql/error/ParseRefusal.scala` (new), `vql/error/QueryError.scala`

```scala
package vql.error

import lexer.{Found, GrammarSymbol, LexFailure, TokenKind, WordText}
import parser.SyntaxFailure

/** What the parser was about to read when it needed a symbol. */
enum ParseContext(val label: String):
  case RangeFormula         extends ParseContext("range formula")
  case ScopeFormula         extends ParseContext("scope formula")
  case AnswerVariables      extends ParseContext("answer variables")
  case EndOfAnswerVariables extends ParseContext("end of answer variables")
  case Denominator          extends ParseContext("denominator")
  case ToleranceOrEnd       extends ParseContext("tolerance or end")

enum QuantifierPart(val label: String):
  case Numerator   extends QuantifierPart("numerator k")
  case Denominator extends QuantifierPart("denominator n")

/** Why `VagueQueryParser.parse` refused a query's text. One case per sentence template. */
enum ParseRefusal:
  case QuantifierExpected(found: List[TokenKind])
  case UnknownQuantifierOperator(found: TokenKind)
  case IntegerExpected(part: QuantifierPart, found: Found)
  case IntegerOutOfRange(part: QuantifierPart, digits: WordText)
  case FractionOutOfRange(numerator: Int, denominator: Int)
  case ToleranceOutOfRange(tolerance: Double)
  case VariableExpected(found: Found)
  case AnswerVariableExpected(found: Found)
  case SymbolExpected(expected: GrammarSymbol, before: ParseContext, found: Found)
  case TrailingTokens(first: TokenKind, rest: List[TokenKind])
  case Formula(failure: SyntaxFailure)
  case Lexical(failure: LexFailure)

  def message: String
```

`message`:

| Case | Sentence |
|---|---|
| `QuantifierExpected` | `Expected quantifier Q[op]^{k/n}, got: <labels joined by a space>` |
| `UnknownQuantifierOperator` | `Invalid quantifier operator: <label> (expected ~, >=, or <=)` |
| `IntegerExpected` | `Expected integer for <part>, got: <label>` or `Expected integer for <part>, got end of input` |
| `IntegerOutOfRange` | `Integer out of range for <part>: <digits>` |
| `VariableExpected` | `Expected variable identifier, got: <label>` or `…, got end of input` |
| `AnswerVariableExpected` | `Expected variable in list, got: <label>` or `…, got end of input` |
| `SymbolExpected` | `Expected '<expected.text>' before <before.label>, got '<label>'` or `…, got end of input` |
| `TrailingTokens` | `Unexpected tokens after query: <labels joined by a space>` |
| `Formula` | `<failure.message>` |
| `Lexical` | `Lexer error: <failure.message>` |

`QuantifierExpected.found` holds at most seven tokens. The number is the
existing literal in `parseQuantifier`: the six tokens of `Q [ op ] ^ {` and the
numerator. It becomes a named constant in `VagueQueryParser`:

```scala
/** The tokens of `Q [ op ] ^ {` and the numerator. */
private val quantifierPrefixLength: Int = 7
```

`FractionOutOfRange` and `ToleranceOutOfRange` are the two cases `TR-D-3`
adds. Their sentences are `Fraction <k>/<n> must lie in [0, 1] with a positive
denominator` and `Tolerance <tolerance> must lie in [0, 1]`. §5.11 states where
the bounds come from.

In `QueryError`:

```scala
case class ParseError(refusal: ParseRefusal, position: SourcePosition) extends QueryError:
  def message: String = refusal.message

  override def formatted: String = position match
    case SourcePosition.At(offset) => s"Parse error at position $offset: $message"
    case SourcePosition.Unknown    => s"Parse error: $message"

enum ValidationProblem:
  case QuantifiedVariableNotInRange(variable: VariableText, rangeVariables: Set[VariableText])
  case RangeVariablesNotAnswered(rangeVariables: Set[VariableText], answerVariables: List[VariableText])
  case MissingAnswerValue(variable: VariableText)
  case AnswerSortMismatch(variable: VariableText, expected: TypeId, actual: TypeId)

  def field: String
  def message: String

case class ValidationError(problem: ValidationProblem, position: SourcePosition) extends QueryError:
  def message: String = problem.message

  override def formatted: String = s"Validation error in '${problem.field}': $message"
```

`ValidationProblem.field` and `message`, word for word from 0.17.0:

| Case | `field` | `message` |
|---|---|---|
| `QuantifiedVariableNotInRange` | `quantified_variable` | `Quantified variable '<variable>' must occur free in the range formula` |
| `RangeVariablesNotAnswered` | `answer_variables` | `Range variables <rangeVariables joined by ","> must be subset of answer variables <answerVariables joined by ",">` |
| `MissingAnswerValue` | `answer_tuple` | `Missing answer tuple value for '<variable>'` |
| `AnswerSortMismatch` | `answer_tuple` | `Type mismatch for answer variable '<variable>': expected <expected>, actual <actual>` |

A sort prints as `TypeId.value`. `RangeVariablesNotAnswered.rangeVariables` is
the range's free variables without the quantified one, as today's sentence
prints them. The variables with no answer are `rangeVariables --
answerVariables`, which a consumer computes.

All four sites pass `SourcePosition.Unknown`. `ParseError.input` and both
`context` overrides are removed, so `QueryError.context` returns its default
empty map for these two cases.

### 5.7 `VagueQueryParser` — `vql/parser/VagueQueryParser.scala`

```scala
def parse(s: String): Either[QueryError, ParsedQuery] =
  try
    val tokens             = mergeDecimalTokens(Lexer.lex(StringUtil.explode(s)))
    val (query, remaining) = parseTokens(tokens)
    remaining match
      case Nil           => Right(query)
      case first :: more =>
        Left(refused(ParseRefusal.TrailingTokens(TokenKind.of(first), more.map(TokenKind.of))))
  catch
    case e: QueryException =>
      Left(e.error)
    case e: LexerError     =>
      Left(QueryError.ParseError(ParseRefusal.Lexical(e.failure), SourcePosition.At(s.length - e.remaining)))
    case e: ParseFailure   =>
      Left(refused(ParseRefusal.Formula(e.failure)))
    case e: SyntaxError    =>
      Left(refused(ParseRefusal.Formula(e.failure)))

private def refused(refusal: ParseRefusal): QueryError.ParseError =
  QueryError.ParseError(refusal, SourcePosition.Unknown)
```

The `case e: Exception` arm is removed. Each of the eleven throwing sites
throws `QueryException(refused(…))` with its case:

| Function | Case |
|---|---|
| `parseQuantifier`, operator `match` | `UnknownQuantifierOperator(TokenKind.of(opTok))` |
| `parseQuantifier`, outer `match` | `QuantifierExpected(tokens.take(quantifierPrefixLength).map(TokenKind.of))` |
| `parseVariable`, both arms | `VariableExpected(Found.of(tokens))` |
| `parseVariableList`, both arms | `AnswerVariableExpected(Found.of(tokens))` |
| `expect`, both arms | `SymbolExpected(expected, before, Found.of(tokens))` |
| `parseInteger`, `NumberFormatException` | `IntegerOutOfRange(part, num)` |
| `parseInteger`, both other arms | `IntegerExpected(part, Found.of(tokens))` |

Signatures that change:

```scala
private def expect(expected: GrammarSymbol, tokens: List[Token], before: ParseContext): List[Token]
private def parseInteger(tokens: List[Token], part: QuantifierPart): (Int, List[Token])
```

`expect` compares `TokenKind.of(head)` with `TokenKind.Symbol(expected)`. Its
six call sites pass `(LParen, RangeFormula)`, `(Comma, ScopeFormula)`,
`(RParen, AnswerVariables)`, `(RParen, EndOfAnswerVariables)`,
`(Divide, Denominator)` and `(RBrace, ToleranceOrEnd)`.

Each pair of arms that differed only in "end of input" becomes one arm, because
`Found.of` covers both. `opLabel` stays for the accepted-operator `match`.
The import of `tokensLabel` is removed. The import of `tokenLabel` stays for
`opLabel`.

### 5.8 Bind errors, declared once — `vql/error/TypeCheckError.scala` (moved), `vql/error/QueryError.scala`

`TypeCheckError` moves from `vql.typed` to `vql.error` and is the one
declaration of a bind error (`TR-D-8`). Every name carries a type (`TR-D-5`).
The mismatch site and the symbol kind are the fields `TR-D-7` ruled.

```scala
package vql.error

import lexer.{LiteralText, SymbolText, VariableText}
import vql.id.TypeId

/** The term whose sort did not match. */
enum MismatchSite:
  case Variable(name: VariableText)
  case Constant(name: LiteralText)
  case FunctionReturn(name: SymbolText)

  def label: String = this match
    case Variable(name)       => s"variable '${name.value}'"
    case Constant(name)       => s"constant '${name.value}'"
    case FunctionReturn(name) => s"function '${name.value}' return"

/** What kind of catalog symbol a bind error names. */
enum SymbolKind:
  case Predicate
  case Function

/** Why `QueryBinder` refused a query or a satisfying-set formula. */
enum TypeCheckError:
  case UnknownPredicate(name: SymbolText)
  case UnknownFunction(name: SymbolText)
  case ArityMismatch(symbol: SymbolText, kind: SymbolKind, expected: Int, actual: Int)
  case UnknownConstantOrLiteral(name: LiteralText)
  case TypeMismatch(expected: TypeId, actual: TypeId, site: MismatchSite)
  case UnboundAnswerVar(name: VariableText)
  case UnconstrainedVar(name: VariableText)
  case ConflictingTypes(name: VariableText, left: TypeId, right: TypeId)
  case UnparseableConstant(name: LiteralText, sort: TypeId, sourceText: LiteralText)
  case TypeNotQuantifiable(sort: TypeId)
  case UnexpectedFreeVar(name: VariableText)

  def message: String
```

The existing scaladoc of `UnparseableConstant`, `TypeNotQuantifiable` and
`UnexpectedFreeVar` moves with the cases. `TypeNotQuantifiable.name: String`
becomes `sort: TypeId`: the field held `TypeId.value`.

`message`, word for word from `VagueSemantics.renderTypeError` at 0.17.0. A
name prints as its `.value` and a sort as `TypeId.value`:

| Case | Sentence |
|---|---|
| `UnknownPredicate` | `unknown predicate: <name>` |
| `UnknownFunction` | `unknown function: <name>` |
| `ArityMismatch` | `arity mismatch for '<symbol>': expected <expected>, actual <actual>` |
| `UnknownConstantOrLiteral` | `unknown constant or literal: <name>` |
| `TypeMismatch` | `type mismatch in <site.label>: expected <expected>, actual <actual>` |
| `UnboundAnswerVar` | `unbound answer variable: <name>` |
| `UnconstrainedVar` | `unconstrained quantifier variable: <name>` |
| `ConflictingTypes` | `conflicting inferred types for '<name>': <left> vs <right>` |
| `UnparseableConstant` | `cannot parse '<sourceText>' as <sort>` |
| `TypeNotQuantifiable` | `type '<sort>' is not a domain type and cannot be quantified over` |
| `UnexpectedFreeVar` | `unexpected free variable '<name>': a satisfying-set formula may only mention its one variable` |

`ArityMismatch`'s sentence does not print the kind, so it is byte-identical.

In `QueryError`, four cases change. `vql/error/QueryError.scala` gains the
imports `lexer.{LiteralText, VariableText}`, `parser.SourcePosition` and
`vql.id.{SymbolName, TypeId}`.

```scala
case class BindError(details: List[TypeCheckError]) extends QueryError:
  def messages: List[String] = details.map(_.message)
  def message                = s"Query type-checking failed: ${messages.mkString("; ")}"
  override val context       = Map("errors" -> messages.mkString("; "))

case class UnknownConstantOrLiteralError(name: LiteralText) extends QueryError:
  def message          = s"Unknown constant or literal: '${name.value}'"
  override val context = Map("constant" -> name.value)

case class DomainNotFoundError(typeName: TypeId, availableTypes: Set[TypeId]) extends QueryError:
  def message = s"No domain found for type '${typeName.value}'"

  override val context = Map(
    "type"      -> typeName.value,
    "available" -> availableTypes.map(_.value).mkString(", "),
  )

  override def formatted: String =
    s"No domain found for type '${typeName.value}'. " +
      s"Available types with domains: ${availableTypes.map(_.value).mkString(", ")}"

case class UnboundVariableError(variableName: VariableText, boundVariables: Set[String]) extends QueryError:
  def message = s"Unbound variable: '${variableName.value}'"

  override val context = Map(
    "variable"        -> variableName.value,
    "bound_variables" -> boundVariables.mkString(", "),
  )
```

`UnknownConstantOrLiteralError`, `DomainNotFoundError` and
`UnboundVariableError` were not named by the earlier rulings. They change here
because `TR-D-5` types every name a refusal reads and `TR-D-8` makes every
sort a `TypeId`. Each sentence is byte-identical.

**What is removed.** The enum `BindErrorDetail`. The private methods
`VagueSemantics.toBindErrorDetail` and `VagueSemantics.renderTypeError`. The
file `core/src/main/scala/vql/typed/TypeCheckError.scala`.

**The sites.** `VagueSemantics.bindTyped` builds `QueryError.BindError(details
= errors)`, and keeps its one special case, which now builds
`UnknownConstantOrLiteralError` from the first `LiteralText`.
`VagueSemantics.satisfyingSet` builds `BindError(details = errors)`.

`QueryBinder` types each name where it builds an error: `VariableText.of(name)`
for a variable, `SymbolText.of(name)` for a predicate or function, and
`LiteralText(name)` for a constant. None can throw, because §5.13 checks the
same name when the tree is built. Its three mismatch sites pass
`MismatchSite.Variable`, `MismatchSite.Constant` and
`MismatchSite.FunctionReturn`. It raises an arity mismatch at two sites. The
site that binds an atom passes `SymbolKind.Predicate`, and the site that binds
a function application passes `SymbolKind.Function`. At 0.17.0 both write only
the name, so a consumer cannot tell the two apart. Its three
`TypeNotQuantifiable` sites pass the sort itself.

`QueryBinder.scala` and `VagueSemantics.scala` import
`vql.error.TypeCheckError`. `MapDispatcherSpec`, `QueryBinderSpec` and
`SatisfyingSetSpec` name the type and gain the same import.
`VagueSemanticsTypedSpec` imports `BindErrorDetail` today and imports
`TypeCheckError` in its place.

### 5.9 The family of each new type

| New type | Family it joins | Existing member | Existing type for the same concept |
|---|---|---|---|
| `WordText`, `DecimalText`, `LiteralText` | the opaque types built through a `require` | `opaque type TypeId` in `vql/typed/TypeDefs.scala` at 0.17.0 | none |
| `VariableText`, `SymbolText` | the lexer's token types in `lexer/` | `enum Token` in `lexer/Token.scala` | none. A tree holds the same text as a `String` |
| `GrammarSymbol`, `TokenKind`, `Found` | the lexer's token types in `lexer/` | `enum Token` in `lexer/Token.scala` | none. `Token` holds unbounded text in `OpSym`, which a refusal must not carry |
| `LexFailure`, `SyntaxFailure` | the foundation's failure enums | `enum ParseError` in `parser/ParseError.scala` | none. Each restates message strings that no type holds today |
| `SyntaxError` | the foundation's parser exceptions | `class ParseFailure` in `parser/Combinators.scala` | none. `ParseFailure` is caught by backtracking, and these five sites must not be |
| `SourcePosition` | the foundation's failure enums | `enum ParseError` | replaces `QueryError.ParseError.position: Option[Int]` |
| `ParseRefusal`, `ValidationProblem`, `EvaluationFailure`, `MismatchSite`, `SymbolKind` | the per-error enums in `vql/error/` | `enum TypeCheckError`, which this plan moves there | none |
| `ParseContext`, `QuantifierPart` | the same | `enum TypeCheckError` | none. Each replaces a set of string arguments to `expect` and `parseInteger` |
| `Term.mkVar`, `Term.mkFn`, `Term.mkConst` | the `mk*` building functions | `Formula.mkForall` in `logic/Formula.scala`, `Quantifier.mkAbout` in `vql/quantifier/Quantifier.scala` | none |

`TypeCheckError`, `RuntimeModelError`, `TypeId` and `SymbolName` are existing
types that move. No new type denotes what they denote.

**Reachability under ADR-004.** `lexer` imports `util`, as today. `logic`
imports `lexer`, its first import. `parser` imports `lexer` and `logic`, as
today. `vql.id` imports nothing. `vql.error` imports `lexer`, `parser` and
`vql.id`, its first imports. `vql.typed` imports `vql.error`, as `FolModel`
and `TypedSemantics` do today, and `vql.id`. No foundation package imports
`vql.*`, and `vql.error` imports nothing from `vql.typed`.

**Algebraic structure.** `TokenKind.of`, `Found.of`, each `admits*`, each `of`
and each `message` are transformations of one value. Nothing is aggregated or
sequenced.

**The remaining `String` fields.**

- `Term.Var.name`, `Term.Fn.name`, `Term.Const.name`, `FOL.predicate`,
  `Formula.Forall.x`, `Formula.Exists.x`, `ParsedQuery.variable`,
  `ParsedQuery.answerVars` and `BoundVar.name` stay `String`. Each is checked
  when its value is built (§5.13). A typed field there is the change listed
  under "Not in scope".
- The `name` parameters of the building functions are `String` for the same
  reason: each function is the check.
- `UnboundVariableError.boundVariables` is a `Set[String]`. Its members are
  the names bound at that point of evaluation, which include the keys of the
  caller's answer tuple. The engine places no constraint on those keys, so no
  type narrower than `String` holds them. It is the one refusal field that
  holds caller text as a `String`.
- `dispatcherMessage` is the text a consumer's dispatcher returned through
  `Either[String, _]` (ADR-015).
- Every `message`, `label`, `field` and `phase` is a sentence or a fixed word.

`SourcePosition.At.offset` and the fraction's two numbers are `Int`, the type
the parser already computes them in. `ToleranceOutOfRange.tolerance` is the
parsed `Double`. No opaque type in the codebase denotes an offset or a count.

### 5.10 Sentences that differ from 0.17.0

1. A formula failure inside a vague query loses the prefix `Unexpected error
   during parsing: `. `parse_atom` is reported as `parse_atom`.
2. An unknown operator run prints as `unknown operator`, and a stray character
   as `unexpected character`, where 0.17.0 printed the characters.
3. `ParseError.formatted` has no `Input:` line and no context lines.
   `ValidationError.formatted` has no context lines.
4. The refused-literal-character sentence is new.
5. The two quantifier sentences of §5.6 are new.
6. The variable-rule sentence joins its variables in the order of a
   `Set[VariableText]`. For more than four variables that order can differ
   from the order of the `Set[String]` at 0.17.0.

### 5.11 Quantifier bounds — `vql/quantifier/Quantifier.scala`, `vql/parser/VagueQueryParser.scala`

**Where the bounds come from.** `docs/VagueQuantifiers.md` defines the
threshold `k/n` as the "Target proportion" and the proportion as
`Prop_D(S, φ) = |{s ∈ S | KB ⊨ φ(s, c)}| / |S|`. A proportion is the size of a
subset over the size of its set, so it lies in [0, 1]. A target for it has a
meaning only in the same interval, which requires `n > 0` and `0 ≤ k ≤ n`.
The tolerance ε is the "Allowed deviation" from that target. Two proportions
differ by at most 1, so ε above 1 accepts everything and ε below 0 accepts
nothing. The interval [0, 1] for ε is this plan's derivation from that, and it
is the bound `Quantifier.mkAbout` already checks.

**The type.** The case constructors are closed to the package, so every
`Quantifier` outside it was built by `mkAbout`, `mkAtLeast` or `mkAtMost`.
Pattern matching on the cases is unchanged.

```scala
enum Quantifier:
  case About private[quantifier] (k: Int, n: Int, tolerance: Double)
  case AtLeast private[quantifier] (k: Int, n: Int, tolerance: Double)
  case AtMost private[quantifier] (k: Int, n: Int, tolerance: Double)
```

A probe compiled against Scala 3.8.4 on 2026-11-11 confirmed the three effects
outside the package: `Q.About(5, 3, 0.1)` does not compile, `new Q.About(…)`
does not compile, `copy` does not compile, and `case Q.About(k, n, t)` matches.

**The guard.** `parseQuantifier` checks the numbers it read before it builds
the value, and throws the typed refusal. The `require`s in `mk*` stay as the
second check ADR-012 §3 allows.

```scala
private def checkedQuantifier(k: Int, n: Int, tolerance: Double, build: (Int, Int, Double) => Quantifier): Quantifier
```

It throws `QueryException(refused(ParseRefusal.FractionOutOfRange(k, n)))`
when `n <= 0 || k < 0 || k > n`, then
`QueryException(refused(ParseRefusal.ToleranceOutOfRange(tolerance)))` when
`tolerance < 0 || tolerance > 1`, and otherwise returns `build(k, n,
tolerance)`. The three operator arms pass `Quantifier.mkAbout`,
`Quantifier.mkAtLeast` and `Quantifier.mkAtMost`.

**Examples checked.** A scan on 2026-11-11 of every quantifier literal in
source, tests and documents found none out of range: 119 in this repository
outside this plan, and 165 in register. The scan reads literal text, so a
quantifier assembled at run time is not covered.

### 5.12 Model and evaluation failures — `vql/error/RuntimeModelError.scala` (moved), `vql/error/QueryError.scala`, `vql/typed/FolModel.scala`, `vql/typed/RuntimeModel.scala`, `vql/typed/TypedSemantics.scala`

`RuntimeModelError` moves from `vql.typed` to `vql.error` with its three
cases, its fields and its `message` unchanged (`TR-D-8`). It imports
`vql.id.{SymbolName, TypeId}`.

```scala
// vql/error/RuntimeModelError.scala
package vql.error

import vql.id.{SymbolName, TypeId}

enum RuntimeModelError:
  case MissingFunctionImplementation(name: SymbolName)
  case MissingPredicateImplementation(name: SymbolName)
  case MissingDomainForType(typeName: TypeId)

  def message: String = this match
    case MissingFunctionImplementation(n)  => s"missing function: ${n.value}"
    case MissingPredicateImplementation(n) => s"missing predicate: ${n.value}"
    case MissingDomainForType(t)           => s"missing domain for type: ${t.value}"

// vql/error/QueryError.scala
case class ModelValidationError(details: List[RuntimeModelError]) extends QueryError:
  def errors: List[String] = details.map(_.message)
  def message              = s"Runtime model validation failed: ${errors.mkString("; ")}"
  override val context     = Map("errors" -> errors.mkString("; "))

/** Why evaluation of a bound query stopped. */
enum EvaluationFailure:
  /** The dispatcher refused a predicate; `dispatcherMessage` is its own text. */
  case PredicateRefused(name: SymbolName, dispatcherMessage: String)
  /** The dispatcher refused a function; `dispatcherMessage` is its own text. */
  case FunctionRefused(name: SymbolName, dispatcherMessage: String)
  case VariableSortMismatch(variable: VariableText, expected: TypeId, actual: TypeId)

  def phase: String = this match
    case PredicateRefused(name, _)     => s"predicate:${name.value}"
    case FunctionRefused(name, _)      => s"function:${name.value}"
    case VariableSortMismatch(_, _, _) => "typed_term"

  def message: String = this match
    case PredicateRefused(_, dispatcherMessage) => dispatcherMessage
    case FunctionRefused(_, dispatcherMessage)  => dispatcherMessage
    case VariableSortMismatch(variable, expected, actual) =>
      s"Variable '${variable.value}' has type ${actual.value}, expected ${expected.value}"

case class EvaluationError(failure: EvaluationFailure) extends QueryError:
  def message: String = failure.message

  override def formatted: String = s"Evaluation error in ${failure.phase}: $message"
```

A refused predicate or function is named by its `SymbolName`, the type the
bound atom already holds. No conversion from text is needed there.

**The sites.** `FolModel.apply` builds `ModelValidationError(details =
errors)`. `RuntimeModel.scala` imports `vql.error.RuntimeModelError`. In
`TypedSemantics`:

| Site | Builds |
|---|---|
| `evalAtom` | `EvaluationError(PredicateRefused(atom.name, msg))` |
| `evalTerm`, function application | `EvaluationError(FunctionRefused(name, msg))` |
| `evalTerm`, variable of the wrong sort | `EvaluationError(VariableSortMismatch(VariableText.of(v.name), v.sort, value.sort))` |
| `evalTerm`, variable with no value | `UnboundVariableError(VariableText.of(v.name), env.keySet)` |
| the four `DomainNotFoundError` sites | `DomainNotFoundError(typeName = sort, availableTypes = model.domains.keySet)` |
| `validateAnswerTuple`, missing value | `ValidationError(MissingAnswerValue(VariableText.of(boundVar.name)), SourcePosition.Unknown)` |
| `validateAnswerTuple`, wrong sort | `ValidationError(AnswerSortMismatch(VariableText.of(boundVar.name), boundVar.sort, v.sort), SourcePosition.Unknown)` |

No `VariableText.of` here can throw, because `BoundVar` checks its name when
it is built (§5.13).

Every `message`, `phase` and `formatted` string is byte-identical to 0.17.0.
`EvaluationError.cause` and its `context` parameter are removed. No site
passes either.

**What is removed.** The file
`core/src/main/scala/vql/typed/RuntimeModelError.scala`.

### 5.13 Building a tree — `logic/Term.scala`, `logic/FOL.scala`, `logic/Formula.scala`, `logic/FOLUtil.scala`

**The rule.** A name in a tree is one that query text can produce. The case
constructors that hold a name are closed to the package `logic`, and the
functions that replace them check the name (`TR-D-9`). The field types are
unchanged, and so is every pattern match.

**What query text can produce.** Read from the parsers at 0.17.0:

| Name in the tree | Comes from | Predicate that admits it |
|---|---|---|
| a variable | a `Token.Word`: a word, or a merged decimal such as the bound variable in `forall 0.5 . p(0.5)` | `VariableText.admits` |
| a constant | an all-digit word, `nil`, a merged decimal, or a quoted literal's content | `LiteralText.admits` |
| a predicate | a `Token.Word`, or one of `=`, `<`, `<=`, `>`, `>=` | `SymbolText.admitsPredicate` |
| a function | a `Token.Word`, or one of `+`, `-`, `*`, `/`, `^`, `::` | `SymbolText.admitsFunction` |

Every constant fits `LiteralText`, because digits, `nil` and a decimal use only
characters of the literal alphabet.

**The types.**

```scala
// logic/Term.scala
enum Term:
  case Var private[logic] (name: String)
  case Fn private[logic] (name: String, args: List[Term])
  case Const private[logic] (name: String)

object Term:
  /** A variable. Throws `IllegalArgumentException` for a name no query text can produce. */
  def mkVar(name: String): Term =
    require(VariableText.admits(name), "A variable name must be a word or a decimal")
    Var(name)

  /** A function application. Throws `IllegalArgumentException` for a name no query text can produce. */
  def mkFn(name: String, args: List[Term]): Term =
    require(SymbolText.admitsFunction(name), "A function name must be a word, a decimal or a term operator")
    Fn(name, args)

  /** A constant. Throws `IllegalArgumentException` for text outside the literal alphabet. */
  def mkConst(name: String): Term =
    require(LiteralText.admits(name), "A constant must stay within the literal alphabet")
    Const(name)

// logic/FOL.scala
case class FOL private[logic] (predicate: String, terms: List[Term])

object FOL:
  /** An atom. Throws `IllegalArgumentException` for a predicate name no query text can produce. */
  def apply(predicate: String, terms: List[Term]): FOL =
    require(SymbolText.admitsPredicate(predicate), "A predicate name must be a word, a decimal or a relation")
    new FOL(predicate, terms)

  /** Convenience constructor */
  def apply(pred: String, terms: Term*): FOL =
    FOL(pred, terms.toList)

// logic/Formula.scala
enum Formula[+A]:
  // False, True, Atom, Not, And, Or, Imp and Iff are unchanged
  case Forall private[logic] (x: String, p: Formula[A])
  case Exists private[logic] (x: String, p: Formula[A])

object Formula:
  /** Throws `IllegalArgumentException` for a variable name no query text can produce. */
  def mkForall[A](x: String, p: Formula[A]): Formula[A] =
    require(VariableText.admits(x), "A variable name must be a word or a decimal")
    Forall(x, p)

  /** Throws `IllegalArgumentException` for a variable name no query text can produce. */
  def mkExists[A](x: String, p: Formula[A]): Formula[A] =
    require(VariableText.admits(x), "A variable name must be a word or a decimal")
    Exists(x, p)
```

Each `require` is a construction invariant under ADR-012 §1. `FOL` keeps its
call syntax: `FOL("p", List(x))` resolves to the checked `apply`. The cases
of `Formula` that hold no name keep public constructors.

A probe compiled against Scala 3.8.4 on 2026-11-12, on both platforms,
confirmed the effects outside the package for these shapes. `Term.Var("x")`,
`new Term.Var("x")`, `copy` on a `Var`, `new FOL("p", Nil)`, `copy` on a
`FOL` and `Formula.Forall("x", True)` do not compile. The building functions
compile, and one `match` over `Forall`, `And`, `Atom`, `FOL`, `Fn`, `Var` and
`Const` binds every field.

**Three more places where a name enters.** None of them is a tree, and each
gets the same check.

```scala
// vql/logic/ParsedQuery.scala — first statement of the class body
require(
  (variable :: answerVars).forall(VariableText.admits),
  "A query's variable names must be words or decimals",
)

// vql/typed/BoundQuery.scala
case class BoundVar(name: String, sort: TypeId):
  require(VariableText.admits(name), "A variable name must be a word or a decimal")

// vql/typed/QueryBinder.scala — first statement of bindSatisfyingFormula
require(VariableText.admits(variable), "A variable name must be a word or a decimal")
```

`VagueSemantics.satisfyingSet` passes its `variable` to
`bindSatisfyingFormula`, so the one check covers both entries. No signature
changes at these three places.

**The construction sites.**

| File | Change |
|---|---|
| `parser/TermParser.scala` | `Fn(…)` becomes `Term.mkFn(…)`, `Var(a)` becomes `Term.mkVar(a)`, `Term.Const(…)` becomes `Term.mkConst(…)` |
| `parser/FormulaParser.scala` | the two quantifier functions passed to `parseQuant` call `mkForall` and `mkExists` |
| `parser/FOLAtomParser.scala` | not edited for this: `FOL(p, args)` resolves to the checked `apply` |
| `logic/FOLUtil.scala` | `generalize` calls `Formula.mkForall`. `subst` passes `Formula.mkForall` and `Formula.mkExists` to `substQuant`, which calls `Term.mkVar`. The OCaml source has `mk_forall` and `mk_exists` at the same three places |
| `vql/logic/ParsedQuery.scala`, `examples/FOLDemo.scala` | the examples call the building functions |

The scaladoc of each ported function keeps its OCaml source (C11).

**What throws, and when.**

1. A building function, `ParsedQuery`, `BoundVar` or `bindSatisfyingFormula`
   given a name no query text can produce throws `IllegalArgumentException`
   on that call.
2. A tree the parser returns never throws, because every name in it came from
   a token. A token from a custom lexer handed to `FOLParser.parseWithLexer`
   passes only if that lexer keeps the same character classes, and a
   violation is a defect of that lexer.
3. `FOLUtil.subst` renames a bound variable by appending `'`. A decimal is a
   variable name, and a decimal with `'` appended is neither a word nor a
   decimal. So a substitution that has to rename a decimal-named bound
   variable throws `IllegalArgumentException`. The query pipeline never calls
   `subst`.

### 5.14 The identifier package — `vql/id/Identifiers.scala` (new), `vql/typed/TypeDefs.scala`

`TypeId` and `SymbolName` move, unchanged, to a package that imports nothing
(`TR-D-8`).

```scala
// vql/id/Identifiers.scala
package vql.id

opaque type TypeId = String

object TypeId:

  def apply(value: String): TypeId =
    require(value.nonEmpty, "TypeId must be non-empty")
    value

  extension (s: TypeId) def value: String = s

opaque type SymbolName = String

object SymbolName:

  def apply(value: String): SymbolName =
    require(value.nonEmpty, "SymbolName must be non-empty")
    value

  extension (s: SymbolName) def value: String = s

// vql/typed/TypeDefs.scala — in place of the two declarations
type TypeId = vql.id.TypeId

object TypeId:
  def apply(value: String): TypeId = vql.id.TypeId(value)

type SymbolName = vql.id.SymbolName

object SymbolName:
  def apply(value: String): SymbolName = vql.id.SymbolName(value)
```

The alias and its companion make both names members of `vql.typed`, so every
file in that package and every consumer import of `vql.typed.TypeId` or
`vql.typed.SymbolName` resolves as before. `.value` resolves through the
companion in `vql.id`. `TypeDecl` stays in `TypeDefs.scala`. Both types keep
`String` in `apply`: each is the check that turns a string into an identifier.

**Ruled 2026-11-12, during Phase 1: Option A of a deviation.** The plan first
specified `export vql.id.{SymbolName, TypeId}`. Through the exported
companion, an inferred `SymbolName(s)` is typed as `String`, so
`extensions.keys.map(SymbolName(_)).toSet` in `examples/VagueDemo.scala` did
not compile. A probe on 2026-11-12 showed that the alias with a forwarding
`apply` infers the identifier type. The user ruled this form over removing the
old path.

---

## 6. Implementation phases

Each phase compiles and passes the full suite on both platforms before the
next starts. The plan ships whole, as one release.

**Phase 1 — the identifier package.**
1. `vql/id/Identifiers.scala`; the aliases and forwarding companions in
   `vql/typed/TypeDefs.scala` (§5.14).
2. Tests: `TypeCatalogSpec` gains one test that a `vql.typed.TypeId` is a
   `vql.id.TypeId`. No other file changes, and the unchanged suite shows the
   old path resolves.

**Phase 2 — foundation refusal types.**
1. `lexer/TokenKind.scala`: `GrammarSymbol`, `TokenKind`, `Found`;
   `lexer/TokenText.scala`: the three text types and the two name enums
   (§5.1).
2. `lexer/LexFailure.scala`; `lexer/Lexer.scala`: `LexerError`, `literalChars`,
   the new `lexStringLit` arm (§5.2).
3. `parser/SourcePosition.scala` (§5.3).
4. `parser/SyntaxFailure.scala`; `parser/Combinators.scala`: the two exception
   classes and `parseBracketed`; `FormulaParser`, `FOLAtomParser`, `TermParser`:
   the throw sites (§5.4).
5. `parser/ParseError.scala`, `parser/FOLParser.scala` (§5.5).
6. Tests: `TokenKindSpec` (new), `SyntaxFailureSpec` (new), `LexerSpec`,
   `FOLParserSpec`, `FormulaParserSpec` line 33, which builds a `ParseFailure`,
   and `TermParserSpec` line 406, which gains an assertion on `failure`.

`VagueQueryParser` still compiles after this phase, because its `LexerError`
arm reads `getMessage` and its last arm catches `Exception`.

**Phase 3 — tree building.**
1. `logic/Term.scala`, `logic/FOL.scala`, `logic/Formula.scala`,
   `logic/FOLUtil.scala` (§5.13).
2. `parser/TermParser.scala`, `parser/FormulaParser.scala`: the construction
   sites.
3. `vql/logic/ParsedQuery.scala`: the class-body check and the three examples.
   `vql/typed/BoundQuery.scala`: the `BoundVar` check.
   `vql/typed/QueryBinder.scala`: the check in `bindSatisfyingFormula`.
4. `examples/FOLDemo.scala`.
5. Tests: the fourteen test files outside the package `logic` that build
   trees call the building functions. Each expected value is the same tree.
   `TermSpec`, `FOLSpec`, `FormulaSpec`, `FOLUtilSpec` and `ParsedQuerySpec`
   gain the tests of §7.

**Phase 4 — errors declared once.**
1. `vql/error/TypeCheckError.scala` and `vql/error/RuntimeModelError.scala`
   are created from the two files in `vql/typed/`, which are deleted. In this
   phase `TypeCheckError` keeps its 0.17.0 fields, gains `message`, and
   `TypeNotQuantifiable` holds a `TypeId`.
2. `vql/error/QueryError.scala`: `BindErrorDetail` is removed; `BindError`,
   `ModelValidationError` and `DomainNotFoundError` as in §5.8 and §5.12.
3. `vql/semantics/VagueSemantics.scala`: `toBindErrorDetail` and
   `renderTypeError` are removed. `vql/typed/FolModel.scala`,
   `vql/typed/RuntimeModel.scala`, `vql/typed/QueryBinder.scala`,
   `vql/typed/TypedSemantics.scala`: the imports and the sites.
   `vql/fragment/Fragment.scala`: one scaladoc line names the type's package.
4. Tests: `QueryBinderSpec`, `MapDispatcherSpec`, `SatisfyingSetSpec`,
   `VagueSemanticsTypedSpec`.

**Phase 5 — typed refusals in the vague layer.**
1. `vql/error/ParseRefusal.scala`; `QueryError.ParseError`,
   `ValidationProblem`, `QueryError.ValidationError` (§5.6).
2. `vql/parser/VagueQueryParser.scala` (§5.7).
3. `vql/logic/ParsedQuery.scala`: the two rules throw
   `QueryException(ValidationError(problem, SourcePosition.Unknown))`.
4. `vql/quantifier/Quantifier.scala` and `parseQuantifier` (§5.11).
5. `TypeCheckError`: the typed names, `MismatchSite` and `SymbolKind`;
   `UnknownConstantOrLiteralError` and `UnboundVariableError`;
   `QueryBinder.scala` and `VagueSemantics.scala`: the sites (§5.8).
6. `EvaluationFailure`, `EvaluationError` and every site in
   `TypedSemantics.scala` (§5.12).
7. Tests: `VagueQueryParserSpec`, `ParsedQuerySpec`, `QuantifierSpec`,
   `QueryBinderSpec`, `MapDispatcherSpec`, `SatisfyingSetSpec` and
   `VagueSemanticsTypedSpec`. The tests that build a `Quantifier` case
   directly build it through `mk*`: `QuantifierSpec`, thirteen sites in
   `VagueSemanticsTypedSpec`, three in `CompoundRangeSpec` and one in
   `MapDispatcherSpec`. Their assertions are unchanged.

**Phase 6 — documents and release preparation.**
1. `docs/ADR-022.md` (new): typed refusals. It records the closed sets, the
   text types, the literal alphabet, the position type and the two boundaries
   that convert only the parsers' own failures.
2. `docs/ADR-023.md` (new): errors declared once above a shared identifier
   package. It records `vql.id`, the import directions of §5.9, and that it
   supersedes ADR-019 and ADR-021.
3. The ADR amendments of `TR-D-4`. ADR-007 C8 is reworded to the text of
   `TR-D-9`, "What C8 keeps", and its review criterion gains a note for the
   typed throws of `TR-D-1`. Both record the user's ruling.
4. `CHANGELOG.md` 0.18.0; `build.sbt` version.
5. `docs/Architecture.md`: the package map gains `id/`, lists
   `TypeCheckError` and `RuntimeModelError` under `error/`, and line 126's
   description of the `ParseError` mapping is brought to the new shape. Its
   record table gains ADR-022 and ADR-023. The scaladoc of `FragmentCheck`
   describes the same mapping.
6. `docs/TODOS.md`: the entry near line 235 that describes
   `List[BindErrorDetail]` and a `ModelValidationError` of `List[String]` is
   brought to the state this plan leaves.
7. `docs/scratch/register-typed-refusals-<date>.md` (new): the handoff note.
   It lists every engine case with its fields and the content type of each
   text field, the input each case is attributable to, and the sentences of
   §5.10. Under headings of their own it states:
   - before the case list, that the user overruled including the apostrophe
     in the literal alphabet on 2026-11-11: a literal's content is
     `[A-Za-z0-9 /_.+-]*`, and a register type for a literal's content that
     admits `'` is wider than what the engine can send;
   - that a quantifier outside the bounds of §5.11 is now refused, that an
     example or test encoding one has no mathematical meaning and needs
     correcting, not the engine, and that register should run its integration
     tests against 0.18.0 and read each failure against that intent, with the
     scan result of §5.11;
   - that a predicate or function name can be an operator's text (§9);
   - that a bind error is a `TypeCheckError` on both of register's paths, with
     the import lines that change.
8. `PLAN-refusal-positions.md`: its §3 is brought to the state this plan
   leaves, and its §4 item 4 names the closed constructors beside C8.
9. `.claude/skills/vql-adr-constraints/SKILL.md`: ADR-022 and ADR-023 are
   placed in all four layers. Layer 2 gains their rows and marks ADR-019 and
   ADR-021 superseded. Layer 1 names ADR-022 under "Editing a file in the
   OCaml-ported foundation", "Changing the vague query grammar or
   `VagueQueryParser`" and "Adding or changing a failure, `QueryError` or
   bind-error detail". It names ADR-023 wherever it names ADR-019 or ADR-021
   today, in their place. Layer 3 gains the rows ADR-007 C12 × ADR-022 and
   ADR-004 × ADR-023, and loses the three rows that name ADR-019. Layer 4's
   second row names `TypeCheckError.UnparseableConstant.sort`, and Layer 4
   gains the row below.
10. `CLAUDE.md` at the repository root states three rules this plan changes.
    It is the user's gate file, so the implementing session presents the
    replacements below and the user applies them.

| `CLAUDE.md` passage | Replacement |
|---|---|
| "A primitive crossing into `vql.error` is the sanctioned case under ADR-019, and says so." | "A sort or a symbol name in `vql.error` is a `TypeId` or a `SymbolName` from `vql.id` (ADR-023)." |
| "It crosses into `vql.error` only as `TypeId.value` (ADR-019)." | "Both are declared in `vql.id`, below `vql.error` and `vql.typed` (ADR-023)." |
| "**The foundation never imports `vql.*`, and `vql.error` never imports `vql.typed`** (ADR-004, ADR-019)." | "**The foundation never imports `vql.*`, and `vql.error` never imports `vql.typed`** (ADR-004, ADR-023)." |

### Register records this plan bears on

- Register's ADR-035 (error leakage) gains a section on what of the caller's
  query text a refusal may carry. Register's own plan writes it in its step
  5.7, and that section depends on the literal alphabet and the typed cases
  of ADR-022. This is the new Layer 4 row.
- Register's ADR-028 appendix §5 decides `UnknownReferences` by reading the
  sort of an unparseable constant. The field it reads moves from
  `BindErrorDetail.UnparseableConstant.sortName` to
  `TypeCheckError.UnparseableConstant.sort`. The record's text states the
  meaning and stays true.
- Register's ADR-029 §3 rests on the binder's literal branch, which this plan
  does not edit.
- What register must do is step 5.7 of its plan
  `docs/dev/plans/PLAN-UES-UNIFIED-ERROR-SHAPE.md`, which already names this
  plan. Nothing further is written into register's task list.

### Consumer-document impact

- `docs/Architecture.md`: the passages Phase 6 item 5 names. Its description
  of the parse step also gains the rule of §5.13: a tree is built through the
  building functions, and each name is one query text can produce.
- The scaladoc of `QueryError`, `parser.ParseError`, `LexerError`,
  `Combinators.ParseFailure`, `FOLParser`, `Lexer.lexStringLit` and
  `FragmentCheck` describes the shapes this plan replaces and is rewritten
  with them.
- The scaladoc of `QueryError.BindError`, `QueryError.ModelValidationError`,
  `FolModel` and `VagueSemantics` describes the primitives-only copy and the
  fold, and is rewritten.
- The scaladoc of `Term`, `FOL`, `Formula`, `ParsedQuery`, `BoundVar` and
  `QueryBinder.bindSatisfyingFormula` states the name check and its
  exception. The comment on `Formula`'s `mk*` functions that calls them
  temporary is replaced by what they do.
- `README.md` and `docs/VagueQuantifiers.md` name none of the changed types.

---

## File inventory

Created:

- `core/src/main/scala/vql/id/Identifiers.scala`
- `core/src/main/scala/lexer/TokenKind.scala`
- `core/src/main/scala/lexer/TokenText.scala`
- `core/src/main/scala/lexer/LexFailure.scala`
- `core/src/main/scala/parser/SourcePosition.scala`
- `core/src/main/scala/parser/SyntaxFailure.scala`
- `core/src/main/scala/vql/error/ParseRefusal.scala`
- `core/src/main/scala/vql/error/TypeCheckError.scala`
- `core/src/main/scala/vql/error/RuntimeModelError.scala`
- `core/src/test/scala/lexer/TokenKindSpec.scala`
- `core/src/test/scala/parser/SyntaxFailureSpec.scala`
- `docs/ADR-022.md`
- `docs/ADR-023.md`
- `docs/scratch/register-typed-refusals-<date>.md`, the date being the day it is written

Deleted:

- `core/src/main/scala/vql/typed/TypeCheckError.scala`
- `core/src/main/scala/vql/typed/RuntimeModelError.scala`

Modified, source:

- `core/src/main/scala/lexer/Lexer.scala`
- `core/src/main/scala/logic/Term.scala`
- `core/src/main/scala/logic/FOL.scala`
- `core/src/main/scala/logic/Formula.scala`
- `core/src/main/scala/logic/FOLUtil.scala`
- `core/src/main/scala/parser/Combinators.scala`
- `core/src/main/scala/parser/FormulaParser.scala`
- `core/src/main/scala/parser/FOLAtomParser.scala`
- `core/src/main/scala/parser/TermParser.scala`
- `core/src/main/scala/parser/ParseError.scala`
- `core/src/main/scala/parser/FOLParser.scala`
- `core/src/main/scala/vql/error/QueryError.scala`
- `core/src/main/scala/vql/parser/VagueQueryParser.scala`
- `core/src/main/scala/vql/logic/ParsedQuery.scala`
- `core/src/main/scala/vql/quantifier/Quantifier.scala`
- `core/src/main/scala/vql/typed/TypeDefs.scala`
- `core/src/main/scala/vql/typed/BoundQuery.scala`
- `core/src/main/scala/vql/typed/TypedSemantics.scala`
- `core/src/main/scala/vql/typed/RuntimeModel.scala`
- `core/src/main/scala/vql/typed/FolModel.scala`
- `core/src/main/scala/vql/typed/QueryBinder.scala`
- `core/src/main/scala/vql/semantics/VagueSemantics.scala`
- `core/src/main/scala/vql/fragment/Fragment.scala` (scaladoc only)
- `core/src/main/scala/vql/fragment/FragmentCheck.scala` (scaladoc only)
- `core/src/main/scala/examples/FOLDemo.scala`

Modified, tests:

- `core/src/test/scala/lexer/LexerSpec.scala`
- `core/src/test/scala/logic/TermSpec.scala`
- `core/src/test/scala/logic/FOLSpec.scala`
- `core/src/test/scala/logic/FormulaSpec.scala`
- `core/src/test/scala/logic/FOLUtilSpec.scala`
- `core/src/test/scala/parser/FOLParserSpec.scala`
- `core/src/test/scala/parser/FOLAtomParserSpec.scala`
- `core/src/test/scala/parser/FormulaParserSpec.scala`
- `core/src/test/scala/parser/TermParserSpec.scala`
- `core/src/test/scala/printer/FOLPrinterSpec.scala`
- `core/src/test/scala/semantics/FOLSemanticsSpec.scala`
- `core/src/test/scala/vql/fragment/FragmentCheckSpec.scala`
- `core/src/test/scala/vql/parser/VagueQueryParserSpec.scala`
- `core/src/test/scala/vql/logic/ParsedQuerySpec.scala`
- `core/src/test/scala/vql/quantifier/QuantifierSpec.scala`
- `core/src/test/scala/vql/typed/TypeCatalogSpec.scala`
- `core/src/test/scala/vql/typed/CompoundRangeSpec.scala`
- `core/src/test/scala/vql/typed/QueryBinderSpec.scala`
- `core/src/test/scala/vql/typed/MapDispatcherSpec.scala`
- `core/src/test/scala/vql/semantics/SatisfyingSetSpec.scala`
- `core/src/test/scala/vql/semantics/VagueSemanticsTypedSpec.scala`

Modified, documents and build:

- `build.sbt`
- `CHANGELOG.md`
- `docs/Architecture.md`
- `docs/ADR-001.md`, `docs/ADR-002.md`, `docs/ADR-004.md`, `docs/ADR-006.md`, `docs/ADR-007.md`, `docs/ADR-012.md`, `docs/ADR-014.md`, `docs/ADR-015.md`, `docs/ADR-017.md`, `docs/ADR-019.md`, `docs/ADR-020.md`, `docs/ADR-021.md`
- `docs/TODOS.md`
- `docs/PLAN-refusal-positions.md`
- `.claude/skills/vql-adr-constraints/SKILL.md`
- `CLAUDE.md`, edited by the user from the table in Phase 6

Not edited: `core/src/main/scala/util/StringUtil.scala`,
`core/src/main/scala/parser/SimpleExpr.scala` and
`core/src/main/scala/lexer/Token.scala`.

---

## 7. Tests

| Test | Pins |
|---|---|
| `TypeCatalogSpec` | a `vql.typed.TypeId` is a `vql.id.TypeId` |
| `TokenKindSpec` | each text type refuses text outside its content; `TokenKind.of` for each `Token` case, a merged decimal, an unknown operator run, a stray character; `GrammarSymbol.fromText(s.text) == Some(s)` for every case; `label` equals `tokenLabel` for words, decimals, literals and symbols |
| `TokenKindSpec` | `VariableText.of` and `SymbolText.of` for a word, a decimal and each name operator; `admitsPredicate` holds for the five relations only and `admitsFunction` for the six term operators only; no punctuation or connective is a symbol name; each `admits*` is false, and each `of` throws `IllegalArgumentException`, for `""`, `"risk-id"` and `"a b"`; `LiteralText` throws for `"a<b"` |
| `SyntaxFailureSpec` | every `message` of §5.4's table, as a literal string |
| `LexerSpec` | L3 and L4 assert `failure` and `remaining`; L6b is rewritten to pin that every character of the alphabet is kept; each of `<`, `>`, `&`, `'`, `,`, `(`, `\r`, `\t` is refused with the right `remaining` |
| `FOLParserSpec` | each `ParseError` case with its typed value; `Lex` carries `At` with the exact offset; an exception thrown by a custom lexer propagates |
| `FormulaParserSpec`, `TermParserSpec` | the custom infix parser throws `ParseFailure(NotAnInfixAtom)`; backtracking still selects the bracketed-formula branch |
| `TermSpec`, `FOLSpec`, `FormulaSpec` | each building function returns the value its case constructor builds for a word, a decimal and an operator; each throws `IllegalArgumentException` for `""`, `"risk-id"` and `"a b"`, and `Term.mkConst` for `"a<b"` |
| `FOLUtilSpec` | `generalize` and `subst` return the trees they return at 0.17.0; renaming a decimal-named bound variable throws `IllegalArgumentException` |
| `ParsedQuerySpec` | outside the package `logic`, `Term.Var("x")`, `new FOL("p", Nil)`, `copy` on a `Var` and `Formula.Forall("x", True)` do not compile; a `ParsedQuery` with the variable `risk-id`, or with such an answer variable, throws `IllegalArgumentException` |
| `QueryBinderSpec` | `BoundVar("risk-id", sort)` and `bindSatisfyingFormula(f, "risk-id", catalog)` throw `IllegalArgumentException` |
| `VagueQueryParserSpec` | one test per `ParseRefusal` case asserting the whole typed value; the ten sentences of §5.6 as literal strings; a refused literal character reports `At` with the exact offset; a formula failure is `Formula(…)`, not a wrapped exception |
| `VagueQueryParserSpec` | a query with a word holding an apostrophe, a merged decimal variable and each operator parses, and each refusal about those names builds |
| `ParsedQuerySpec` | both rules throw the typed problem with its variables |
| `QuantifierSpec`, `VagueQueryParserSpec` | `Q[>=]^{5/3}`, `Q[>=]^{1/0}` and `Q[~]^{1/2}[2.5]` are each refused with the typed case; `Q[>=]^{0/4}` and `Q[~]^{1/1}[1]` parse; a case constructor does not compile outside the package |
| `QueryBinderSpec` | every `TypeCheckError` case with its typed fields; `message` for each case as a literal string of §5.8's table |
| `QueryBinderSpec`, `VagueSemanticsTypedSpec` | an arity mismatch on a predicate carries `SymbolKind.Predicate` and one on a function carries `SymbolKind.Function`; the sentence is unchanged |
| `VagueSemanticsTypedSpec` | `BindError.details` holds the binder's `TypeCheckError` values unchanged; the three `MismatchSite` cases; the two answer-tuple problems; `DomainNotFoundError` with its `TypeId` fields |
| `MapDispatcherSpec`, `VagueSemanticsTypedSpec` | `ModelValidationError.details` holds each `RuntimeModelError`; each `EvaluationFailure` case with its `phase` and `message` |
| `SatisfyingSetSpec` | the satisfying-set bind errors arrive as `TypeCheckError` values in `BindError.details` |

The plan adds no public entry point. It changes what three existing entries
return on the left: `VagueQueryParser.parse`, `FOLParser.parse` and
`VagueSemantics.evaluateTyped`. The end-to-end test through parse, bind and
evaluate is the type-mismatch case in `VagueSemanticsTypedSpec`: a query text
is parsed, `evaluateTyped` refuses it, and the test asserts the site.

### Test assertions this plan changes

- `LexerSpec` L6b asserts that `"a, b. (c) [d] {e} ==> f"` lexes to one
  literal. The ruled alphabet refuses that text. The test is rewritten to
  assert that every character of the alphabet is kept.
- `FOLParserSpec` matches `ParseError.Trailing(rem)`,
  `ParseError.Syntax(_)` and `ParseError.Lex(_)`. The patterns take the new
  fields, and each assertion is strengthened to the typed value.
- `VagueSemanticsTypedSpec` asserts on `BindErrorDetail` cases and their
  `rendered` strings. Each assertion is restated on the `TypeCheckError` case
  and its `message`, with the same fields and the same string. A sort is
  compared as a `TypeId`.
- A test that builds an expected tree with a case constructor builds it with
  the building function. The expected tree is the same value.
- `VagueQueryParserSpec` asserts that the refusal of `Q[==]^{1/2}` contains
  `==`. An unknown operator run prints as `unknown operator` (§5.10), so the
  test asserts the typed value `UnknownQuantifierOperator(UnknownOperator)`
  and the whole sentence instead.
- No other assertion is removed or weakened.

### Code this plan removes

- The files `core/src/main/scala/vql/typed/TypeCheckError.scala` and
  `core/src/main/scala/vql/typed/RuntimeModelError.scala`. Each type moves to
  `vql/error/`.
- The enum `BindErrorDetail`, and the private methods
  `VagueSemantics.toBindErrorDetail` and `VagueSemantics.renderTypeError`.
- The private method `FOLParser.detailOf` and the `NonFatal` arm.
- The `case e: Exception` arm of `VagueQueryParser.parse`.
- `QueryError.ParseError.input`, and the `context` overrides of `ParseError`
  and `ValidationError`.
- `QueryError.EvaluationError.cause` and its `context` parameter. No site
  passes either.

### Cleanup in the ported files

The cleanup obligations of the global `boyscout` skill apply to every file
this plan edits. In a file of ADR-007's scope they stop where a characteristic
starts: wildcard imports stay, the OCaml scaladoc stays, and an `if` that
mirrors the OCaml source stays an `if`.

---

## 8. Verification

```bash
sbt test
```

Expected: every suite passes on `folEngineJVM` and `folEngineJS`, and the run
ends with `[success]`.

```bash
grep -n "case e: Exception\|NonFatal\|detailOf" \
  core/src/main/scala/parser/FOLParser.scala \
  core/src/main/scala/vql/parser/VagueQueryParser.scala
```

Expected: no output.

```bash
grep -rn "tokenLabel\|tokensLabel" core/src/main/scala/vql
```

Expected: two lines in `VagueQueryParser.scala`, the import of `tokenLabel`
and the `opLabel` fallback.

```bash
grep -rn "import vql" core/src/main/scala/lexer core/src/main/scala/parser \
  core/src/main/scala/logic core/src/main/scala/util
```

Expected: no output. The foundation imports nothing from the vague layer.

```bash
grep -rn "import vql.typed" core/src/main/scala/vql/error core/src/main/scala/vql/id
```

Expected: no output. Neither package imports the typed package.

```bash
grep -rn "BindErrorDetail\|toBindErrorDetail\|renderTypeError\|ModelErrorDetail" core/src
```

Expected: no output. Each error has one declaration and no translation.

```bash
grep -n "new Exception" core/src/main/scala/parser/FormulaParser.scala \
  core/src/main/scala/parser/FOLAtomParser.scala core/src/main/scala/parser/TermParser.scala
```

Expected: two lines, the `Unparsed input` throws of the two `parseFromString`
entries.

---

## 9. Downstream (register) impact

Breaking. Register bumps `vqlEngineVersion` to `0.18.0` and changes:

- `QueryProblem.fromQueryError`: the `ParseError` and `ValidationError` arms
  read `refusal`, `problem` and `position`, with one arm per case.
- `QueryProblem.fromQueryError`: the `BindError` arm matches
  `TypeCheckError.UnparseableConstant(_, sort, _)` and reads `sort.value`,
  where it matched `BindErrorDetail.UnparseableConstant(_, sortName, _, _)`.
- `MitigationScopeResolverLive`, `BinderIntegrationSpec` and
  `RiskTreeKnowledgeBaseSpec` import `TypeCheckError` from `vql.error`.
  `QueryProblem` imports it in place of `BindErrorDetail`.
- `MitigationScopeResolverLive.fromTypeCheckError`: `TypeMismatch`'s third
  field is `site`, `ArityMismatch` has a `kind` after `symbol`,
  `TypeNotQuantifiable` holds a `TypeId`, and every name is read through
  `.value`.
- `TargetingPredicate.create` and `MitigationScopeResolverLive`: the
  `parser.ParseError` cases carry typed values.
- A detail's `rendered` is `message`.
- `QueryProblem.fromQueryError` and `QueryService.refused` read `formatted`
  of `ModelValidationError` and `EvaluationError`, which is unchanged. The
  typed details are available to them.
- `QueryProblemFromQueryErrorSpec` builds `BindErrorDetail` values,
  `DomainNotFoundError("Loss", Set("Asset"))`, `UnboundVariableError` and
  `UnknownConstantOrLiteralError("foo")`. Each takes the typed fields of §5.8.
- `RiskTreeKnowledgeBaseSpec` line 849 matches
  `TypeCheckError.TypeMismatch(_, actual, _)` and still compiles.

Unchanged for register: every import of `vql.typed.TypeId` and
`vql.typed.SymbolName`, and every tree it reads. Register builds no tree, so
the closed constructors change none of its code.

The input each case is attributable to (requirement 10):

| Engine value | Input |
|---|---|
| `QueryError.ParseError`, every `ParseRefusal` case | query text |
| `parser.ParseError`, every case | formula text |
| `ValidationProblem.QuantifiedVariableNotInRange`, `RangeVariablesNotAnswered` | query text |
| `ValidationProblem.MissingAnswerValue`, `AnswerSortMismatch` | answer tuple |
| `QueryError.BindError`, `UnknownConstantOrLiteralError` | query text, read against the catalog |
| `QueryError.ModelValidationError` | the catalog and the model |
| `QueryError.DomainNotFoundError` | the catalog and the model |
| `EvaluationFailure.PredicateRefused`, `FunctionRefused` | the consumer's dispatcher |
| `EvaluationFailure.VariableSortMismatch`, `QueryError.UnboundVariableError` | the answer tuple or the model's domains |

A `BindError` has two possible causes the engine cannot tell apart: the text
names something the catalog lacks, or the catalog changed under text that
once bound. The consumer knows which, because it knows where the text came
from.

The content type of each text field register refines:

| Field | Content |
|---|---|
| `WordText` | `[A-Za-z0-9_']+` |
| `DecimalText` | digits, `.`, digits |
| `LiteralText` | `[A-Za-z0-9 /_.+-]*` |
| `VariableText` | a `WordText` or a `DecimalText` |
| `SymbolText` | a `WordText`, a `DecimalText`, or a `GrammarSymbol` |
| `TypeId`, `SymbolName` | a non-empty string the consumer's own catalog declared |

An infix relation parses to an atom named by its operator, and an infix
operator to a function application named by it. `x < 5` is the atom `<`, and
`x + 1` is the function `+`. So `UnknownPredicate.name`, `UnknownFunction.name`,
`ArityMismatch.symbol` and `MismatchSite.FunctionReturn.name` can hold an
operator's text, which `SymbolText.Operator` carries. A consumer type that
admits only a word refuses them. The handoff note states this under its own
heading.

Register calls `QueryBinder.bind` and `QueryBinder.bindSatisfyingFormula`
directly, in `QueryServiceLive` and `MitigationScopeResolverLive`. It receives
the same `TypeCheckError` values there as in `BindError.details`. The variable
it passes to `bindSatisfyingFormula` comes from `FOLUtil.fvFOL` of a parsed
formula, so the new check on that argument always passes.

Two behaviour changes reach register's users. A query whose quoted literal
holds a character outside the alphabet is refused at the lexer. A query whose
quantifier lies outside the bounds of §5.11 is refused at the parser.
