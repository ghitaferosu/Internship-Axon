# Venue Seeding Language + Caching — Handoff Notes for Claude Code

Companion to the earlier `README.md` (performer/event seeding endpoints) and
`venue-modeling-notes.md` (initial parked questions). This file covers
everything decided since then: the venue seeding DSL design, the lexer/
parser/AST plan, and the Redis caching strategy for venue layouts. Read
`venue-modeling-notes.md` first for background, then this file for the
concrete, settled design.

## Status

Design is settled enough to start implementing. Nothing here has been
coded yet — this is the spec to build from. Order of implementation should
be: Lexer → Parser → AST → Validator → SeatGenerator, each independently
testable, per the pipeline below.

## The venue seeding language

One venue per script. Hand-rolled lexer + recursive-descent parser (not
ANTLR/JFlex — chosen deliberately for learning purposes, grammar is small
enough not to need a parser generator).

### Grammar (EBNF)

```
program         := venueDecl sectionDecl+
venueDecl       := "VENUE" STRING

sectionDecl     := seatedSection | generalAdmissionSection

seatedSection   := "SECTION" IDENT "ROWS" range "SEATS" range curveClause? rotateClause?

generalAdmissionSection
                := "SECTION" IDENT "GENERAL_ADMISSION" "CAPACITY" INT

range           := INT ".." INT
curveClause     := "CURVE" signedInt
rotateClause    := "ROTATE" signedInt
signedInt       := ("+" | "-")? INT
```

Shape is derived from which clauses are present, not from an explicit
keyword (avoids the possibility of contradictory input like
`SHAPE GRID CURVE +2`):

| Clauses present | Resulting shape |
|---|---|
| neither `CURVE` nor `ROTATE` | `GRID` (straight rectangle) |
| `CURVE` only | `ARC` (rows grow, stay radially aligned) |
| `ROTATE` only | `ANGLED_GRID` (straight rectangle, rotated) |
| `GENERAL_ADMISSION ... CAPACITY ...` | `GENERAL_ADMISSION` (no individual `Seat` rows at all) |

**Validation rule: `CURVE` and `ROTATE` cannot both be present on the same
section** — decided against supporting curved+rotated combined sections
(geometrically plausible but adds real complexity to position derivation;
not worth it). Validator must reject with something like
`"Section {name}: CURVE and ROTATE cannot be combined"`.

`CURVE +2` semantics (confirmed against a real Ticketmaster seat map
screenshot): each row has 2 more seats than the previous row — row-length
growth, seats stay radially aligned, no stagger/offset numbering.

Example script:
```
VENUE "National Arena"
SECTION A ROWS 1..30 SEATS 1..40
SECTION B ROWS 1..20 SEATS 1..25 CURVE +2
SECTION C ROWS 1..15 SEATS 1..20 ROTATE +30
SECTION FLOOR GENERAL_ADMISSION CAPACITY 5000
```

### Meaning of `rowStart`/`rowEnd`/`seatStart`/`seatEnd`

- `rowStart`/`rowEnd` = the `ROWS` range — how many rows the section has.
- `seatStart`/`seatEnd` = the `SEATS` range for the *first* row only. If
  `curveStep` is set, later rows extend `seatEnd` by
  `(row - rowStart) * curveStep`; if `curveStep` is null, every row uses the
  same `seatStart..seatEnd` (this is what makes rectangular sections fall
  out of the same code path rather than needing a special case).

### Pipeline

```
raw text -> [Lexer] -> tokens -> [Parser] -> AST -> [Validator] ->
validated AST -> [SeatGenerator] -> List<Seat>
```

Each stage has its own error type so error reporting is precise:
`VenueScriptLexException`, `VenueScriptParseException`,
`VenueScriptValidationException` (line/col, no message dump). The
validator specifically should **collect all errors, not fail on the
first** — a bulk seeding tool should surface every problem in one pass
(duplicate section names, bad ranges, `CURVE`+`ROTATE` combos, non-positive
`CAPACITY`, etc.), not make the user fix-and-resubmit repeatedly.

