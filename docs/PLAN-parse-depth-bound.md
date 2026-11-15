# Implementation Plan: Parse Depth Bound

**Status:** DRAFT, for approval. No decision is open (§2).
**Version this was read at:** 0.19.0.
**Target version:** 0.20.0. Under early-semver a pre-1.0 breaking change bumps
the minor version. This plan adds a case to two public enums, `parser.ParseError`
and `vql.error.ParseRefusal`. A consumer that matches either exhaustively stops
compiling until it adds the case.
**Plan code:** `PDB`, the initials of the filename slug `parse-depth-bound`.
Decisions are `PDB-D-<n>`.
**Origin:** `docs/TODOS.md` T-014, and the user's ruling of 2026-11-15 that the
bound is a check on the token list at the two `Either` boundaries, with an
iterative lexer (the option labelled B in the session that ruled it). Register,
the downstream consumer, waits on this release: in the repository `register`,
`docs/dev/plans/PLAN-RISKTRANSFORM.md` §7.6.12 decision 20 states that the
slice storing a targeting predicate "lands only after register adopts the
release carrying it, expected to be 0.20.0".
**Records this plan creates and amends:** it creates `docs/ADR-024.md`. It
amends `docs/ADR-007.md`, `docs/ADR-022.md` and `docs/ADR-002.md`. Why a new
record is needed is §2, settled point 6.

---

## 0. Executive summary

**What goes wrong today.** The engine turns a long or deeply nested input into
a `StackOverflowError`. A stack frame is the block of the thread's fixed-size
stack that one function call holds until it returns. Three recursions take
frames in proportion to the input:

- The lexer takes one frame per token, and one per character of a word, a
  space run or a quoted literal.
- The parser takes about 20 frames per parenthesis in formula position.
- Every function that walks a parsed tree takes about one frame per tree level.

`StackOverflowError` is not a parse refusal, so `FOLParser.parse` and
`VagueQueryParser.parse` do not catch it. Inside a ZIO effect under the default
runtime it ends the process. Register stores a predicate of up to 4 096
characters and parses it again on every read of a tree, so one deep predicate
would end its server on each read.

**What changes.**

1. The lexer becomes iterative, so lexing uses constant stack for any input.
2. Before parsing, each of the two `Either` boundaries makes one pass over the
   token list, `NestingBound.check`. The pass tracks two counts:
   - the parenthesis depth;
   - the number of operator tokens and quantified variables, each of which can
     add one level to the parsed tree.
3. At the first token past either limit, the boundary returns a typed refusal,
   and the parser does not run. The limits are 64 parentheses and 256
   operators and quantified variables.
4. Every input within the limits leaves at least half of a 1 MiB JVM thread
   stack, and half of Node's default stack, unused (§4.4).

**What does not change.** No combinator in `Combinators`, `FormulaParser`,
`TermParser` or `FOLAtomParser` changes. The grammar is unchanged, and every
input within the limits parses to the same tree as today. `FOLParser.parseTokens`
and `VagueQueryParser.parseTokens` keep their shape, and they do not run the
check.

---

## 1. Requirements

1. **No input reaches a `StackOverflowError` through `FOLParser.parse`,
   `FOLParser.parseWithLexer` with the default lexer, `FOLParser.defaultParser`
   or `VagueQueryParser.parse`.** This covers parsing and every consumer of the
   tree they return: `FragmentCheck.check`, `QueryBinder.bind`,
   `VagueSemantics.evaluateTyped`, `VagueSemantics.satisfyingSet` and
   `FOLPrinter.print`. It holds on a 1 MiB JVM thread stack in an interpreted
   run and on Node's default stack.
2. **An input past a limit is a typed refusal.** `FOLParser` returns a
   `ParseError` case, and `VagueQueryParser` returns a `ParseRefusal` case
   inside `QueryError.ParseError`. Each holds the limit crossed.
3. **The refusal is deterministic.** Whether an input is refused depends on its
   tokens only. It never depends on the JIT state, the JDK or the stack size.
   The JIT, the JVM's just-in-time compiler, replaces interpreted frames with
   smaller compiled ones as code warms up, so the same input can use different
   amounts of stack in two runs.
4. **Register's realistic predicates are accepted.** A disjunction of 85
   hand-picked nodes, `eq(x, node_id("<26-character id>"))` joined by `\/`,
   holds 84 operators and nests two parentheses deep.
5. **The lexer is stack-safe on any input.** A 100 000-character word, space
   run or quoted literal lexes without a `StackOverflowError`.

### Not in scope

- The two `parseTokens` entries take tokens and keep the combinator shape that
  ADR-007 C1 fixes. Their scaladoc states that they run no bound, and that a
  caller holding tokens runs `NestingBound.check` itself (§2, settled point 5).
- A custom lexer passed to `FOLParser.parseWithLexer` is the caller's code. The
  bound covers the tokens that lexer returns, not the lexer's own stack use.
- The parser's running time on deep parentheses grows faster than linearly,
  because the infix-atom attempt reparses the bracket at each level. At 64
  levels it is under a millisecond, so the bound covers it as well.

---

## 2. Decisions for the user

None is open. The design was ruled on 2026-11-15. The points below are
settled by derivation from that ruling and from the measurements of §4. Each is
reversible, and three of them refine the form the ruling was discussed in.

### Settled by derivation

