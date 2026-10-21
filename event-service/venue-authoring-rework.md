# Venue Authoring Rework

## Why

The current venue-seeding pipeline takes a hand-typed text DSL (`Lexer`/
`Parser`/`Validator` in `ro.axonsoft.services.event.language`), posted as
`VenueScriptRequestDTO{address, script}` to `POST /admin/venues`. Its
`SectionGeometryCalculator`/`geometry.js` only support axis-aligned
rectangular sections glued to one of 4 compass directions (`NORTH`/`SOUTH`/
`EAST`/`WEST` from a center point).

Goal: replace hand-typed scripts with a frontend tool where an admin draws
sections visually, enabling arbitrary/curved section shapes (real stadium
bowl layouts) instead of just 4 fixed rectangle orientations.

## Branch strategy

Work happens on a **new branch off `matei`**, not on `matei` directly — this
is a bigger, more speculative rework. `venue-authoring` was reset to
`matei`'s tip and is the active branch.

## What stays, what goes

- `SeatGenerator` (row×seat → `GeneratedSeat` expansion) stays as-is — seats
  are still always generated, never hand-inserted. Only the *placement
  formula* changes, from the fixed direction enum to drawn shape parameters.
- Dropped outright: `Lexer`, `Parser`, `Validator`, `TokenType` (+`Token`),
  the three `VenueScript*Exception` classes, `VenueScriptRequestDTO`, and
  their tests. No DSL-fixture path is kept — this was a deliberate decision,
  not an oversight.
- `VenueAst`/`SeatedSectionAst` survive as plain data shapes, now built from
  the frontend's JSON instead of `Parser.parse()`.
- Endpoint URL stays the same (`/admin/venues`) — only its contract changes,
  from DSL script text to structured polygon JSON.

## Drawing & seat-placement design

Per section, the admin draws **exactly one shape**: the outline polygon
(arbitrary/curved, clicked point-by-point). Nothing else is drawn or
resized manually — everything else is *computed* from that polygon:

1. **Facing angle** = angle from the stage/scene center point to the
   polygon's centroid. This is the continuous generalization of the old
   `Direction` enum (`NORTH`/`SOUTH`/`EAST`/`WEST` were just 4 fixed cases
   of the same idea) — replaces `SectionGeometryCalculator`'s per-direction
   switch statement.
2. Rotate the polygon's points into a frame where "toward the stage" is a
   fixed axis, then fit a rectangle to the rotated points. **The rectangle
   must fit *inside* the polygon** — a plain rotated bounding box can extend
   past a concave outline. Chosen method: shrink the rotated bounding box
   inward until all four corners land inside the polygon. This is a
   heuristic, not an optimal largest-inscribed-rectangle solve (which has
   no closed form) — accepted trade-off.
3. Fill that rectangle with a uniform fixed-spacing grid: `rowCount =
   floor(height/rowSpacing)`, `seatsPerRow = floor(width/rowSpacing)`. A
   single `rowSpacing` constant now covers both axes — `colSpacing` is
   dropped. Seat *count* is derived from drawn shape size, not hand-typed
   like the DSL's `ROWS 1-20 SEATS 1-15`.
   - Rejected alternative: per-row scanline against the polygon's actual
     edges, giving each row a different seat count (a true wedge/rake
     shape). More realistic, but needs new polygon-intersection geometry
     with nothing to build on — deferred as a possible later swap-in; it
     only changes how row/seat ranges are computed, not the drawing UI, the
     DTO, or the persistence flow.
   - Known caveat: for concave outlines the fitted rectangle can still sit
     slightly off from the drawn curve — acceptable for simple
     wedge/trapezoid shapes.
4. **Angle reference point:** fixed convention — canvas origin `(0,0)` is
   always the stage. No stage coordinate needs to be stored on `Venue` or
   sent in any payload.