### Token types

```java
enum TokenType {
    VENUE, SECTION, ROWS, SEATS, CURVE,
    GENERAL_ADMISSION, CAPACITY, ROTATE,
    IDENT, STRING, INT,
    DOTDOT, PLUS, MINUS,
    EOF
}

record Token(TokenType type, String text, int line, int col) {}
```

Note `GENERAL_ADMISSION` contains an underscore — the lexer's identifier-
character rule must include `_`, or this keyword will mis-tokenize.

### Lexer approach

Regex-pattern-array style (not char-by-char switch), patterns tried in
order at the current position using a `^`-anchored match:

```java
String[] patterns = {
    "\\.\\.",                    // DOTDOT
    "[a-zA-Z_][a-zA-Z0-9_]*",    // IDENT or keyword — resolved via map, see below
    "\"[^\"]*\"",                // STRING
    "\\d+",                      // INT
    "\\+",                       // PLUS
    "-"                          // MINUS
};
```

Keyword resolution happens **after** the identifier pattern matches, via a
lookup map — not as separate regex patterns per keyword:

```java
Map<String, TokenType> KEYWORDS = Map.of(
    "VENUE", TokenType.VENUE, "SECTION", TokenType.SECTION,
    "ROWS", TokenType.ROWS, "SEATS", TokenType.SEATS,
    "CURVE", TokenType.CURVE, "ROTATE", TokenType.ROTATE,
    "GENERAL_ADMISSION", TokenType.GENERAL_ADMISSION,
    "CAPACITY", TokenType.CAPACITY
);
// matched text -> KEYWORDS.getOrDefault(matched, TokenType.IDENT)
```

Line/col tracking: on every match, scan the consumed substring for `\n` —
if present, increment `line` and reset `col`; otherwise just advance `col`
by the matched length. Needed for precise error messages at every later
stage.

### AST

`sealed interface` so the compiler forces exhaustive handling of both
section kinds everywhere they're processed (validator, seat generator,
entity mapper):

```java
sealed interface SectionAst permits SeatedSectionAst, GeneralAdmissionSectionAst {}

record SeatedSectionAst(
    String name,
    int rowStart, int rowEnd,
    int seatStart, int seatEnd,
    Integer curveStep,     // null if not ARC
    Integer rotateAngle,   // null if not ANGLED_GRID
    int line, int col
) implements SectionAst {}

record GeneralAdmissionSectionAst(
    String name,
    int capacity,
    int line, int col
) implements SectionAst {}

record VenueAst(String name, List<SectionAst> sections) {}
```

## Database schema (venue/section/seat) — revised

Supersedes the earlier `Section`-less schema in `venue-modeling-notes.md`.
Identity (bookable seat rows) and geometry (render position) are
deliberately separated: geometry is NOT stored per-seat, it's derived at
read time from a handful of section-level parameters (same principle as
the language computing seat counts from `ROWS`/`SEATS`/`CURVE` rather than
listing every seat).

```
Venue
├── PK  VenueID
├──     name
├──     address
└──     noSeats

Section
├── PK  SectionID
├── FK  venueID
├──     name
├──     shape                 -- enum: ARC, GRID, ANGLED_GRID, GENERAL_ADMISSION
├──     rowStart, rowEnd      -- irrelevant for GENERAL_ADMISSION
├──     seatStart, seatEnd    -- irrelevant for GENERAL_ADMISSION
├──     curveStep (nullable)  -- ARC only
├──     rotateAngle (nullable)-- ANGLED_GRID only
├──     startAngle, endAngle  -- ARC rendering
├──     innerRadius           -- ARC rendering
├──     rowSpacing            -- ARC/GRID rendering
├──     originX, originY      -- GRID/ANGLED_GRID rendering
├──     colSpacing            -- GRID/ANGLED_GRID rendering
└──     capacity              -- GENERAL_ADMISSION only

Seat
├── PK  SeatID
├── FK  sectionID   -- NULL/absent for GENERAL_ADMISSION sections (no individual seats)
├──     row
└──     seatNumber
    UNIQUE(sectionID, row, seatNumber)
```