1. **The second count is operators and quantified variables, not all tokens.**
   The ruling was discussed with "the token count" as the second number. The
   measurements of §4.3 rule that out for register's inputs.
   - Evaluating on Node overflows at about 696 tree levels.
   - With an all-token count, the limit has to sit near 350, because a chain of
     negations adds one tree level per token.
   - A register predicate of 85 nodes holds about 900 tokens.

   The pass therefore counts only the tokens that can add a tree level (§4.2).
   Register's 85-node predicate then counts 84.

2. **The limits are 64 parentheses and 256 operators and quantified
   variables.** These are figures chosen here from the measurements of §4.3:
   - **64.** The smallest measured parenthesis limit is 185 levels, for
     function applications nested inside a predicate (`p(f(f(…)))`) in an
     interpreted JVM. 64 is the largest power of two at most half of it.
   - **256.** The smallest measured tree-depth limit is 696 levels, for
     evaluation on Node. Per §4.2, an input within both limits builds a tree at
     most 64 + 256 + 3 = 323 levels deep, which is under half of 696.

   §4.4 checks the combined case on both platforms.

3. **The refusal carries `SourcePosition.Unknown`.** The ruling was discussed
   with the refusal naming the token index, and T-014 says so. Two facts decide
   against it:
   - No single token is at fault. The 257th operator is refused only because
     256 came before it.
   - ADR-022 §4 states: "A site reports `At` only where the offset is exact."
     Tokens carry no source offset, and a token index is no offset.

   `Syntax` and `Trailing` report `Unknown` for the same reason. Phase 4
   brings T-014's wording to this.

4. **The limits are constants, not a parameter.** T-014 leaves the two open.
   The safe value follows from the engine's own frames per level, which a
   consumer cannot measure from outside. A consumer that wants a tighter bound
   already applies one, as register's length bound does. A parameter would add
   public surface that no consumer needs.

5. **The `parseTokens` entries run no check.** Both are documented as the
   combinator entry for callers that lex themselves, and ADR-007 C1 fixes the
   `FOLParser.parseTokens` shape as `List[Token] => (A, List[Token])`.
   `NestingBound.check` is public, so a token caller runs it. Register calls
   only `FOLParser.parse` and `VagueQueryParser.parse`:
   - `modules/common/.../TargetingPredicate.scala` line 77;
   - `modules/server/.../MitigationScopeResolver.scala` line 105;
   - `QueryRequest`, through `VagueQueryParser.parse`.

6. **A new record, ADR-024, holds the bound.** The bound constrains future
   work in a way no existing record states:
   - A grammar change that adds a tree level without an operator token must
     extend the count.
   - A new recursive walk over the tree must stay within the measured margin.

   ADR-007 records the preserved port, and ADR-022 records the refusal types.
   Neither one constrains the depth of a parse tree, so the constraint needs a
   record of its own.

7. **`NestingFailure` is a new enum beside `SyntaxFailure`, not cases of it.**
   The scaladoc of `SyntaxFailure` says "`message` is the ported parser's
   `failwith` text, word for word". The depth failures have no OCaml text, and
   no parser throws them.

---

## 3. ADR compliance review (planning phase)

| ADR | Verdict | Basis |
|---|---|---|
| ADR-001 (many-sorted binding) | Relevant, compliant | `QueryBinder.bind` and `VagueSemantics.evaluateTyped` are not edited. The bound protects them, because every `ParsedQuery` they receive came through `VagueQueryParser.parse`. |
| ADR-002 (parser-combinator style) | Relevant, compliant, example stale | §2's rule holds: "A single `try/catch` at the public entry point converts those four, and only those". The depth check returns a value and is not a caught throw. §2's code block shows the body of `VagueQueryParser.parse`, which gains the check. Phase 4 brings the example to §5.4. §4, "Lexer Produces Flat Token List", holds. |
| ADR-003 (HDR sampling) | Not relevant | No sampling code is touched. |
| ADR-004 (layering) | Relevant, compliant | `parser.NestingBound` imports `lexer` only. `vql.error.ParseRefusal` imports `parser.NestingFailure`, the direction ADR-023 §4 states: "`vql.error` ← imports `vql.id` and the foundation". |
| ADR-006 (enum vs sealed trait) | Relevant, compliant | `NestingFailure` is a pure-data `enum` with a derived `message` under §3. New files follow §4 and §5. `Lexer.scala` and `FOLParser.scala` keep their ported import style under ADR-007. |
| ADR-007 (OCaml-ported core) | Relevant, **amended** | `lexer/Lexer.scala` and `parser/FOLParser.scala` are in Tier 1. C10 says "`lexwhile` accumulates via recursive prepend", and the iterative `lexwhile`, `lexStringLit` and `lex` depart from it. The user ruled that departure on 2026-11-15. C1, C2, C3 to C9 and C11 to C13 hold: every signature, throw and OCaml scaladoc block stays. `FOLParser.parseWithLexer` gains a check before `parseTokens`, beside the boundary conversion the review criterion records. Phase 4 amends C10 and adds a review-criterion paragraph. `parser/NestingBound.scala` is a new file, not a port, and is outside the scope tables, like `ParseError.scala`. |
| ADR-012 (error channel) | Relevant, compliant | `NestingBound.check` returns `Either[NestingFailure, Unit]`. The refusal leaves through the two existing `Either` boundaries. No `require` is added. |
| ADR-014 (quantifiability) | Not relevant | Neither check is touched. |
| ADR-015 (value boundaries) | Not relevant | The literal branch and `Extract` are not touched. |
| ADR-016 (carrier witness) | Not relevant, Proposed | `LiteralRef.value` is not touched. |
| ADR-017 (formula ranges) | Relevant, compliant | `ParsedQuery.mk`'s range rules still run inside `parseTokens`, after the check. A range formula counts toward the same limits as the scope. |
| ADR-018 (fragment membership) | Relevant, compliant | §5 says the caller parses through `FOLParser.parse` and maps `ParseError` at its own boundary. A caller now meets one more case, `Nesting`. `FragmentCheck` is not edited. |
| ADR-020 (binder error accumulation) | Not relevant | The binder is not edited. |
| ADR-022 (typed refusals) | Relevant, **amended** | §1: `NestingFailure` is one case per sentence template, and each case holds the limit its sentence is built from. §4: the refusal reports `Unknown`, because no offset is measured. §5: the boundaries still catch the same four failure types. The new cases hold no caller text, so §2 and §3 hold. Phase 4 adds `NestingFailure` to §1's list of sets and `parser/NestingBound.scala` to the implementation table. |
| ADR-023 (identifiers in `vql.id`) | Relevant, compliant | `ParseRefusal` stays the one declaration of the vague refusal. `NestingFailure` holds no sort and no symbol. |