Buyers must see the actual drawn section outline, not just the computed
seat-placement box — "admin-only" means only admins can *edit* section
outlines, not that buyers can't see the result. That's the whole point of
moving off the DSL's rectangles.

## Write path vs. read path

- **Write path (`seedVenue()`):** wire-level input from the frontend stays
  minimal/raw — `{name, address, sections:[{name, points:[{x,y}]}]}`, no
  computed fields. A server-side geometry calculator (the existing
  `SectionGeometryCalculator`, reworked — not a new class) consumes each
  section's raw points and computes `angle`, `originX`, `originY`,
  `rowSpacing`, `rowStart/rowEnd`, `seatStart/seatEnd`. This runs
  **once, at write time**, and its output is persisted straight into
  `Section`'s existing `originX`/`originY`/`rowSpacing` columns (+ a new
  `angle` column replacing `direction`).
- **Read path (`GET /venues/{id}/layout`, `@Cacheable`):** today this
  recomputes origin/spacing from the `Direction` enum on *every cached
  read* via `SectionGeometryCalculator.compute(Direction)` inside
  `VenueLayoutService.toSectionLayoutDTO()` — meaning `Section`'s geometry
  columns are currently dead (never populated, never read by this path).
  Recomputing on every read stops making sense once geometry comes from
  arbitrary polygon vertices (would mean joining the vertices table on
  every fetch to redo work already done once). `toSectionLayoutDTO()` will
  stop calling the calculator and just read the persisted columns off the
  entity instead.
- `SectionLayoutDTO` (inside `VenueLayoutResponseDTO`) — not
  `SectionSeedingResponseDTO` — is the real vertices-delivery point: it's
  what every viewer (buyer or admin) fetches to render the seat map. Needs
  a `vertices` field (from the new vertices table) and `Direction
  direction` → `angle`; `colSpacing` is dropped to match the
  rowSpacing-only decision above.
- `SectionSeedingRequestDTO`/`ResponseDTO` (write path) get the computed
  bundle (angle/origin/rowSpacing/ranges) plus the original vertices.
  Response mirrors the same shape back with generated `id`s — the frontend
  gets back exactly what was persisted, vertices included, after save.

## Persistence

- **One-shot save:** the whole venue (name/address + every section's
  polygon) is held in frontend state while drawing; a single "Save venue"
  POST creates `Venue` → `Section` → `Seat` in one transaction, mirroring
  `VenueSeedingService.seedVenue()`'s current shape.
  - Rejected: incremental per-section inserts while drawing — would need a
    new edit/move/resize path, draft-vs-published venue state, and
    per-section cache eviction, none of which exist today, purely to make
    page-refresh mid-draw resilient. Not worth the surface area for an
    admin tool.
- **Vertices storage: a wholly separate table/entity**, not a field/column
  on `Section` itself — own repository, FK'd by `section_id`, no JPA
  relationship declared on `Section`. Reason: a rendering-only concern
  shouldn't live in the core persistence model, and this keeps zero
  disturbance to the already-built seat/ticket code paths that load
  `Section` today.
- `Section` table changes are otherwise small: retype `direction` (enum) →
  `angle` (double). `originX`/`originY`/`rowSpacing`/`rowStart..seatEnd`
  columns already exist on `Section` (previously unused placeholders) —
  they just need to actually get populated now.
- `Seat` has no x/y columns and this stays true — seat pixel positions are
  computed on the frontend (`geometry.js`) from `Section`'s stored
  angle/origin/spacing, never persisted per-seat.
- Not yet decided: whether the vertices table needs to support reopening a
  venue for later editing, or only first-time creation. Deferred, not in
  scope yet.
- Wire/persistence format for the polygon: **JSON** (plain `{x, y}` point
  arrays inside a Java `record` DTO, matching every other endpoint in this
  codebase) — not literal SVG/XML. SVG stays a frontend rendering choice
  only.

## Frontend (`VenueEditor.jsx`)