`GENERAL_ADMISSION` sections generate **no `Seat` rows at all** — capacity
is tracked only on `Section.capacity`. This changes the future double-
booking approach for GA tickets: not a per-seat row lock, but something
like `sold < capacity` checked against the section itself (exact mechanism
not yet designed — flag this as a TODO when the `Ticket` table gets built
in Week 3 work).

Rendering position is derived, not stored:
- ARC: `angle = startAngle + (seatNumber - seatStart) * angleStep`,
  `radius = innerRadius + (row - rowStart) * rowSpacing`, then
  `x = radius * cos(angle)`, `y = radius * sin(angle)`.
- GRID/ANGLED_GRID: `x = (seatNumber - seatStart) * colSpacing`,
  `y = (row - rowStart) * rowSpacing`, then rotate by `rotateAngle` if set.

Not yet decided: whether this derivation happens client-side (API sends
section params + seat list, frontend computes positions) or server-side
(API computes and returns x/y in the response DTO). Either is workable;
pick when building the read endpoint.

## Redis caching strategy for venue layouts (Challenge 4)

**What gets cached:** static layout only — section geometry + the list of
`Seat` (id, row, seatNumber) per section, keyed `venue:{venueId}:layout`.
**What does NOT get cached here:** per-event seat status (free/held/sold)
— that's a separate, fast-changing concern driven by the future `Ticket`
table and real-time updates (SSE/Kafka), not this cache.

**Strategy: cache-aside as the universal baseline, warming as an
optimization on top — not a replacement.**

- **Read path (always, regardless of warming):** check Redis for
  `venue:{venueId}:layout`. Hit → return directly. Miss → read from
  Postgres, serialize, write to Redis, return. This is the fallback that
  makes the system correct even if warming never ran (Redis flushed,
  restarted, warming failed, etc.) — must always exist, not just during
  initial rollout.
- **Write path (best-effort warming, via Kafka):** decided to implement
  this using Kafka rather than a direct in-process call, specifically
  because the system is expected to run multiple `event-service`
  instances — Kafka decouples "who created the venue" from "who populates
  the shared Redis cache."
  - New topic: `venue.layout.updated`.
  - Message payload is a **small trigger, not the full layout**:
    `{ "venueId": 4, "eventType": "CREATED" }`. Consumer re-reads the full
    layout from Postgres itself; don't put the whole section/seat payload
    in the Kafka message.
  - Producer (in the venue-creation service): after committing
    Venue+Section+Seat to Postgres, publish the trigger message. If the
    publish fails, log a warning — do NOT roll back the Postgres write.
    The read-path fallback above covers the gap.
  - Consumer (in `messaging/`, same `event-service` — no other service in
    the architecture needs this event): on receipt, read venueId, load
    full layout from Postgres, serialize, `SET` into Redis at
    `venue:{venueId}:layout`.
  - **Consumer must be idempotent** — Kafka is at-least-once delivery, the
    same message can arrive twice. This is automatically satisfied here:
    re-writing the same Redis key with the same recomputed value has no
    side effects, so no dedup logic is needed. This only holds because
    warming is "just a mirror" of Postgres, not an operation with
    cumulative effects — worth keeping in mind if the caching design ever
    changes.

## Next steps when resuming this thread

1. Decide server-side vs. client-side position derivation (flagged above).
2. Design the `Ticket`/capacity-check mechanism for `GENERAL_ADMISSION`
   sections when the `Ticket` table gets built (Week 3 work) — flagged as
   unresolved above.
3. Implement in order: Lexer -> Parser -> AST -> Validator -> SeatGenerator,
   each with its own tests, before wiring up the venue seeding endpoint or
   the Kafka producer/consumer.
