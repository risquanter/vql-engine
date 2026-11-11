# Plan stub: Typed Refusals

**Status:** STUB, written 2026-11-11. It records the requirement and the
context found so far. It is not an implementation plan and authorizes no code.
Elevating it means writing the signatures, the per-ADR verdict table
`docs/WORKING-INSTRUCTIONS.md` requires, and the phases, and getting them
approved.
**Version this was read at:** 0.17.0.
**Target version:** not decided. The change removes or reshapes public error
fields, so under early-semver it is a breaking minor bump.
**Origin:** register, the downstream consumer, in the repository `register`:
`docs/dev/plans/PLAN-UES-UNIFIED-ERROR-SHAPE.md`, decisions `UES-D-18`,
`UES-D-19` and `UES-D-20`, and its steps 5.6 and 5.7. Those two steps wait on
the release this plan produces.
**Companion stub:** [PLAN-refusal-positions.md](PLAN-refusal-positions.md),
which register does not wait on.

---

## 1. What is asked for

Every refusal a caller can cause by what they typed is returned as a value of a
closed set, with the values its sentence is built from as typed fields. The
sentence is derived from those fields and is never the only carrier.

The bind refusals already have this shape. `BindErrorDetail` in
`vql/error/QueryError.scala` has one case per `TypeCheckError`, each with its
fields and its `rendered` string (ADR-019, ADR-021). This plan gives the same
shape to the refusals that are still strings.

The engine adopts the principle in its own form. It does not take register's
types, and its error types keep their own root. The goal is that register's
handling of an engine refusal is one `match` arm per engine case, with no
reading of message text.

## 2. Why register needs it

Register's rule is that a refusal sent to a client or written to a log is
structured, and never repeats the caller's whole input or a sentence another
component composed. Two risks motivate the rule. A value repeated to a client
can be executed by a client that renders it unsafely. A value repeated into a
log reaches a wider audience than the caller and may be confidential.

Register may still return values extracted from query text, provided each value
has a content type it can state. After the lexer has read a query, every piece
of the caller's text is one of four kinds:

| Piece of the query | Content | How a refusal carries it |
|---|---|---|
| a word: predicate, function, variable, bare constant | `StringUtil.alphanumeric` | as text, in a typed field |
| a quoted literal | the alphabet of §3.1 | as text, in a typed field |
| punctuation, a known operator | a closed set | as a case, never as text |
| anything else: an unknown operator run, a stray character, a literal holding a refused character | unbounded | as a kind and a position, never as text |

A sentence the engine composed cannot be given such a type, because it embeds
`tokensLabel(…)` output and exception messages. That is why the string fields
have to become typed ones.

## 3. Scope

### 3.1 The quoted literal's alphabet

`Lexer.lexStringLit` reads a literal verbatim up to the closing `"`. It refuses
only an embedded `\n` and a missing closing quote, so `<`, `>`, `&`, `\r` and
every other control character pass into `Token.StringLit` today.

The lexer admits in a quoted literal only what a name, an identifier or a
number can hold. Register's proposal is about `[A-Za-z0-9 /_.+-]`; the engine
plan decides the exact set and states where it comes from. Any other character
is a lexer refusal of its own kind, reported with the character's offset, which
the lexer has because it walks the input one character at a time.

The literal branch of `Lexer.lex` is marked "NEW per D1 fix; no OCaml
counterpart", so this does not touch the ported core.

### 3.2 Parse refusals of `VagueQueryParser`

`VagueQueryParser.parse` returns `QueryError.ParseError(message, input,
position, context)`. It is built at fourteen sites in
`vql/parser/VagueQueryParser.scala`. Each has a fixed template, and most
interpolate `tokensLabel(tokens)`. They become one closed set with one case per
template. A field that is a token is typed by the token's kind, and carries
text only for a `Token.Word` or a `Token.StringLit`.

`ParseError.input` repeats the caller's whole query. Register has the query
already and must not receive it back, so the field goes or stops being part of
what a consumer reads.

### 3.3 The catch-all

`VagueQueryParser.parse` ends with:

```scala
case e: Exception      =>
  Left(
    QueryError.ParseError(
      s"Unexpected error during parsing: ${e.getMessage}",
```

Any exception thrown under `parse` is therefore reported as the caller's parse
error, with the exception's message. The catch is narrowed to the parser's own
failure types, so an exception that is not a parse refusal stays a defect.

### 3.4 The ported formula parser

The foundation parser signals failure by throwing. The sites, in
`core/src/main/scala/parser/`:

- `SimpleExpr.scala`: "Expected an expression at end of input", "Expected
  variable, constant, or '(', got: …", "Unparsed input: …"
- `FormulaParser.scala`: "formula expected", "Body of quantified term
  expected", and one more at line 128
- `FOLAtomParser.scala`: "Not an infix atom" (`ParseFailure`), "parse_atom:
  expected atom", "parse_atom", "Unparsed input: …"
- `Combinators.scala`: one `ParseFailure` at line 126
- `TermParser.scala`: "term expected", "term expected, got: …", "Unparsed
  input: …"

These files are in ADR-007's scope. Two of its characteristics bear on the
change:

> C12 — `failwith` mapped to `throw new Exception(…)`. OCaml's `failwith
> "message"` is consistently translated to `throw new Exception("message")`.
> Error messages are often preserved verbatim from the OCaml source.

> C13 — Element-type refinement permitted; combinator shape preserved

Register's reading, to be confirmed in the per-ADR verdict: each throw keeps
its message word for word and additionally carries a typed kind, which is an
element-type refinement of the thrown value. Exception backtracking (C2) is
unchanged. ADR-007 says "A change that breaks a characteristic requires
explicit justification and approval", so if the verdict finds that C12 is
broken, that is a decision for the user and not for this plan.

### 3.5 The foundation's `parser.ParseError`

`FOLParser.parseWithLexer` already returns an enum:

```scala
enum ParseError:
  case Syntax(detail: String)
  case Trailing(remaining: String)
  case Lex(detail: String)
```

Its three payloads are strings: an exception message, a `tokensLabel` rendering
and a lexer message. They become the typed values of §3.2 and §3.4. Register
reads this type in `TargetingPredicate.create` and
`MitigationScopeResolverLive`.

### 3.6 The two variable rules of `ParsedQuery.mk`

`ParsedQuery.mk` throws `QueryError.ValidationError(message, field, context)`
for two rules: the quantified variable must occur free in the range formula,
and the range's other free variables must be answer variables. The variable
names are in a `Map[String, String]` joined with `", "`. Each rule becomes a
typed case carrying its variables as a collection of names.

### 3.7 The type-mismatch site

`TypeCheckError.TypeMismatch(expected, actual, context: String)` is built in
`QueryBinder.scala` with one of three strings: `s"variable '$name'"`,
`s"constant '$name'"` and `s"function '$name' return"`. The site becomes a
three-case type carrying the name, and `BindErrorDetail.TypeMismatch` carries
it across the boundary as the other details carry their fields.

### 3.8 A position field on every refusal

Every refusal carries a position, and "no position" is an explicit case of it,
not an absent `Option` default. This plan fills it where the engine has a
position today: the lexer refusals of §3.1, which have the exact offset, and
the one parse site that computes one. How far the rest can be filled is the
subject of the companion stub.

## 4. Not in scope

- The bind refusals other than §3.7. They are structured since 0.17.0.
- Positions beyond what §3.8 states.
- A maximum length for query text, a word or a literal. Register records that
  question in its own `docs/dev/TODO.md`, item 65.
- Any change to what a valid query means. Only the §3.1 alphabet narrows what
  is accepted, and a query using a refused character in a literal is the one
  behaviour change a consumer sees.

## 5. What the engine plan has to settle

1. The exact literal alphabet of §3.1, with its origin. Register's node names
   are `^[A-Za-z0-9 /\-]+$` and its identifiers are ULIDs; a number needs a
   digit, `.`, `+` and `-`. Whether `_` and `.` stay is the engine's call.
2. Whether the apostrophe stays in `StringUtil.alphanumeric`. It comes from the
   OCaml original, so removing it touches the ported core. Register leaves it
   to its own output encoding if it stays.
3. The shape of the typed parse refusal: one enum for the lexer, the vague
   parser and the foundation parser, or one per layer joined at
   `VagueQueryParser.parse`. ADR-004's layering (the foundation never imports
   `vql.*`) bears on it.
4. Whether `rendered`-style derived strings stay on the new cases, as
   `BindErrorDetail` has them, so `QueryError.formatted` is unchanged for
   consumers that only log.
5. The reading of ADR-007 C12 and C13 in §3.4.
6. The target version and the handoff note for register.

## 6. What register does with the release

Register bumps `vqlEngineVersion` in its `build.sbt`, translates each engine
case to its own wire type one arm per case, and renders its own sentences. Its
plan writes its signatures for step 5.7 from this plan's signatures once this
plan is approved.
