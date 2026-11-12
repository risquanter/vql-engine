# Plan stub: Refusal Positions

**Status:** STUB, written 2026-11-11. It asks for an analysis and records the
context found so far. It is not an implementation plan and authorizes no code.
**Version this was read at:** 0.17.0. §3 is brought to 0.18.0.
**Origin:** register, the downstream consumer, in the repository `register`:
`docs/dev/plans/PLAN-UES-UNIFIED-ERROR-SHAPE.md`, decision `UES-D-20`.
**Independence:** this analysis is the engine's own. Register does not wait on
it and its design does not depend on the outcome.
**Companion stub:** [PLAN-typed-refusals.md](PLAN-typed-refusals.md), which
introduces the position field this analysis would fill.

---

## 1. The question

How far can the engine say where in the query text a refusal points, for each
kind of refusal, and what does each step cost?

The result is a recommendation per kind of refusal: fill the position, or leave
it as the explicit "no position" case, with the reason.

## 2. Why it is asked

Register treats a position as present on every query refusal. Its type is:

```scala
enum QueryPosition:
  case At(offset: NonNegativeInt)
  case Unknown
```

A refusal the engine reports with no position becomes `Unknown`, and register
computes no position itself. So every position the engine learns to report
reaches the user with no further change in register, and every one it cannot
report stays a visible `Unknown`.

## 3. Where the engine stands at 0.18.0

**Tokens carry no offset.** `lexer/Token.scala`:

```scala
enum Token:
  case Word(name: String)
  case StringLit(content: String)
  case OpSym(sym: String)
  case LParen, RParen, LBracket, RBracket, LBrace, RBrace, Comma, Dot
```

`Lexer.lex` walks a `List[Char]` and knows each character's place only
implicitly, as the length of what is left.

**The position type exists.** `parser/SourcePosition.scala`:

```scala
enum SourcePosition:
  case At(offset: Int) // a UTF-16 code-unit offset into the input
  case Unknown
```

`QueryError.ParseError`, `QueryError.ValidationError` and every
`parser.ParseError` case carry one.

**Only lexer refusals report a position.** `LexerError` carries `remaining`,
the length of the input left at the refused character, and both boundaries
report `SourcePosition.At(s.length - e.remaining)`. Every other parse refusal,
including tokens left after a query, reports `SourcePosition.Unknown`. No site
reports an estimate.

**Bind errors have none.** A `TypeCheckError` is raised over `Formula[FOL]`,
`Term` and `FOL`, which hold names and no source location. `TypeCheckError`
has no position field.

**The two variable rules have none.** `ParsedQuery.mk` throws
`ValidationError(problem, SourcePosition.Unknown)`.

## 4. What the analysis covers

1. **Lexer refusals.** The lexer can count consumed characters. The cost is a
   counter threaded through `lex` and `lexStringLit`, or computed from the
   remaining length. `lexwhile` is the OCaml primitive and should stay as it
   is.
2. **Offsets on tokens.** A token that carries its start offset lets every
   parser site report the offset of the token it stopped at. The options
   include a field on each `Token` case, and a parallel list of offsets beside
   the token list. ADR-007 C13 permits refining the token type "provided that
   the surrounding combinator shape — signature arity, tuple threading `(A,
   Remaining)`, exception-based backtracking, mutual recursion structure — is
   unchanged". Pattern matches on tokens in the ported parsers would gain a
   field, and whether that stays within C13 (b)'s case-by-case traceability is
   part of the verdict. `mergeDecimalTokens` in `VagueQueryParser` joins tokens
   and has to keep an offset.
3. **Parse refusals.** With token offsets, every parse site that reports
   `Unknown` can report the first unconsumed token, including the
   trailing-tokens site. The ported parsers throw from deep inside backtracking, so
   which failure is the one reported, and therefore which offset, needs
   stating: the furthest position reached is the usual choice and is not what
   exception backtracking yields by default.
4. **Bind refusals.** A bind error names a predicate, a function, a constant or
   a variable. Reporting its place needs the parsed formula to remember where
   each part came from. ADR-007 C8 says of `Term`, `FOL` and `Formula[+A]`:
   "The cases, their fields and their field types map one to one to the OCaml
   definitions." So a location field on those types breaks C8. The case
   constructors that hold a name are closed, and a tree is built through
   `Term.mkVar`, `Term.mkFn`, `Term.mkConst`, `FOL(…)`, `Formula.mkForall` and
   `Formula.mkExists`. A side table could be filled in those functions, but they
   receive no offset from the parser today. The alternatives to weigh are a side table from each node to its
   offset built during parsing, a search of the token list for the name at
   report time, which is ambiguous when a name occurs twice, and leaving bind
   refusals at "no position".
5. **The two variable rules of `ParsedQuery.mk`.** They concern the query as a
   whole. Whether any single offset describes them is a question to answer, and
   "no position" may be the correct value.
6. **What an offset counts.** Characters of the string handed to `parse`, from
   zero. A consumer that shows the query in a browser counts UTF-16 units; the
   lexer's alphabet is ASCII apart from what a refused character may be, so the
   analysis states whether the two can differ before the reported offset.

## 5. Constraints

- ADR-007 C1 to C13 for every file in its scope tables, and "A change that
  breaks a characteristic requires explicit justification and approval".
- ADR-004: the foundation never imports `vql.*`, so a position type used by the
  foundation parser lives in the foundation.
- ADR-012: construction invariants by `require`, state-dependent failures by
  `Either`.
- No dependency is added.

## 6. Output

A document with the recommendation per kind of refusal, the ADR verdicts it
rests on, and the decisions the user has to rule. If it recommends work, that
work becomes an implementation plan of its own or a section of
`PLAN-typed-refusals.md`, as the user decides.
