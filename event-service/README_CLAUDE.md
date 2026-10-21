# event-service — Handoff Notes for Claude Code

This replaces the previous version of this file, which had drifted out of sync
with the repo (wrong package name, wrong table names, claimed things were
"not yet built" that already existed). Everything below was verified against
the actual code as of this writing. Companion doc:
**`venue-language-and-caching.md`** — the *original* design spec for the venue
seeding DSL and Redis caching strategy. **Its grammar/schema sections are now
superseded by the simplification described below (ARC/ANGLED_GRID were
dropped, and later `GENERAL_ADMISSION` was dropped too) — read this file's
account of the current grammar, not that doc's, if the two disagree.** The
Redis/Kafka caching sections of that doc are still accurate as a
forward-looking spec (nothing built yet either way).

There is a git branch, `checkpoint/venue-language-with-arc`, pinned at the
commit right before the ARC/ANGLED_GRID removal — the full version with
curved sections, in case that work is ever wanted back.

## What this service is

`event-service` is one microservice in a larger Event Ticketing System
(concerts/theatre/sports ticketing platform). Other services: `user-service`
(auth/JWT), `app-gateway` (routing). This service owns venues, seat layouts,
performers, events, and eventually tickets/orders/payments, search, and
real-time seat status via Kafka.

Stack: Spring Boot 3.5, Java 21, PostgreSQL (`event_db`), Flyway, Redis
(not yet wired), Kafka (not yet wired). Base package:
`ro.axonsoft.services.event`. Local Postgres expected at `localhost:5432`,
db `event_db`, user/password `user`/`secret` (overridable via
`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`). No `docker-compose.yml`
in the repo yet — you need your own Postgres to run `EventApplicationTests`
or the app itself; the pure unit test suites below don't need it.

## Status summary

| Area | State |
|---|---|
| Domain model (`model/`) | Built; `Seat` now FKs to `Section` per the design doc (see "Recently resolved" below) |
| Repositories (`repository/`) | Built: `VenueRepository`, `SeatRepository`, `SectionRepository`, `PerformerRepository`, `EventRepository` (+ `EventSpecifications`), `EventPerformerRepository` |
| Performer/event seeding endpoints (`feedingJob/`) | **Done** (still no tests — see below) |
| Venue seeding DSL (`language/`) | **Done**, simplified twice over: Lexer → Parser → AST → Validator → SeatGenerator, `GRID`-only now — no more ARC/ANGLED_GRID, and no more `GENERAL_ADMISSION`/floor sections either. Every section is seated with a mandatory direction. All independently unit-tested (19 tests, all green) |
| Flyway migrations | `V1__Initialize.sql` + `V2__Sections.sql` + `V3__RemoveArcAndAngledGrid.sql` + `V4__Tickets.sql` (merged from the `denis` branch — tickets table, unrelated to the venue DSL). **No migration drops `sections.shape`/`sections.capacity`** even though the entity/code no longer use them — see "GENERAL_ADMISSION removed too" below, this is a known gap, not an oversight |
| Venue seeding HTTP endpoint | **Done**: `POST /admin/venues` — DSL text in, persists `Venue`/`Section`/`Seat`, returns metadata (no seats) as JSON. See "Venue seeding endpoint" below |
| Venue layout read endpoints (`layout/`) | **Done**: `GET /venues/{id}/layout` (sections, no seats) + `GET /sections/{id}/seats` (one section's seats, on demand) — see "Venue layout read endpoints" below |
| Rendering/geometry (`layout/SectionGeometryCalculator`) | **Done**, center-point + compass-direction formula, computed fresh on every read — **not** persisted to `Section`'s `rowSpacing`/`originX`/`originY`/`colSpacing` columns, which remain unused `0` placeholders. See "Venue layout read endpoints" below |
| Seat-map frontend (`frontend/`) | **Done**: React + Vite, click-a-section-to-see-its-seats, bundled into the jar via `mvn clean package -Pfrontend`. See "Seat-map frontend" below |
| Redis caching (Challenge 4) | **Not built at all** — no config, no read-through, no cache key logic |
| Kafka warming (`venue.layout.updated`) | **Not built at all** — no topic, no producer, no consumer |
| Ticket/order/payment domain | `Ticket`/`TicketStatus` entities + `TicketRepository` + `tickets` table (`V4__Tickets.sql`) exist (merged from `denis` branch); no service/endpoint logic yet — otherwise not started (Week 3 per original proposal) |
| Auth (ADMIN role on `/admin/**`) | **Not built** — admin endpoints are currently wide open; blocked on `user-service` JWT roles support (unconfirmed whether ready) |

## Already built, in detail

### Performer + event seeding (`feedingJob/`)

- `POST /admin/performers` — `DataFeedingPerformersController` →
  `PerformerSeedingService.seedPerformers(List<PerformerSeedingRequestDTO>)`,
  bulk `saveAll`, returns `201` + `List<PerformerSeedingResponseDTO>`.
- `POST /admin/events` — `DataFeedingEventsController` →
  `EventSeedingService.seedEvents(List<EventSeedingRequestDTO>)`. One
  `@Transactional` method for the whole batch (all-or-nothing). Per event:
  looks up the venue (`VenueNotFoundException` if missing), builds the
  `Event` via its constructor which always forces `status = DRAFT` (the DTO
  has no `status` field at all — client can never set it), then for each
  nested performer appearance looks up the performer
  (`PerformerNotFoundException` if missing), validates
  `endHour.isAfter(startHour)` (`InvalidPerformerAppearanceException` if
  not), and creates the `EventPerformer` link.
- `exception/GlobalExceptionHandler` (`@RestControllerAdvice`) maps
  `VenueNotFoundException`, `PerformerNotFoundException`, and
  `InvalidPerformerAppearanceException` to `400` with the exception message
  as the body — no raw 500s for these cases.
- Still no tests for this slice (performer/event seeding) — worth adding
  before it's trusted in anger, especially the atomicity/rollback behavior.
  `VenueSeedingServiceTest` (below) is the only `feedingJob` test that
  exists so far.

### Venue seeding endpoint (`feedingJob/`)

`POST /admin/venues` — `DataFeedingVenuesController` →
`VenueSeedingService.seedVenue(VenueScriptRequestDTO)`.

- **Request** is `VenueScriptRequestDTO(String address, String script)` —
  `script` is the raw DSL text (`VENUE "name" SECTION A NORTH ROWS 1..30 ...`).
  The grammar has no clause for a venue's address (it never needed one for
  seat-generation purposes), so `address` rides in as a plain sibling JSON
  field rather than extending the grammar for one scalar. `noSeats` is
  never supplied by the client at all — it's computed as the sum of
  `seatCount` across every section (every section is seated now, so nothing
  is excluded).
