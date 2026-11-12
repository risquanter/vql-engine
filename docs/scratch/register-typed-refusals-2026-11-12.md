# vql-engine 0.18.0 for register — handoff

**Date:** 2026-11-12
**vql-engine version:** `0.18.0`, from `0.17.0`. Breaking, a pre-1.0 minor
bump. Register pins `0.18.0` when adapting.
**Scope:** every refusal the engine returns is a typed value, and each error is
declared once. This note is written to be handed to the register session as it
stands. It serves step 5.7 of register's
`docs/dev/plans/PLAN-UES-UNIFIED-ERROR-SHAPE.md`.

Register paths are under `modules/`. Line numbers may drift, so match on symbol
names.

---

## 1. The literal alphabet excludes the apostrophe

The user ruled on 2026-11-11 that the apostrophe is not part of the literal
alphabet. A quoted literal's content is `[A-Za-z0-9 /_.+-]*`. Any other
character inside quotes is refused at the lexer, with its offset.

A register type for a literal's content that admits `'` is wider than what the
engine can send. A word, which is unquoted text, still admits `'`.

---

## 2. Every case, its fields, and the input it comes from

### The text types

| Type | Content |
|---|---|
| `lexer.WordText` | `[A-Za-z0-9_']+` |
| `lexer.DecimalText` | digits, `.`, digits |
| `lexer.LiteralText` | `[A-Za-z0-9 /_.+-]*` |
| `lexer.VariableText` | `Word(WordText)` or `Decimal(DecimalText)` |
| `lexer.SymbolText` | `Word(WordText)`, `Decimal(DecimalText)` or `Operator(GrammarSymbol)` |
| `vql.id.TypeId`, `vql.id.SymbolName` | a non-empty string register's own catalog declared |

Each opaque type is read through `.value`. `VariableText.value` and
`SymbolText.value` give the text.

### Tokens

```scala
enum TokenKind:
  case Word(text: WordText)
  case Decimal(text: DecimalText)
  case Literal(content: LiteralText)
  case Symbol(symbol: GrammarSymbol)   // punctuation and the 16 known operators
  case UnknownOperator                 // a symbolic run that is no GrammarSymbol; no text
  case StrayCharacter                  // a character outside every lexer class; no text

enum Found:
  case Token(kind: TokenKind)
  case EndOfInput

enum SourcePosition:
  case At(offset: Int)                 // a UTF-16 code-unit offset into the input
  case Unknown
```

### Query text: `QueryError.ParseError(refusal: ParseRefusal, position: SourcePosition)`

Only `Lexical` reports `At`. Every other case reports `Unknown`.

```scala
enum ParseRefusal:
  case QuantifierExpected(found: List[TokenKind])        // at most 7 tokens
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

enum QuantifierPart: Numerator, Denominator
enum ParseContext:   RangeFormula, ScopeFormula, AnswerVariables, EndOfAnswerVariables, Denominator, ToleranceOrEnd

enum SyntaxFailure:
  case FormulaExpected
  case QuantifierBodyExpected(found: Found)
  case AtomExpected(found: Found)
  case TermExpected(found: Found)
  case ClosingBracketExpected(bracket: TokenKind, found: Found)
  case NotAnInfixAtom

enum LexFailure: UnterminatedLiteral, NewlineInLiteral, RefusedLiteralCharacter
```

`ParseError.input` and its `context` map are removed.

### Formula text: `parser.ParseError` from `FOLParser.parse`

```scala
enum ParseError:
  case Syntax(failure: SyntaxFailure, position: SourcePosition)
  case Trailing(first: TokenKind, rest: List[TokenKind], position: SourcePosition)
  case Lex(failure: LexFailure, position: SourcePosition)
```

### Query text, the range rules: `QueryError.ValidationError(problem, position)`

```scala
enum ValidationProblem:                                              // in object QueryError
  case QuantifiedVariableNotInRange(variable: VariableText, rangeVariables: Set[VariableText])
  case RangeVariablesNotAnswered(rangeVariables: Set[VariableText], answerVariables: List[VariableText])
  case MissingAnswerValue(variable: VariableText)                    // answer tuple
  case AnswerSortMismatch(variable: VariableText, expected: TypeId, actual: TypeId)  // answer tuple
```

`RangeVariablesNotAnswered.rangeVariables` is the range's free variables other
than the quantified one. The variables with no answer are
`rangeVariables -- answerVariables`. The first two cases come from query text,
and the last two from the answer tuple. Every site passes `SourcePosition.Unknown`.

### Query text read against the catalog: `QueryError.BindError(details: List[TypeCheckError])`

```scala
enum TypeCheckError:                                                 // in vql.error
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

enum MismatchSite:
  case Variable(name: VariableText)
  case Constant(name: LiteralText)
  case FunctionReturn(name: SymbolText)

enum SymbolKind: Predicate, Function
```

A `BindError` has two possible causes the engine cannot tell apart: the text
names something the catalog lacks, or the catalog changed under text that once
bound. Register knows which, because it knows where the text came from.

`QueryError.UnknownConstantOrLiteralError(name: LiteralText)` comes from query
text read against the catalog.