**Deviations detected:** one, ADR-007 C10. The user ruled it as part of the
ruling this plan implements, and Phase 4 records it in ADR-007.

**What the review read.** Every record in force was read on 2026-11-15:
ADR-001, 002, 003, 004, 006, 007, 012, 014, 015, 017, 018, 020, 022 and 023,
and ADR-016 (Proposed). The activities of `vql-adr-constraints` Layer 1 this
plan falls under:

- editing a file in the OCaml-ported foundation;
- changing `VagueQueryParser`;
- adding a failure;
- changing the public API;
- writing a decision record.

**Layer 3 rows checked.**

- ADR-002 × ADR-007 × ADR-012: the library keeps exactly two `Either`
  boundaries, and the check sits inside them.
- ADR-007 × ADR-006: `Lexer.scala` and `FOLParser.scala` keep their imports as
  written.
- ADR-007 C11 × the comment-style rule: the OCaml blocks in `Lexer.scala`'s
  scaladoc stay verbatim. A sentence is added beside each one, saying the Scala
  body is iterative.
- ADR-007 C12 × ADR-022: `LexerError` keeps its `failure` and `remaining`, and
  the iterative `lexStringLit` throws at the same character with the same
  `remaining`.
- ADR-017 × ADR-002: `ParsedQuery.mk` still throws behind
  `VagueQueryParser.parse`.
- No other row involves a record this plan touches.

Two new rows are owed. Phase 4 writes both into the records' cross-ADR
sections and into Layer 3:

- **ADR-024 × ADR-007.** The bound reads as a change to the parser. It runs
  outside every combinator, so C1 and C6 hold. Only the lexer departs, from
  C10.
- **ADR-024 × ADR-022.** A refusal at a token reads as one with a position. No
  token is at fault, so the position is `Unknown`.

**Layer 4.** No register record states a parse depth.

- Register's ADR-029 §3 row for `TargetingPredicate.create` says
  "length-bounded (1–4096) then parsed via `FOLParser`", and that stays true.
- Register's ADR-028 appendix, "Error Responses", says `Unparseable` "carries
  one typed `ParseProblem`". That stays true once register adds a
  `ParseProblem` case for the new refusal.

So ADR-024 gets no Layer 4 row. Register's code changes are written into its
task list once (§10).

---

## 4. Current behaviour and measurements (grounding)

### 4.1 The three recursions

`core/src/main/scala/lexer/Lexer.scala`. `lex` is not tail-recursive, so every
token holds a frame until the input ends:

```scala
case c :: cs if punctChars.contains(c) =>
  // Single-character punctuation — emit dedicated Token case.
  punctuationToken(c) :: lex(cs)
```

`lexwhile` recurses once per character with `val (tok, rest) = lexwhile(prop, cs)`.
`lexStringLit` does the same with `val (tail, rest) = lexStringLit(cs)`. In
`VagueQueryParser.scala`, `mergeDecimalTokens` recurses once per token with
`t :: mergeDecimalTokens(rest)`.

`core/src/main/scala/parser/FormulaParser.scala`. On `(`, the formula parser
first tries the infix-atom parser, which parses the whole bracket as a term
through the six operator levels of `TermParser.parseTerm`:

```scala
case Token.LParen :: rest =>
  // Try infix atom parser first; on backtracking, parse bracketed formula.
  try papply((a: A) => Atom(a))(ifn(vs, inp))
  catch
    case _: ParseFailure =>
      parseBracketed(parseFormula(ifn, afn)(vs), Token.RParen)(rest)
```

Every tree consumer recurses once per tree level. A chain of `n` connectives
parses into a right-nested tree `n` levels deep.

### 4.2 What adds a tree level

The parsed tree is a `Formula[FOL]`, whose atoms hold `Term`s. Consider the
nodes on any path from the root to a leaf.