- Click-to-place-points polygon drawing: rubber-band preview line to
  cursor, click-near-first-vertex or "Finish section" to close, Esc cancels
  the draft, Backspace undoes the last point.
- Own Panzoom instance on its own `<svg>` (separate from `SeatMap.jsx`'s).
  Screen→venue-space coordinates go through
  `contentEl.getScreenCTM().inverse()` — this reflects the full transform
  chain (SVG viewBox scaling + Panzoom's transform) regardless of pan/zoom
  state, so no Panzoom-internals knowledge is needed. Close-snap tolerance
  is a constant *screen*-pixel radius, not a venue-unit one, so it doesn't
  get harder to trigger when zoomed out.
- Multiple sections supported (name + points each, removable individually).
- Select + drag-to-move a drawn section (yellow highlight when selected);
  Delete/Backspace removes the selected section; Esc deselects. Each
  section opts out of Panzoom's own canvas-drag via the `panzoom-exclude`
  class. Click-vs-drag is disambiguated by a screen-pixel movement
  threshold between mousedown/mouseup, not the native `click` event.
- Copy/paste (Ctrl/Cmd+C/V, app-internal clipboard, not the OS one) and
  mirror (horizontal/vertical, flips the *selected* section about its own
  centroid in place). Copy/paste and mirror are independent, composable
  primitives — not one combined "mirror to the opposite side of the stage"
  operation. To get a mirrored duplicate elsewhere, the admin copies,
  pastes (offset so it's visible), mirrors the duplicate, then drags it
  into place.
- No router added — a `view`/`author` mode toggle in `App.jsx` instead, to
  match the app's existing no-router style.
- Not yet done: live seat-count/grid preview while drawing (was blocked on
  the frontend-vs-backend authority question, now resolved in the
  backend's favor — see above); polygon-editing of a single vertex (only
  append-during-draft and whole-section-remove exist so far).

## Task breakdown

1. **Drop DSL code** — done. `Lexer`/`Parser`/`Validator`/`TokenType`/
   exception classes/`VenueScriptRequestDTO` and their tests deleted.
   `VenueSeedingService`/`DataFeedingVenuesController` stubbed to keep the
   module compiling (route removed, class kept so it slots back in later).
   `GlobalExceptionHandler`'s handler list trimmed. `SeatGeneratorTest`
   rewritten to build `VenueAst`/`SeatedSectionAst` directly instead of via
   DSL text. Not yet touched: `feeding-ui.html` (still posts DSL script
   text — now a dead form) and `README_CLAUDE.md`'s venue-seeding section
   (documents the old contract).
2. **Frontend polygon-drawing UI** — done (`VenueEditor.jsx`, as described
   above). "Save venue" currently just logs the payload — no backend call
   yet.
3. **New request/response DTOs + backend geometry** — designed, not yet
   implemented. Order of implementation: `SectionGeometryCalculator`
   rework first (the algorithmic crux, testable in isolation), then the
   write-path DTOs, then the new vertices table/entity, then
   `VenueSeedingService.seedVenue()`, then the read path
   (`SectionLayoutDTO`/`VenueLayoutService`), then
   `geometry.js`/`SeatMap.jsx` on the frontend, then wiring
   `VenueEditor.jsx`'s "Save venue" button last.
4. Touch list for step 3: `SectionGeometryCalculator` (rework `compute()`
   signature + move the call site from read to write path), `Section`
   entity (`direction` → `angle`), new vertices table/entity + repository,
   `SectionSeedingRequestDTO`/`ResponseDTO` (write path),
   `SectionLayoutDTO` + `VenueLayoutService.toSectionLayoutDTO()` (read
   path — stop recomputing, add vertices), `geometry.js`/`SeatMap.jsx`
   (frontend — angle-based `seatPosition()`, polygon-based boundary
   rendering instead of a computed box).

## Open questions

- Whether the vertices table needs to support reopening a venue for later
  editing, or only first-time creation.