### The catalog and the model

```scala
case class ModelValidationError(details: List[RuntimeModelError])
enum RuntimeModelError:                                              // in vql.error
  case MissingFunctionImplementation(name: SymbolName)
  case MissingPredicateImplementation(name: SymbolName)
  case MissingDomainForType(typeName: TypeId)

case class DomainNotFoundError(typeName: TypeId, availableTypes: Set[TypeId])
```

### Evaluation: `QueryError.EvaluationError(failure: EvaluationFailure)`

```scala
enum EvaluationFailure:                                              // in object QueryError
  case PredicateRefused(name: SymbolName, dispatcherMessage: String) // register's dispatcher
  case FunctionRefused(name: SymbolName, dispatcherMessage: String)  // register's dispatcher
  case VariableSortMismatch(variable: VariableText, expected: TypeId, actual: TypeId)
```

`dispatcherMessage` is the text register's own dispatcher returned.
`VariableSortMismatch` and `UnboundVariableError(variableName: VariableText,
boundVariables: Set[String])` come from the answer tuple or the model's
domains. `boundVariables` includes the keys of register's answer tuple, so it is
the one refusal field that holds caller text as a `String`. `cause` and
`context` of `EvaluationError` are removed.

---

## 3. Sentences that differ from 0.17.0

Every `message` and `formatted` is byte-identical to 0.17.0, except these:

1. A formula failure inside a vague query loses the prefix `Unexpected error
   during parsing: `. `parse_atom` is reported as `parse_atom`.
2. An unknown operator run prints as `unknown operator`, and a stray character
   as `unexpected character`, where 0.17.0 printed the characters.
3. `ParseError.formatted` has no `Input:` line and no context lines.
   `ValidationError.formatted` has no context lines.
4. The refused-literal-character sentence is new: `Lexer error: String literal
   holds a character outside its alphabet`.
5. Two quantifier sentences are new: `Fraction <k>/<n> must lie in [0, 1] with
   a positive denominator` and `Tolerance <tolerance> must lie in [0, 1]`.
6. The range-variable sentence joins its variables in the order of a
   `Set[VariableText]`. For more than four variables that order can differ from
   0.17.0.

---

## 4. A quantifier outside its bounds is refused

`Q[op]^{k/n}[ε]` is refused when `n <= 0`, when `k < 0` or `k > n`, or when
`ε < 0` or `ε > 1`. The threshold `k/n` is a target for a proportion, which
lies in [0, 1], so a quantifier outside those bounds has no mathematical
meaning. An example or a test that encodes one needs correcting. The engine
does not.

A scan on 2026-11-11 of every quantifier literal in register's source, tests
and documents found 165, none out of range. The scan read literal text, so a
quantifier assembled at run time is not covered. Run register's integration
tests against 0.18.0, and read each failure against this rule.

The `Quantifier` case constructors are closed. Build with
`Quantifier.mkAbout`, `mkAtLeast` and `mkAtMost`.

---

## 5. A predicate or function name can be an operator's text

An infix relation parses to an atom named by its operator, and an infix operator
to a function application named by it. `x < 5` is the atom `<`, and `x + 1` is
the function `+`. So `UnknownPredicate.name`, `UnknownFunction.name`,
`ArityMismatch.symbol` and `MismatchSite.FunctionReturn.name` can hold
`SymbolText.Operator`. The predicate operators are `= < <= > >=`, and the
function operators are `:: + - * / ^`. A register type that admits only a word
refuses them.

---

## 6. A bind error is a `TypeCheckError` on both of register's paths

`QueryBinder.bind` and `QueryBinder.bindSatisfyingFormula` return the same
`TypeCheckError` values that `BindError.details` holds. Register writes one
translation.

- `QueryProblem.fromQueryError`: the `BindError` arm matches
  `TypeCheckError.UnparseableConstant(_, sort, _)` and reads `sort.value`, where
  it matched `BindErrorDetail.UnparseableConstant(_, sortName, _, _)`. A
  detail's `rendered` is `message`.
- `MitigationScopeResolverLive.fromTypeCheckError`: `TypeMismatch`'s third field
  is `site`, `ArityMismatch` has `kind` after `symbol`, `TypeNotQuantifiable`
  holds a `TypeId`, and every name is read through `.value`.
- `QueryProblem.fromQueryError`: the `ParseError` and `ValidationError` arms read
  `refusal`, `problem` and `position`, one arm per case.
- `TargetingPredicate.create` and `MitigationScopeResolverLive`: the
  `parser.ParseError` cases carry typed values.
- `QueryProblemFromQueryErrorSpec` builds the typed fields above.

The import lines that change:

```scala
// was: import vql.typed.TypeCheckError
import vql.error.TypeCheckError          // MitigationScopeResolverLive, BinderIntegrationSpec, RiskTreeKnowledgeBaseSpec

// was: import vql.error.BindErrorDetail
import vql.error.TypeCheckError          // QueryProblem
```

`vql.typed.TypeId` and `vql.typed.SymbolName` still resolve, so those imports
are unchanged. Register builds no tree, so the closed tree constructors change
none of its code.