- **Pipeline**: `Lexer → Parser → Validator` (same as `language/`'s own
  tests) produces a validated `VenueAst`, `SeatGenerator` produces
  `Map<sectionName, List<GeneratedSeat>>` from that same AST. The service
  then maps the AST into `VenueSeedingRequestDTO` (+ nested
  `SectionSeedingRequestDTO` per section) — a persistence-facing DTO
  decoupled from the `language` package's AST types, built by the service
  itself rather than deserialized from client JSON (the client never sends
  this shape directly). There's no `shape` field anywhere anymore — one
  section shape (`GRID`) is the only one that exists, so tracking it was
  pointless (see "GENERAL_ADMISSION removed" below).
- **Persistence**: saves `Venue`, then each `Section`, then that section's
  `Seat` rows (translated from `GeneratedSeat`) — straight to the DB, never
  round-tripped through JSON.
- **Response** (`VenueSeedingResponseDTO` + nested
  `SectionSeedingResponseDTO`) mirrors the request DTO shape but adds
  persisted `id`s and a `seatCount` per section, and **never includes
  actual seats** — just the count. Every field is always present now (no
  more per-shape nullability to worry about — there's only one shape).
- **Scope gap, intentional for now**: `Section`'s rendering columns
  (`rowSpacing`, `originX`, `originY`, `colSpacing`) are **not** set by
  this endpoint at all — nothing computes real values for them yet. See
  "Rendering/geometry: still unresolved" below for the actual plan (center
  point + compass direction), which isn't implemented yet either. They're
  persisted at the Java default (`0`) for now — don't read anything into
  that `0`, it means "not decided yet."
- `GlobalExceptionHandler` also maps `VenueScriptLexException`/
  `VenueScriptParseException`/`VenueScriptValidationException` to `400`,
  alongside the three `feedingJob` exceptions it already handled.
- **Side effect**: `VenueAst`/`SeatedSectionAst` in `language/` had to
  become `public` (were package-private) so `feedingJob` could reference
  them — each is its own file (Java only allows one public top-level type
  per file). There used to also be a sealed `SectionAst` interface with a
  second implementation, `GeneralAdmissionSectionAst`; both are gone now
  that `GENERAL_ADMISSION` doesn't exist, and `VenueAst.sections()` is just
  `List<SeatedSectionAst>` directly — no more sealed-interface dispatch
  anywhere in `language/` or `VenueSeedingService`.
- `VenueSeedingServiceTest` (Mockito, no DB needed): full example-venue
  script end-to-end (asserts `noSeats`, per-section `direction`/`seatCount`,
  and that `Seat`s actually get saved with the right counts), plus one test
  confirming an invalid script (two sections sharing a direction) throws
  before touching any repository.

### Venue seeding DSL (`language/`)

```
raw text -> [Lexer] -> List<Token> -> [Parser] -> VenueAst ->
[Validator] -> validated VenueAst -> [SeatGenerator] -> Map<sectionName, List<GeneratedSeat>>
```

**ARC/ANGLED_GRID removed** (deliberate simplification — see git history /
`checkpoint/venue-language-with-arc` for the removed version): `CURVE` and
`ROTATE` are gone from the grammar entirely, along with `curveStep`/
`rotateAngle` on the AST, `Shape.ARC`/`ANGLED_GRID`, and the curve row-growth
math in `SeatGenerator`. Reason: rendering position for ARC/ANGLED_GRID had
no defined source anywhere (see "Rendering/geometry" below) — real
angle/radius/rotation values were never derivable from the grammar, they
were always going to be either hardcoded placeholders or require a whole
separate authoring tool, and supporting them added real complexity (signed
ints, `CURVE`+`ROTATE` mutual-exclusion validation, per-row seat-count
growth) for a feature whose actual rendering was unsolved anyway.

**`GENERAL_ADMISSION` removed too** (later simplification, same branch):
`CAPACITY` is gone from the grammar, `GeneralAdmissionSectionAst` and the
sealed `SectionAst` interface it shared with `SeatedSectionAst` are deleted,
and `VenueAst.sections()` is now plainly `List<SeatedSectionAst>`. Every
section the DSL can describe is seated, GRID-shaped, and has a mandatory
direction — there's no more "floor" concept and no `Shape` type at all
(`model/Shape.java` is deleted; `Section.shape`/`Section.capacity` are gone
from the entity and DTOs). **No Flyway migration drops the DB columns** —
deliberate, not an oversight: `sections.shape` is still `NOT NULL` in
Postgres per `V2`/`V3`, so seeding a venue against a DB that's actually run
`V1`-`V4` will currently fail on that `NOT NULL` constraint (the app never
sets a value for a column it doesn't know about anymore). Whoever picks this
back up needs either a new migration (`V5`, since `V4` is taken by the
tickets table) dropping `shape`/`capacity` and the `chk_sections_shape`
check, or some other resolution — this is unresolved, not swept under the
rug.

**Every section requires a compass direction**:
`SECTION A NORTH ROWS 1..30 SEATS 1..40` — one of `NORTH`/`SOUTH`/`EAST`/
`WEST`. This is the anchor for the (not yet implemented) center-based
rendering scheme — see "Rendering/geometry" below. **"Levels" (multiple
sections sharing one direction, e.g. a lower + upper tier) are deliberately
not supported yet** — the `Validator` rejects two sections claiming the same
direction. Planned to be relaxed once the plain-grid version is confirmed
working.

- **`Lexer`** — regex-array style, `"^" + pattern` tried in order; `IDENT`
  resolves to a keyword via the `KEYWORDS` map (`NORTH`/`SOUTH`/`EAST`/`WEST`).
  `PLUS`/`MINUS` token types were removed along with `CURVE`/`ROTATE` —
  nothing in the grammar needs signed integers anymore (`ROWS`/`SEATS` are
  plain unsigned `INT`). Tracks line/col across newlines. Throws
  `VenueScriptLexException` on an unrecognized character. 5 tests in
  `LexerTest`.
- **`Parser`** — recursive-descent, one method per grammar rule.
  `parseDirection()` requires exactly one of `NORTH`/`SOUTH`/`EAST`/`WEST`
  right after the section name; throws `VenueScriptParseException` (with
  line/col) if it's missing or anything else is found there (including the
  old `GENERAL_ADMISSION` keyword, which now just reads as an unexpected
  identifier). 6 tests in `ParserTest`.
- **`Validator`** — collects *all* errors in one pass into a
  `List<ValidationError>` (duplicate section names, `rowStart > rowEnd` /
  `seatStart > seatEnd`, **duplicate direction across sections**) and only
  throws `VenueScriptValidationException` once, at the end, carrying every
  error found — never fail-fast. Returns the same `VenueAst` on success so
  it composes: `seatGenerator.generate(validator.validate(parser.parse(tokens)))`.
  6 tests in `ValidatorTest`, including one confirming 4 simultaneous errors
  surface together from one bad script.
- **`SeatGenerator`** — `generate(SeatedSectionAst)` returns
  `List<GeneratedSeat>` (a plain `record(row, seatNumber)`, not the JPA
  `Seat` entity — the translation happens in `VenueSeedingService`). Every
  row is `seatStart..seatEnd`, no per-row variation. `generate(VenueAst)`
  returns `Map<sectionName, List<GeneratedSeat>>` for every section — no
  more skipping logic, since every section now generates seats. 2 tests in
  `SeatGeneratorTest`.

All 19 `language` tests pass (`mvn -o test -Dtest=LexerTest,ParserTest,ValidatorTest,SeatGeneratorTest`).
`EventApplicationTests` (the one `@SpringBootTest`) requires a live Postgres
and will fail without one — that's expected/unrelated to this work.

### Venue layout read endpoints (`layout/`)

This was flagged for a while as a real hole in the *original* design doc:
the ARC rendering formula used `angleStep`, which wasn't even a stored
column, and `startAngle`/`endAngle`/`innerRadius`/`originX`/`originY`/
`rowSpacing`/`colSpacing` had no stated source anywhere — not the grammar,
not a default, not a separate tool. Dropping ARC/ANGLED_GRID sidesteps the
angle/radius half of that problem; the plan below (now implemented) covers
the GRID half.

**`SectionGeometryCalculator`** (pure function, no DB/Spring) implements the
previously-agreed plan: one center point represents the middle of the
field/stage (there's no `GENERAL_ADMISSION` `Section` to represent it as
data — it's purely an implicit reference point for the math). Each section
is anchored to its `direction`; `compute(Direction)` returns
`(originX, originY, rowSpacing, colSpacing)` — fixed constants
(`ROW_SPACING = 24`, `COL_SPACING = 20`, `CENTER_CLEARANCE = 150`, arbitrary
SVG units) placed on the corresponding axis/side. **Important**: this is
computed **fresh on every read**, not persisted — `Section`'s own
`rowSpacing`/`originX`/`originY`/`colSpacing` columns are never read or
written by this code path and remain unused `0` placeholders (a candidate
for later becoming a genuine cache, e.g. written once by a Kafka consumer
per the original caching design doc, rather than recomputed per request —
not done, since Redis/Kafka aren't wired yet either). No "levels" handling
(matches the `Validator`'s current one-per-direction rule — not
over-building for a case that can't occur yet). 5 tests in
`SectionGeometryCalculatorTest`, one per direction plus a shared-spacing
check.

Per-seat position (`(seatNumber - (seatStart + seatEnd) / 2) * spacing` for
the perpendicular axis, `(row - rowStart) * spacing` for the direction axis)
is **not** computed server-side at all — sending coordinates for every seat
would make the layout payload O(seats) instead of O(sections). It's
computed client-side instead, in `frontend/src/geometry.js`, using the same
convention — see "Seat-map frontend" below.

**Endpoints**, both public (not under `/admin/**` — these are reads a
ticket-buying client will eventually need too, unlike the seeding
endpoints), constructor injection, no class-level `@RequestMapping`,
matching `DataFeedingVenuesController`'s style:
- `GET /venues/{venueId}/layout` — `VenueLayoutController` →
  `VenueLayoutService.getLayout`. Returns `VenueLayoutResponseDTO` (venue
  metadata + every section's geometry/row-seat ranges/`seatCount`) —
  **never actual seats**.
- `GET /sections/{sectionId}/seats` — `SectionSeatsController` →
  `VenueLayoutService.getSeats`. Returns that one section's seats
  (`List<SeatResponseDTO(row, seatNumber)>`) via the already-existing
  `SeatRepository.findBySectionId`.
- New `SectionRepository.findByVenueId(Long)` (the repository previously had
  zero custom query methods). New `SectionNotFoundException` (same
  one-arg-`Long` pattern as `VenueNotFoundException`), registered in
  `GlobalExceptionHandler` alongside the rest — same `400` status, keeping
  the app's existing (if debatably REST-y) convention rather than
  introducing a one-off `404` for just these two endpoints.
- `VenueLayoutServiceTest` (Mockito, no DB): DTO mapping including computed
  geometry, not-found paths for both venue and section.

"Levels" (multiple sections sharing one direction, e.g. a lower + upper
tier) are still deliberately not supported — the `Validator` rejects two
sections claiming the same direction, and `SectionGeometryCalculator` has no
notion of stacking multiple sections outward from each other. Both would
need to change together if levels land.

### Seat-map frontend (`frontend/`)

New `frontend/` directory at repo root (sibling of `src/`, `pom.xml`) — a
React + Vite app, plain JS (not TypeScript), minimal dependencies (`react`,
`react-dom`, `vite`, `@vitejs/plugin-react` — no state library, no axios,
`fetch()` directly), matching `feeding-ui.html`'s lack of dependencies in
spirit even though it's a different stack.

- `frontend/src/api.js` — thin `fetch()` wrappers for the two endpoints
  above.
- `frontend/src/geometry.js` — client-side mirror of
  `SectionGeometryCalculator`'s convention: `seatPosition(section, row,
  seatNumber)` computes one seat's `(x, y)`; `sectionBoundingBox(section)`
  derives a section's overview rectangle from its four corner seats, without
  fetching any seats.
- `frontend/src/SeatMap.jsx` — renders an SVG venue overview: one rectangle
  per section (from `sectionBoundingBox`, labeled with the section name),
  **no seats rendered for the whole-layout view**. Clicking a section fetches
  (and caches in state) that section's seats and renders them as circles via
  `seatPosition`; other sections dim to stay visible as context. Clicking the
  expanded section again collapses it back to a rectangle.
- `frontend/src/App.jsx` — a venue-ID input + "Load" button (there's no
  `GET /venues` list endpoint to populate a dropdown from), matching
  `feeding-ui.html`'s manual-input style.

**Build integration**: `pom.xml` has a `frontend` Maven profile (**not
active by default**) wrapping `frontend-maven-plugin` (installs a local
Node `v20.17.0`, `npm install`, `npm run build`, all bound to
`generate-resources`). Deliberately gated behind a profile rather than
always-on: `generate-resources` runs before `test` in the default lifecycle,
so an unconditional binding would make the documented offline workflow
(`mvn -o test -Dtest=...`) try to `npm install` with no network and break.
Plain `mvn test`/`mvn package` are completely unaffected by any of this.
`vite.config.js` sets `build.outDir` straight to
`../src/main/resources/static/seat-map` (`base: '/seat-map/'`) so no
separate copy step is needed — Spring Boot's default static-resource
handling serves it at `/seat-map/` once bundled. Verified: `mvn clean
package -Pfrontend` succeeds end-to-end and the resulting jar contains
`BOOT-INF/classes/static/seat-map/index.html` + built JS bundle. `.gitignore`
excludes `frontend/node_modules/` and the generated
`src/main/resources/static/seat-map/` output (regenerated by the build, not
committed — same treatment as `target/`).

For local dev without the bundled jar: `cd frontend && npm install && npm
run dev` — `vite.config.js`'s dev-server proxy forwards `/venues` and
`/sections` requests to `http://localhost:8081` so the app can hit a locally
running Spring Boot instance with no CORS config needed (none exists in the
backend, and none was added).

## Recently resolved: Seat/Section reconciliation

`Seat` used to FK straight to `Venue` with a free-text `section` column
(the pre-`Section` schema). It's now aligned with the design doc:

- `Seat.java`: `@ManyToOne Section section` (not `Venue`), `row`,
  `seatNumber`; unique on `(section_id, row_number, seat_number)`. The old
  `venue`/`section` (String) fields and constructor arg are gone.
- `SeatRepository`: `findByVenueId`/`countByVenueId` → `findBySectionId`/
  `countBySectionId`.
- `V2__Sections.sql` added: creates `sections`, then re-keys `seats` —
  drops `venue_id`/`section`, adds `section_id BIGINT NOT NULL REFERENCES
  sections(id)`, new unique constraint. Left `V1` untouched rather than
  editing it (never edit an already-applied migration; add a new one).
  `V3__RemoveArcAndAngledGrid.sql` (later) drops the ARC-only columns
  (`curve_step`/`start_angle`/`end_angle`/`inner_radius`/`rotate_angle`),
  narrowed the `shape` check to `GRID`/`GENERAL_ADMISSION`, and added
  `direction`. `V4__Tickets.sql` (merged from the `denis` branch, unrelated
  to venues) adds the `tickets` table. **No migration removes `shape`/
  `capacity`** from `sections` — see "GENERAL_ADMISSION removed too" above
  for why that's a real, currently-open gap rather than a finished cleanup.

**Resolved**: `V1`-`V3` were run against a live Postgres and verified clean
(`EventApplicationTests`, the one `@SpringBootTest`, passes: Flyway applies
them in order and Hibernate's `ddl-auto: validate` accepts the resulting
schema against the entities). `V4` hasn't been exercised against a live DB
yet — worth doing before relying on it, and before adding whatever migration
eventually drops `shape`/`capacity`.

Local dev note: on this machine, port 5432 was being fought over by a
native Windows PostgreSQL service *and* a Docker Postgres container
belonging to a different project (`api-gateway`) — `event-service` has no
`docker-compose.yml` of its own, so it was colliding with whatever else
happened to be on 5432. Fixed for now by stopping the native service and
using the `api-gateway` container (its `user`/`secret` creds happened to
already match; had to create the `event_db` database inside it, since its
default DB is just `postgres`). Still worth giving `event-service` its own
isolated `docker-compose.yml` at some point rather than depending on
another project's container.

## Next steps, roughly in order

1. **A migration dropping `sections.shape`/`sections.capacity` is still
   needed** (would be `V5`, since `V4` is the tickets table) — the entity
   and DTOs no longer reference them, but the DB columns are still there
   and `shape` is `NOT NULL`, so seeding a venue will fail against any
   Postgres that's actually run `V1`-`V4`. Deliberately left undone for now
   (see "GENERAL_ADMISSION removed too" above) — do this before relying on
   `POST /admin/venues` or the new read endpoints against a real DB.
2. **Exercise `V4`, `POST /admin/venues`, and the new `layout/` read
   endpoints against a live DB** — `V1`-`V3` were verified live; `V4`
   (tickets table) and the whole read side (`GET /venues/{id}/layout`,
   `GET /sections/{id}/seats`, and the frontend against a real backend)
   have only been exercised via Mockito tests / `npm run dev` against a
   locally-seeded response so far, never a real Postgres end-to-end.
3. **"Levels"** — allow more than one section per direction (stacked
   outward from each other), relaxing the `Validator`'s current
   one-per-direction rule. Would also need `SectionGeometryCalculator` to
   grow a notion of stacking (it currently assumes one section per
   direction). Deliberately deferred until the plain-grid version above is
   confirmed working.
4. **Redis cache-aside read path** for `venue:{venueId}:layout` — this is
   the mandatory correctness baseline per the original design doc,
   independent of warming. Rendering/geometry (the thing that blocked this)
   is done now — `VenueLayoutService.getLayout`'s response is exactly the
   payload this would cache — but the actual Redis wiring doesn't exist
   yet. Must exist before warming is meaningful.
5. **Kafka warming**: `venue.layout.updated` topic, trigger-only payload
   (`{venueId, eventType}`), idempotent consumer that re-reads Postgres and
   overwrites the Redis key. Producer lives in `VenueSeedingService` (after
   the transaction commits); publish failures log a warning, never roll
   back the Postgres write.
6. **Tests for the performer/event seeding endpoints** — still no
   `@WebMvcTest`/`@DataJpaTest`/Mockito coverage for `PerformerSeedingService`
   or `EventSeedingService`, unlike `VenueSeedingServiceTest` and the fully
   unit-tested `language` package.
7. **Ticket service/endpoint logic** — the `Ticket`/`TicketStatus` entities,
   `TicketRepository`, and `tickets` table (`V4__Tickets.sql`) exist (merged
   from the `denis` branch), but nothing seeds/reads/updates them yet, and
   there's no capacity-check mechanism (`sold < capacity`-equivalent against
   seat/section availability, exact mechanism TBD) since `GENERAL_ADMISSION`
   capacity tracking is gone — Week 3 scope, still unresolved.
8. **Auth** — lock `/admin/**` to an `ADMIN` role once `user-service` JWT
   claims support it; unconfirmed whether that's ready yet. The new
   `layout/` read endpoints are intentionally public already (not under
   `/admin/**`), since a ticket-buying client will need them eventually —
   nothing to change there.