- `Not`, `And`, `Or`, `Imp` and `Iff` each come from one `Token.OpSym`: `~`,
  `/\`, `\/`, `==>` or `<=>`.
- `Forall` and `Exists` each come from one `Token.Word` in a quantifier's
  variable list. The list is the words after `forall` or `exists` up to
  `Token.Dot`, and `parseQuant` nests one quantifier per word.
- An infix relation atom such as `x < y` comes from one `Token.OpSym`.
- A term operator (`+`, `-`, `*`, `/`, `^`, `::`) and unary minus each come
  from one `Token.OpSym`.
- A function or predicate application `f(…)` holds its arguments inside a
  parenthesis. Nested applications on one path are nested parentheses.
- `Atom` and the `FOL` value inside it add two levels. A path passes through at
  most one atom, because atoms do not nest inside formulas.

So the depth of the tree is at most the operator tokens, plus the quantified
variables, plus the parenthesis depth, plus 3. The extra 3 are the `Atom`
level, the `FOL` level and the leaf.

The same count bounds the parser's non-parenthesis recursion. `parseAtomicFormula`
recurses once per `~`, `parseQuant` once per variable, `parseAtomicTerm` once
per unary minus, and each infix chain composes one closure per operator.

`parseList` composes one closure per argument. It is bounded by the number of
arguments, not by this count, and measured at more than 6 000 arguments on both
platforms (§4.3). A list that long holds 12 000 tokens, which register's
4 096-character bound already excludes.

### 4.3 Measured limits

The measurements were taken on 2026-11-15 at 0.19.0, with scratch programs
outside the repository. The JVM runs used a 1 MiB thread with `-Xint`, the
interpreted mode whose frames are the largest. The Scala.js runs used Node
under `sbt folEngineJS/test` with its default stack. Each figure is the largest
`n` that ran without an exception.

| Shape, `n` levels | JVM parse | JS parse | Smallest downstream, JVM | Smallest downstream, JS |
|---|---|---|---|---|
| `(`ⁿ `p(x)` `)`ⁿ | 222 | 374 | > 6 000 | 608 (fragment) |
| `p(` `f(`ⁿ `x` `)`ⁿ `)` | **185** | 630 | 644 (evaluate) | 630 (printer) |
| `~ `ⁿ `p(x)` | 2 525 | 1 621 | 1 705 (printer) | **696** (evaluate) |
| `p(x)` joined by `\/`, `n` atoms | 4 204 | > 6 000 | 906 (printer) | 697 (evaluate) |
| `q` joined by `\/`, `n` atoms | 4 204 | > 6 000 | 907 (printer) | 698 (evaluate) |
| `forall x1 … xn. p(x1)` | 3 994 | 2 699 | 1 675 (evaluate) | 696 (evaluate) |
| `p(- - … x)`, `n` signs | 2 833 | 1 214 | 1 634 (printer) | 2 249 (printer) |
| `p(x + x + … x)`, `n` terms | 4 160 | > 6 000 | 1 101 (printer) | 1 780 (printer) |
| `p(x, x, … x)`, `n` arguments | 4 324 | > 6 000 | > 6 000 | 5 977 (printer) |

The downstream columns take the smallest limit over five consumers:
`FragmentCheck.check`, `FOLPrinter.print`, `VagueSemantics.evaluateTyped`,
`VagueSemantics.satisfyingSet`, and the JVM's `toString`.

With the current recursive lexer, a 3 000-atom chain overflows inside
`Lexer.lex`. On Node, lexing overflows at 2 718 tokens of parentheses.

Near the limit, one JVM overflow surfaced as `java.util.regex.PatternSyntaxException`
from `StringUtil.isDecimalLiteral`. `Pattern` catches the overflow internally.
So catching `StackOverflowError` would not even catch every overflow.

The smallest limits are 185 parenthesis levels and 696 tree levels. §2,
settled point 2 derives the two limits from them.

### 4.4 An input at both limits

The query below holds 256 operators: `>=`, `^` and `/` in the header, and 253
negations. It nests 64 parentheses: the query's own `(`, the `p(`, and 62
`f(`.

```scala
val scope = "~ " * 253 + "p(" + "f(" * 62 + "x" + ")" * 62 + ")"
val query = s"Q[>=]^{1/2} x (p(x), $scope)"
```

On a 1 MiB interpreted JVM thread and on Node, five steps all succeed:
`VagueQueryParser.parse`, `evaluateTyped`, `FOLParser.parse` of the scope,
`FragmentCheck.check` and `FOLPrinter.print`. Phase 3 pins this as a test.

---

## 5. Target behaviour

### 5.1 The bound — `core/src/main/scala/parser/NestingBound.scala`, new

**Family.** `NestingFailure` joins the foundation's failure enums.
`lexer/LexFailure.scala` holds `enum LexFailure` with `case UnterminatedLiteral`.
`parser/SyntaxFailure.scala` holds `enum SyntaxFailure` with `case FormulaExpected`.
No existing type denotes a size bound (§2, settled point 7). `NestingBound`
joins the foundation's operation objects, such as `parser/FOLParser.scala`'s
`object FOLParser`.

**Primitives.**
- `limit: Int` is a count of tokens compared with a constant. It has no
  identity and no range of its own, so no opaque type applies.
- `depth`, `operators` and `inVariableList` are locals of a private loop.

```scala
package parser

import lexer.Token

import scala.annotation.tailrec

/** Why `NestingBound.check` refused a token list before parsing. */
enum NestingFailure:
  /** Parentheses nest deeper than `limit`. */
  case ParenthesesTooDeep(limit: Int)

  /** More than `limit` operator tokens and quantified variables. Each can add one level to the parsed tree. */
  case TooManyOperators(limit: Int)

  def message: String = this match
    case ParenthesesTooDeep(limit) => s"Parentheses nest deeper than $limit"
    case TooManyOperators(limit)   => s"The formula holds more than $limit operators and quantified variables"

/**
 * A bound on the stack a parse and every walk of its tree can use, checked on the tokens before the
 * parser runs (ADR-024).
 *
 * The tree's depth is at most the operator tokens, plus the quantified variables, plus the
 * parenthesis depth, plus 3, so the two limits bound every recursion over the tokens and the tree.
 */
object NestingBound:

  /** Half the smallest parenthesis depth measured to overflow a 1 MiB interpreted JVM thread, rounded down to a power of two. */
  val MaxParenthesisDepth: Int = 64

  /** Keeps a tree within the limits under half the smallest tree depth measured to overflow evaluation on Node. */
  val MaxOperators: Int = 256

  /**
   * `Left` at the first token past either limit, `Right` otherwise. Unbalanced parentheses are left
   * to the parser to refuse.
   *
   * {{{
   * check(lex("((p))"))                                   // Right(())
   * check(lex("(" * 65 + "p" + ")" * 65))                 // Left(ParenthesesTooDeep(64))
   * check(lex(List.fill(258)("p").mkString(" \\/ ")))     // Left(TooManyOperators(256))
   * }}}
   */
  def check(tokens: List[Token]): Either[NestingFailure, Unit] =
    @tailrec
    def loop(rest: List[Token], depth: Int, operators: Int, inVariableList: Boolean): Either[NestingFailure, Unit] =
      rest match
        case Nil           => Right(())
        case token :: more =>
          val (d, ops, inList) = step(token, depth, operators, inVariableList)
          if d > MaxParenthesisDepth then Left(NestingFailure.ParenthesesTooDeep(MaxParenthesisDepth))
          else if ops > MaxOperators then Left(NestingFailure.TooManyOperators(MaxOperators))
          else loop(more, d, ops, inList)
    loop(tokens, 0, 0, false)

  /** The counts after one token. A quantifier's variable list runs from `forall` or `exists` to the next non-word token. */
  private def step(token: Token, depth: Int, operators: Int, inVariableList: Boolean): (Int, Int, Boolean) =
    (token, inVariableList) match
      case (Token.LParen, _)                    => (depth + 1, operators, false)
      case (Token.RParen, _)                    => (depth - 1, operators, false)
      case (Token.OpSym(_), _)                  => (depth, operators + 1, false)
      case (Token.Word("forall" | "exists"), _) => (depth, operators, true)
      case (Token.Word(_), true)                => (depth, operators + 1, true)
      case _                                    => (depth, operators, false)

end NestingBound
```

**Algebraic structure.** `check` is a validation, and it short-circuits at the
first failure. It is a left fold over the tokens with an early exit.

**Reachability under ADR-004.** `lexer.Token` is foundation, below `parser`.

### 5.2 The foundation refusal — `core/src/main/scala/parser/ParseError.scala`

`ParseError` gains one case. **Family:** the existing `enum ParseError`, whose
case `Lex(failure: LexFailure, position: SourcePosition)` has the same shape.

```scala
  /** The tokens crossed a nesting limit before parsing (ADR-024). The position is always `Unknown`. */
  case Nesting(failure: NestingFailure, position: SourcePosition)
```

`message` gains the arm `case Nesting(failure, _) => failure.message`.

### 5.3 The foundation boundary — `core/src/main/scala/parser/FOLParser.scala`

The signature of `parseWithLexer` is unchanged, and its body runs the check
between lexing and parsing:

```scala
  def parseWithLexer(
    s: String,
    lexer: List[Char] => List[Token],
  ): Either[ParseError, Formula[FOL]] =
    try
      val tokens = lexer(explode(s))
      NestingBound.check(tokens) match
        case Left(failure) => Left(ParseError.Nesting(failure, SourcePosition.Unknown))
        case Right(())     =>
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

Two scaladoc changes:

- `parseWithLexer`'s scaladoc gains: "`Left(ParseError.Nesting)` when the
  tokens cross a limit of `NestingBound`, before any parsing."
- `parseTokens`'s scaladoc gains: "Runs no nesting bound. A caller holding
  tokens runs `NestingBound.check` first."

### 5.4 The vague boundary — `core/src/main/scala/vql/error/ParseRefusal.scala`, `core/src/main/scala/vql/parser/VagueQueryParser.scala`

`ParseRefusal` gains one case. **Family:** the existing `enum ParseRefusal`,
whose case `Lexical(failure: LexFailure)` has the same shape.

```scala
  case Nesting(failure: NestingFailure)
```

`message` gains the arm `case Nesting(failure) => failure.message`. The import
`parser.NestingFailure` joins the file's existing foundation imports.

`VagueQueryParser.parse` keeps its signature. Its body becomes:

```scala
  def parse(s: String): Either[QueryError, ParsedQuery] =
    try
      val tokens = mergeDecimalTokens(Lexer.lex(StringUtil.explode(s)))
      NestingBound.check(tokens) match
        case Left(failure) => Left(refused(ParseRefusal.Nesting(failure)))
        case Right(())     =>
          val (query, remaining) = parseTokens(tokens)
          remaining match
            case Nil           => Right(query)
            case first :: more =>
              Left(refused(ParseRefusal.TrailingTokens(TokenKind.of(first), more.map(TokenKind.of))))
    catch
      // the four arms, unchanged
```

`refused` already builds `QueryError.ParseError(refusal, SourcePosition.Unknown)`.
`mergeDecimalTokens` keeps its signature and becomes tail-recursive:

```scala
  private def mergeDecimalTokens(tokens: List[Token]): List[Token] =
    @tailrec
    def loop(rest: List[Token], acc: List[Token]): List[Token] =
      rest match
        case Token.Word(a) :: Token.Dot :: Token.Word(b) :: more
          if a.nonEmpty && a.forall(_.isDigit) && b.nonEmpty && b.forall(_.isDigit) =>
          loop(more, Token.Word(s"$a.$b") :: acc)
        case t :: more => loop(more, t :: acc)
        case Nil       => acc.reverse
    loop(tokens, Nil)
```

`parse`'s scaladoc gains the `Nesting` refusal. `parseTokens`'s scaladoc gains
the same sentence as §5.3.

### 5.5 The lexer — `core/src/main/scala/lexer/Lexer.scala`

All three signatures are unchanged:

```scala
def lexwhile(prop: Char => Boolean, inp: List[Char]): (String, List[Char])
private def lexStringLit(inp: List[Char]): (String, List[Char])
def lex(inp: List[Char]): List[Token]
```

The bodies become iterative. Each returns what it returns today and throws
what it throws today, at the same character with the same `remaining`.

```scala
  def lexwhile(prop: Char => Boolean, inp: List[Char]): (String, List[Char]) =
    val (run, rest) = inp.span(prop)
    (run.mkString, rest)

  private def lexStringLit(inp: List[Char]): (String, List[Char]) =
    @tailrec
    def loop(rest: List[Char], acc: List[Char]): (String, List[Char]) =
      rest match
        case Nil                                 => throw new LexerError(LexFailure.UnterminatedLiteral, 0)
        case '\n' :: _                           => throw new LexerError(LexFailure.NewlineInLiteral, rest.length)
        case '"' :: after                        => (acc.reverse.mkString, after)
        case c :: _ if !literalChars.contains(c) => throw new LexerError(LexFailure.RefusedLiteralCharacter, rest.length)
        case c :: cs                             => loop(cs, c :: acc)
    loop(inp, Nil)

  def lex(inp: List[Char]): List[Token] =
    @tailrec
    def loop(inp: List[Char], acc: List[Token]): List[Token] =
      val (_, afterSpace) = lexwhile(space, inp)
      afterSpace match
        case Nil                               => acc.reverse
        case '"' :: cs                         =>
          val (content, rest) = lexStringLit(cs)
          loop(rest, Token.StringLit(content) :: acc)
        case c :: cs if punctChars.contains(c) => loop(cs, punctuationToken(c) :: acc)
        case c :: cs                           =>
          if alphanumeric(c) then
            val (toktl, rest) = lexwhile(alphanumeric, cs)
            loop(rest, Token.Word(c.toString + toktl) :: acc)
          else if symbolic(c) then
            val (toktl, rest) = lexwhile(symbolic, cs)
            loop(rest, Token.OpSym(c.toString + toktl) :: acc)
          else loop(cs, Token.OpSym(c.toString) :: acc)
    loop(inp, Nil)
```

The `alphanumeric` and `symbolic` tests stay `if`/`else`, because each is a
predicate over a character, not a test of which case a value holds.

The scaladoc changes leave every OCaml block verbatim under C11:

- Each of the three functions gains: "The Scala body is iterative, so the
  stack use is constant in the input's length (ADR-024)."
- The object's sentence "Key OCaml technique: accumulator-based recursion with
  token threading" becomes "The OCaml original recurses per token; this port
  loops with an accumulator."
- `lexwhile`'s note "Unchanged primitive … (ADR-007 C10 preserved at the
  character level)" becomes "Operates on `(String, List[Char])` regardless of
  the outer `lex` return type."

---

## 6. Implementation phases

Each phase compiles and passes the full suite on both platforms before the
next starts. The plan ships whole, as one release.

**Phase 1 — the lexer.**
1. `lexer/Lexer.scala` (§5.5) and `mergeDecimalTokens` in
   `vql/parser/VagueQueryParser.scala` (§5.4, the last code block).
2. `LexerSpec`:
   - The banner comment "lexwhile (unchanged primitive)" becomes "lexwhile".
   - Four tests are added. A 100 000-character word lexes to one `Token.Word`.
     100 000 spaces before `p` lex to one token. A 100 000-character quoted
     literal of `a`s lexes to one `Token.StringLit`. 20 000 repetitions of
     `p \/ ` lex to 40 000 tokens.
   - The existing `LexerError` tests keep their `remaining` assertions
     unchanged.

**Phase 2 — the bound and the boundaries.**
1. `parser/NestingBound.scala` (§5.1), `parser/ParseError.scala` (§5.2),
   `parser/FOLParser.scala` (§5.3), `vql/error/ParseRefusal.scala` and the rest
   of `vql/parser/VagueQueryParser.scala` (§5.4).
2. `NestingBoundSpec`, new, with these tests:
   - 64 nested parentheses give `Right(())`, and 65 give
     `Left(ParenthesesTooDeep(64))`.
   - 256 operators give `Right(())`, and 257 give `Left(TooManyOperators(256))`.
   - `forall x1 … x256 . p(x1)` gives `Right(())`, and 257 variables give
     `Left(TooManyOperators(256))`.
   - In `forall x . p(y) /\ q(z)`, the words after the `.` are not counted.
     With 255 further operators the input is accepted, and with 256 it is
     refused.
   - A `)` without a matching `(` gives `Right(())`.
   - Each case's `message`.
3. `FOLParserSpec`, with these tests:
   - T-014's input `"(" * 260 + "p(x)" + ")" * 260` gives
     `Left(ParseError.Nesting(ParenthesesTooDeep(64), SourcePosition.Unknown))`.
   - A 3 000-atom `\/` chain gives
     `Left(ParseError.Nesting(TooManyOperators(256), SourcePosition.Unknown))`.
   - `ParseError.Nesting`'s `message` equals the failure's `message`.
4. `VagueQueryParserSpec`, with these tests:
   - A scope of 300 negations gives
     `Left(QueryError.ParseError(ParseRefusal.Nesting(TooManyOperators(256)), SourcePosition.Unknown))`.
   - The header's three operators count. A scope of 253 negations parses, and
     254 are refused.

**Phase 3 — the pipeline at the limits.**
1. `VagueSemanticsTypedSpec`, end to end through parse, bind and evaluate. The
   catalog declares `DomainType(asset)`, the predicate `p: (asset)` and the
   function `f: (asset) -> asset`. The dispatcher's `f` returns its argument,
   and its `p` returns true.
   - The query of §4.4 parses, and its `NestingBound.check` is `Right(())`.
     `evaluateTyped` returns `Right`, and `satisfyingSet` of its scope returns
     `Right`.
   - The same query with one more `~` gives
     `Left(QueryError.ParseError(ParseRefusal.Nesting(TooManyOperators(256)), SourcePosition.Unknown))`.
   - With one more `f(` it gives `ParenthesesTooDeep(64)`.
2. `core/.jvm/src/test/scala/parser/NestingBoundStackSpec.scala`, new and
   JVM-only, because Scala.js has no threads. It runs the §4.4 query on a
   thread whose stack is 1 MiB: `VagueQueryParser.parse`, `evaluateTyped`,
   then `FOLParser.parse`, `FragmentCheck.check` and `FOLPrinter.print` of the
   scope. Each returns without an exception, and the test fails on any
   `Throwable`. On Scala.js, item 1 runs on Node's default stack and covers
   the same steps.

**Phase 4 — records, documents, version, the register entry.**
1. `docs/ADR-024.md`, new, "Parse Depth Bound", following `docs/ADR-00X.md`:
   - **Context:**
     - a thread's stack is fixed, and a recursive parse or tree walk uses
       frames in proportion to the input;
     - the frame size varies with the JIT state and the platform, so the size
       at which input overflows is not fixed;
     - a stack overflow is not a refusal and ends a ZIO process;
     - a refusal must not depend on the run.
   - **Decision:**
     1. the lexer is iterative;
     2. both `Either` boundaries run `NestingBound.check` on the tokens before
        parsing;
     3. the counts and why they bound the tree (§4.2);
     4. the limits and their margin (§2, settled point 2);
     5. a grammar construct that adds a tree level without an operator token
        extends the count, and a new recursive walk of the tree is measured
        against the margin.
   - **Code smells:**
     - catching `StackOverflowError` at a boundary;
     - a depth counter threaded through the combinators;
     - a limit set at the measured edge.
   - **Alternatives rejected:**
     - a counter inside the parser, which breaks C1 or C6 and misses the
       lexer;
     - catching the error, which is not deterministic and misses the `Pattern`
       case of §4.3;
     - an all-token count (§2, settled point 1).
   - **Cross-ADR:** ADR-007 and ADR-022, the two Layer 3 rows.
   - **Implementation table:** `parser/NestingBound.scala`,
     `parser/FOLParser.scala`, `vql/parser/VagueQueryParser.scala`,
     `lexer/Lexer.scala`.
2. `docs/ADR-007.md`:
   - C10's second sentence becomes: "`lexwhile`, `lex` and the literal scanner
     consume iteratively, and their OCaml originals stay in scaladoc (ADR-024)."
   - The review criterion gains a paragraph, "Iterative lexer and depth bound
     (recorded per this criterion)". It states the C10 departure, the user's
     ruling, that every lexer signature and throw holds, and that the bound
     runs in `FOLParser.parseWithLexer` before `parseTokens`, outside every
     combinator.
   - The cross-ADR content gains ADR-024.
3. `docs/ADR-022.md`:
   - §1's list of sets gains `NestingFailure`.
   - The implementation table gains `parser/NestingBound.scala` with
     `NestingFailure`.
   - The cross-ADR section gains ADR-024, the reason the position is
     `Unknown`.
4. `docs/ADR-002.md`: §2's code block is brought to §5.4's body.
5. `.claude/skills/vql-adr-constraints/SKILL.md`:
   - Layer 2 gains the ADR-024 row.
   - Layer 1, "Editing a file in the OCaml-ported foundation", gains an also
     row: ADR-024, if the lexer, a parser or a recursive walk of the tree
     changes.
   - Layer 1, "Changing the vague query grammar", gains ADR-024 in its always
     column.
   - Layer 1, "Changing evaluation", gains an also row: ADR-024, if a new
     recursion over the tree is added.
   - Layer 3 gains the rows ADR-024 × ADR-007 and ADR-024 × ADR-022.
   - Layer 4 gains no row (§3).
6. `docs/Architecture.md`:
   - The package map's `parser/` line gains `NestingBound, NestingFailure`.
   - The sentence "`Syntax`, `Trailing` and `Lex` each carry a typed value
     and a `SourcePosition`" becomes "`Syntax`, `Trailing`, `Lex` and
     `Nesting` each carry a typed value and a `SourcePosition`".
   - The record table gains ADR-024.
7. `CHANGELOG.md`, 0.20.0:
   - "Changed (breaking)" lists the two new cases, `ParseError.Nesting` and
     `ParseRefusal.Nesting`, and that an input past either limit is refused.
   - "Added" lists `parser.NestingBound` and `parser.NestingFailure`.
   - "Fixed" lists that lexing is stack-safe.
8. `docs/TODOS.md`, T-014:
   - Its status becomes "RESOLVED by `PLAN-parse-depth-bound`".
   - "The pass counts the tokens" becomes "The pass counts the operator
     tokens and quantified variables".
   - "names the limit crossed and the token index" becomes "names the limit
     crossed, with an unknown position".
   - "The token count bounds every recursion" becomes "That count bounds every
     recursion".
9. The register task-list entry of §10, in the repository `register`.
10. `build.sbt`: the user runs
    `sed -i 's/^ThisBuild \/ version      := .*/ThisBuild \/ version      := "0.20.0"/' build.sbt`.
    The user runs the release per `docs/RELEASE.md`.

---

## 7. Verification plan

The tests added or changed are listed in §6. These commands must be green:

```bash
sbt "folEngineJVM/testOnly lexer.LexerSpec parser.NestingBoundSpec parser.FOLParserSpec vql.parser.VagueQueryParserSpec vql.semantics.VagueSemanticsTypedSpec parser.NestingBoundStackSpec"
# expected: ends in [success]
sbt test
# expected: ends in [success]; both platforms, so Phase 3 item 1 runs on Node
```

The end-to-end test of Phase 3 item 1 is the integration verification
`docs/WORKING-INSTRUCTIONS.md` requires for a public surface change. It
reaches the bound through `VagueQueryParser.parse`, `FolModel`,
`VagueSemantics.evaluateTyped` and `VagueSemantics.satisfyingSet`.

After Phase 4, the mechanical check of `vql-adr-constraints` must print
nothing:

```sh
for f in docs/ADR-*.md; do grep -q "\`$(basename "$f")\`" .claude/skills/vql-adr-constraints/SKILL.md || echo "MISSING $f"; done
# expected: no output
```

---

## File inventory

- `core/src/main/scala/lexer/Lexer.scala`
- `core/src/main/scala/parser/NestingBound.scala` (new)
- `core/src/main/scala/parser/ParseError.scala`
- `core/src/main/scala/parser/FOLParser.scala`
- `core/src/main/scala/vql/error/ParseRefusal.scala`
- `core/src/main/scala/vql/parser/VagueQueryParser.scala`
- `core/src/test/scala/lexer/LexerSpec.scala`
- `core/src/test/scala/parser/NestingBoundSpec.scala` (new)
- `core/src/test/scala/parser/FOLParserSpec.scala`
- `core/src/test/scala/vql/parser/VagueQueryParserSpec.scala`
- `core/src/test/scala/vql/semantics/VagueSemanticsTypedSpec.scala`
- `core/.jvm/src/test/scala/parser/NestingBoundStackSpec.scala` (new)
- `docs/ADR-024.md` (new)
- `docs/ADR-007.md`
- `docs/ADR-022.md`
- `docs/ADR-002.md`
- `.claude/skills/vql-adr-constraints/SKILL.md`
- `docs/Architecture.md`
- `CHANGELOG.md`
- `docs/TODOS.md`
- `build.sbt` (the version line, run by the user)

Outside this repository, one file in `register`, written once in Phase 4:
`docs/dev/TODO.md`.

---

## 9. Consumer-document impact

- `docs/Architecture.md` lists `Syntax`, `Trailing` and `Lex` as the
  `ParseError` cases (line 131), and its package map omits the new types.
  Stale; Phase 4 item 6.
- `README.md` names no `ParseError` or `ParseRefusal` case. Not affected.
- `docs/VagueQuantifiers.md` states the query syntax and no size limit. Not
  affected. Every query within the limits parses as before.
- Scaladoc: `Lexer`, `lexwhile`, `lexStringLit`, `lex`,
  `FOLParser.parseWithLexer`, `FOLParser.parseTokens`, `VagueQueryParser.parse`
  and `VagueQueryParser.parseTokens`. Brought to state in Phases 1 and 2.

---

## 10. What register must do, named once

This is written into `register/docs/dev/TODO.md` as one item in Phase 4.

- `build.sbt` line 45: `vqlEngineVersion` to `0.20.0`.
- `modules/common/.../http/errors/QueryProblem.scala`. Two matches are
  exhaustive and gain an arm each:
  - the `ParseRefusal` match, around line 262, gains `ParseRefusal.Nesting(failure)`;
  - the `FormulaParseError` match, around line 303, gains
    `FormulaParseError.Nesting(failure, position)`.

  Each maps to a `ParseProblem` case register adds. The case holds the two
  `NestingFailure` cases and their limits, and no caller text.
- `PLAN-RISKTRANSFORM.md` §7.6.12 decision 20: the depth bound it waits on
  ships in 0.20.0, at 64 parentheses and 256 operators and quantified
  variables. A realistic 85-node predicate counts 84 operators.
