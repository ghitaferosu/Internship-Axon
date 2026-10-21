import { useEffect, useRef, useState } from 'react';
import Panzoom from '@panzoom/panzoom';
import { saveVenue } from './api.js';

const STAGE_RADIUS = 40;
const DEFAULT_VIEW_BOX = { x: -400, y: -400, width: 800, height: 800 };
const MIN_SCALE = 0.4;
const MAX_SCALE = 20;

// How close (in screen pixels, not venue units) a click on the draft polygon's
// first vertex has to be before it's treated as "close the shape" rather than
// "place another point here". Screen pixels because the natural affordance
// ("click near the dot you see") shouldn't get harder to trigger just because
// the admin zoomed out.
const CLOSE_SNAP_PX = 10;
const MIN_POLYGON_POINTS = 3;

// A mousedown+mouseup on a section counts as a "click" (select/toggle) below
// this many screen pixels of movement; past it, it's a drag (move the
// section). Screen pixels for the same reason as CLOSE_SNAP_PX above — it's
// about filtering out accidental jitter, not a venue-space distance.
const DRAG_CLICK_THRESHOLD_PX = 4;

const SECTION_STROKE = '#7fb3d9';
const SECTION_STROKE_SELECTED = '#ffd23d';

// Converts a mouse event's screen coordinates into the content group's local
// (venue-space) coordinate system. getScreenCTM() reflects the full transform
// chain — the SVG's own viewBox scaling *and* whatever Panzoom has applied to
// contentEl (pan/zoom) — so this stays correct regardless of current
// pan/zoom state, without this component needing to know Panzoom's internals.
function screenToVenuePoint(event, svgEl, contentEl) {
  const ctm = contentEl.getScreenCTM();
  if (!ctm) return { x: 0, y: 0 };
  const point = svgEl.createSVGPoint();
  point.x = event.clientX;
  point.y = event.clientY;
  const local = point.matrixTransform(ctm.inverse());
  return { x: local.x, y: local.y };
}

// Screen-pixels-per-venue-unit at the current pan/zoom state, derived from
// the CTM's scale components. Used to make the "click to close" snap
// tolerance a constant number of screen pixels rather than venue units.
function screenScaleOf(contentEl) {
  const ctm = contentEl.getScreenCTM();
  if (!ctm) return 1;
  return Math.hypot(ctm.a, ctm.b);
}

function distance(a, b) {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function centroidOf(points) {
  const sum = points.reduce((acc, p) => ({ x: acc.x + p.x, y: acc.y + p.y }), { x: 0, y: 0 });
  return { x: sum.x / points.length, y: sum.y / points.length };
}

function pointsToAttr(points) {
  return points.map((p) => `${p.x},${p.y}`).join(' ');
}

function translatePoints(points, dx, dy) {
  return points.map((p) => ({ x: p.x + dx, y: p.y + dy }));
}

// Flips points about their own centroid — "flip horizontal"/"flip vertical"
// in the same sense as Figma/Illustrator: mirrors the shape in place, it
// doesn't move it. To get a mirrored *copy* on the other side of the venue
// (e.g. the matching stand on the opposite side of the stage), copy/paste
// the section first, then mirror the pasted duplicate and drag it into place
// — deliberately kept as two composable steps rather than one "mirror across
// the stage" operation, since that would need to guess which axis/reference
// point the admin actually wants.
function mirrorPoints(points, axis) {
  const c = centroidOf(points);
  return axis === 'horizontal'
    ? points.map((p) => ({ x: 2 * c.x - p.x, y: p.y }))
    : points.map((p) => ({ x: p.x, y: 2 * c.y - p.y }));
}

// Paste offsets the duplicate by this much (venue units) so it's visibly
// distinct from the section it was copied from, instead of sitting exactly
// on top of it, before the admin drags it into place.
const PASTE_OFFSET = 24;

let nextSectionSeq = 1;

export default function VenueEditor() {
  const [venueName, setVenueName] = useState('');
  const [address, setAddress] = useState('');
  const [sections, setSections] = useState([]); // [{ id, name, points: [{x,y}] }]
  const [draft, setDraft] = useState(null); // { points: [{x,y}] } | null
  const [draftName, setDraftName] = useState('');
  const [cursor, setCursor] = useState(null); // {x,y} | null — for the rubber-band preview segment
  const [saveStatus, setSaveStatus] = useState(null);
  const [isSaving, setIsSaving] = useState(false);
  const [selectedSectionId, setSelectedSectionId] = useState(null);
  const [clipboard, setClipboard] = useState(null); // { name, points } | null

  const svgRef = useRef(null);
  const contentRef = useRef(null);
  const panzoomRef = useRef(null);
  // Mutable, not state: updated on every mousemove during a section drag, and
  // read (not rendered) by the window listeners below — putting this in state
  // would re-run the drag effect on every pixel of movement for no benefit.
  const dragRef = useRef(null); // { sectionId, startVenue, startScreenX, startScreenY, originalPoints, moved }
  // Mirrors `sections` for the copy shortcut's keydown effect below to read
  // without needing `sections` in that effect's deps — dragging updates
  // `sections` on every mousemove without touching selectedSectionId/draft/
  // clipboard, so without this a copy mid-drag (or right after one, before
  // any of those three change) would copy stale, pre-drag points.
  const sectionsRef = useRef(sections);
  useEffect(() => {
    sectionsRef.current = sections;
  }, [sections]);

  useEffect(() => {
    const contentEl = contentRef.current;
    const svgEl = svgRef.current;
    if (!contentEl || !svgEl) return undefined;

    const panzoom = Panzoom(contentEl, {
      minScale: MIN_SCALE,
      maxScale: MAX_SCALE,
      step: 0.3,
      cursor: 'crosshair',
      // canvas:true so pan-drag works from empty space, same as SeatMap. A
      // plain click (mousedown+mouseup with no drag distance) still reaches
      // our onClick below afterwards — Panzoom only intercepts actual drags.
      canvas: true
    });
    panzoomRef.current = panzoom;
    svgEl.addEventListener('wheel', panzoom.zoomWithWheel, { passive: false });

    return () => {
      svgEl.removeEventListener('wheel', panzoom.zoomWithWheel);
      panzoom.destroy();
      panzoomRef.current = null;
    };
  }, []);

  // Escape cancels the in-progress polygon, Backspace undoes its last point —
  // only while a draft is active, so these don't hijack normal typing in the
  // name/address inputs elsewhere on the page.
  useEffect(() => {
    if (!draft) return undefined;

    function onKeyDown(event) {
      if (event.key === 'Escape') {
        cancelDraft();
      } else if (event.key === 'Backspace' && document.activeElement?.tagName !== 'INPUT') {
        event.preventDefault();
        undoLastPoint();
      }
    }

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft]);

  // Delete/Backspace removes the selected section, Escape deselects,
  // Ctrl/Cmd+C copies it, Ctrl/Cmd+V pastes the clipboard as a new (offset)
  // section — only while nothing is being drafted (drafting has its own
  // Backspace/Escape meaning above) and not while typing in a text input.
  useEffect(() => {
    if (draft || (!selectedSectionId && !clipboard)) return undefined;

    function onKeyDown(event) {
      if (document.activeElement?.tagName === 'INPUT') return;
      const meta = event.ctrlKey || event.metaKey;

      if (meta && event.key.toLowerCase() === 'c' && selectedSectionId) {
        event.preventDefault();
        copySelectedSection();
      } else if (meta && event.key.toLowerCase() === 'v' && clipboard) {
        event.preventDefault();
        pasteClipboard();
      } else if (!meta && (event.key === 'Delete' || event.key === 'Backspace') && selectedSectionId) {
        event.preventDefault();
        removeSection(selectedSectionId);
      } else if (event.key === 'Escape' && selectedSectionId) {
        setSelectedSectionId(null);
      }
    }

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft, selectedSectionId, clipboard]);

  // Section drag-to-move. Bound once at the window level (not per-section
  // React handlers) since a drag can move the pointer outside the section's
  // own shape, or outside the SVG entirely, mid-gesture. handleSectionMouseDown
  // below is what actually populates dragRef to arm these.
  useEffect(() => {
    function onMouseMove(event) {
      const drag = dragRef.current;
      const svgEl = svgRef.current;
      const contentEl = contentRef.current;
      if (!drag || !svgEl || !contentEl) return;

      if (!drag.moved) {
        const screenDist = Math.hypot(event.clientX - drag.startScreenX, event.clientY - drag.startScreenY);
        if (screenDist < DRAG_CLICK_THRESHOLD_PX) return;
        drag.moved = true;
      }

      const current = screenToVenuePoint(event, svgEl, contentEl);
      const dx = current.x - drag.startVenue.x;
      const dy = current.y - drag.startVenue.y;
      setSections((prev) =>
        prev.map((s) => (s.id === drag.sectionId ? { ...s, points: translatePoints(drag.originalPoints, dx, dy) } : s))
      );
    }

    function onMouseUp() {
      const drag = dragRef.current;
      if (!drag) return;
      if (drag.moved) {
        // A drag just ended — leave the section selected so the move's result is visible.
        setSelectedSectionId(drag.sectionId);
      } else {
        // No movement past the threshold — this was a click, not a drag: toggle selection.
        setSelectedSectionId((prev) => (prev === drag.sectionId ? null : drag.sectionId));
      }
      dragRef.current = null;
    }

    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
    return () => {
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    };
  }, []);

  function handleSectionMouseDown(event, section) {
    if (draft) return; // drawing a new section takes priority; existing sections are inert while drafting
    event.stopPropagation();
    const svgEl = svgRef.current;
    const contentEl = contentRef.current;
    if (!svgEl || !contentEl) return;

    dragRef.current = {
      sectionId: section.id,
      startVenue: screenToVenuePoint(event, svgEl, contentEl),
      startScreenX: event.clientX,
      startScreenY: event.clientY,
      originalPoints: section.points,
      moved: false
    };
  }

  function startDraft() {
    setDraft({ points: [] });
    setDraftName(`Section ${nextSectionSeq}`);
    setCursor(null);
  }

  function cancelDraft() {
    setDraft(null);
    setDraftName('');
    setCursor(null);
  }

  function undoLastPoint() {
    setDraft((prev) => (prev ? { points: prev.points.slice(0, -1) } : prev));
  }

  function finishDraft() {
    if (!draft || draft.points.length < MIN_POLYGON_POINTS) return;
    const id = `draft-${nextSectionSeq++}`;
    setSections((prev) => [...prev, { id, name: draftName.trim() || `Section ${prev.length + 1}`, points: draft.points }]);
    cancelDraft();
  }

  function removeSection(id) {
    setSections((prev) => prev.filter((s) => s.id !== id));
    setSelectedSectionId((prev) => (prev === id ? null : prev));
  }

  function copySelectedSection() {
    const section = sectionsRef.current.find((s) => s.id === selectedSectionId);
    if (!section) return;
    setClipboard({ name: section.name, points: section.points });
  }

  function pasteClipboard() {
    if (!clipboard) return;
    const id = `section-${nextSectionSeq++}`;
    const points = translatePoints(clipboard.points, PASTE_OFFSET, PASTE_OFFSET);
    setSections((prev) => [...prev, { id, name: `${clipboard.name} copy`, points }]);
    setSelectedSectionId(id);
  }

  function mirrorSelectedSection(axis) {
    if (!selectedSectionId) return;
    setSections((prev) =>
      prev.map((s) => (s.id === selectedSectionId ? { ...s, points: mirrorPoints(s.points, axis) } : s))
    );
  }

  function handleCanvasClick(event) {
    if (!draft) {
      setSelectedSectionId(null);
      return;
    }
    if (!svgRef.current || !contentRef.current) return;
    const point = screenToVenuePoint(event, svgRef.current, contentRef.current);

    if (draft.points.length >= MIN_POLYGON_POINTS) {
      const scale = screenScaleOf(contentRef.current);
      const snapVenueDistance = CLOSE_SNAP_PX / scale;
      if (distance(point, draft.points[0]) <= snapVenueDistance) {
        finishDraft();
        return;
      }
    }

    setDraft((prev) => ({ points: [...prev.points, point] }));
  }

  function handleCanvasMouseMove(event) {
    if (!draft || !svgRef.current || !contentRef.current) return;
    setCursor(screenToVenuePoint(event, svgRef.current, contentRef.current));
  }

  async function handleSave() {
    const payload = {
      name: venueName,
      address,
      sections: sections.map(({ name, points }) => ({ name, points }))
    };
    setIsSaving(true);
    setSaveStatus('Saving…');
    try {
      const saved = await saveVenue(payload);
      setSaveStatus(`Saved "${saved.name}" (id ${saved.id}) with ${saved.sections.length} section(s) and ${saved.noSeats} seat(s).`);
    } catch (err) {
      setSaveStatus(`Failed to save: ${err.message}`);
    } finally {
      setIsSaving(false);
    }
  }

  const viewBox = DEFAULT_VIEW_BOX;
  const canFinishDraft = !!draft && draft.points.length >= MIN_POLYGON_POINTS;

  return (
    <div>
      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.5rem', flexWrap: 'wrap' }}>
        <label htmlFor="venueName">Venue name</label>
        <input id="venueName" type="text" value={venueName} onChange={(e) => setVenueName(e.target.value)} />
        <label htmlFor="venueAddress">Address</label>
        <input id="venueAddress" type="text" value={address} onChange={(e) => setAddress(e.target.value)} />
      </div>

      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.5rem', flexWrap: 'wrap' }}>
        {!draft && (
          <>
            <button onClick={startDraft}>New section</button>
            <button onClick={copySelectedSection} disabled={!selectedSectionId}>
              Copy
            </button>
            <button onClick={pasteClipboard} disabled={!clipboard}>
              Paste
            </button>
            <button onClick={() => mirrorSelectedSection('horizontal')} disabled={!selectedSectionId}>
              Mirror ↔
            </button>
            <button onClick={() => mirrorSelectedSection('vertical')} disabled={!selectedSectionId}>
              Mirror ↕
            </button>
            {sections.length > 0 && (
              <span style={{ fontSize: 12, color: '#888' }}>
                Click a section to select it, drag it to move it. Delete/Backspace removes it, Esc deselects, Ctrl/Cmd+C/V
                copies/pastes. Mirror flips the selected section in place — copy/paste first for a mirrored duplicate.
              </span>
            )}
          </>
        )}
        {draft && (
          <>
            <label htmlFor="draftName">Section name</label>
            <input id="draftName" type="text" value={draftName} onChange={(e) => setDraftName(e.target.value)} />
            <button onClick={undoLastPoint} disabled={draft.points.length === 0}>
              Undo point
            </button>
            <button onClick={finishDraft} disabled={!canFinishDraft}>
              Finish section
            </button>
            <button onClick={cancelDraft}>Cancel</button>
            <span style={{ fontSize: 12, color: '#888' }}>
              Click to place points ({draft.points.length} so far, need {MIN_POLYGON_POINTS}+) — click the first point again, or
              "Finish section", to close the shape. Esc cancels, Backspace undoes.
            </span>
          </>
        )}
      </div>

      <svg
        ref={svgRef}
        viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`}
        style={{ width: '100%', height: '70vh', background: '#111', borderRadius: 8, display: 'block' }}
        onClick={handleCanvasClick}
        onMouseMove={handleCanvasMouseMove}
      >
        <g ref={contentRef}>
          <rect x={viewBox.x} y={viewBox.y} width={viewBox.width} height={viewBox.height} fill="#111" />

          <circle cx={0} cy={0} r={STAGE_RADIUS} fill="#333" stroke="#666" />
          <text x={0} y={0} fill="#aaa" fontSize={12} textAnchor="middle" dominantBaseline="middle">
            STAGE
          </text>

          {sections.map((section) => {
            const centroid = centroidOf(section.points);
            const isSelected = section.id === selectedSectionId;
            return (
              // panzoom-exclude keeps Panzoom's own canvas-drag from starting
              // when the mousedown lands on a section — see handleSectionMouseDown,
              // which owns drag-to-move + click-to-select for these instead.
              <g
                key={section.id}
                className="panzoom-exclude"
                onMouseDown={(event) => handleSectionMouseDown(event, section)}
                // Stop the native click (fired after our mouseup handling below)
                // from bubbling to the canvas's onClick and immediately undoing
                // the selection it just set.
                onClick={(event) => event.stopPropagation()}
                style={{ cursor: draft ? 'default' : 'grab' }}
              >
                <polygon
                  points={pointsToAttr(section.points)}
                  fill="#2b5b8455"
                  stroke={isSelected ? SECTION_STROKE_SELECTED : SECTION_STROKE}
                  strokeWidth={isSelected ? 3 : 2}
                />
                <text x={centroid.x} y={centroid.y} fill="#fff" fontSize={14} textAnchor="middle" dominantBaseline="middle">
                  {section.name}
                </text>
                <text
                  x={centroid.x}
                  y={centroid.y + 18}
                  fill="#e07"
                  fontSize={12}
                  textAnchor="middle"
                  style={{ cursor: 'pointer' }}
                  onMouseDown={(event) => event.stopPropagation()}
                  onClick={(event) => {
                    event.stopPropagation();
                    removeSection(section.id);
                  }}
                >
                  remove
                </text>
              </g>
            );
          })}

          {draft && draft.points.length > 0 && (
            <>
              <polyline
                points={pointsToAttr(cursor ? [...draft.points, cursor] : draft.points)}
                fill="none"
                stroke="#3ddc84"
                strokeWidth={2}
                strokeDasharray="4 3"
              />
              {draft.points.map((p, i) => (
                <circle
                  key={i}
                  cx={p.x}
                  cy={p.y}
                  r={i === 0 && draft.points.length >= MIN_POLYGON_POINTS ? 6 : 4}
                  fill={i === 0 ? '#3ddc84' : '#fff'}
                  stroke="#1c8a4c"
                />
              ))}
            </>
          )}
        </g>
      </svg>

      <div style={{ marginTop: '0.5rem', display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
        <button onClick={handleSave} disabled={sections.length === 0 || isSaving}>
          Save venue
        </button>
        {saveStatus && <span style={{ fontSize: 12, color: '#888' }}>{saveStatus}</span>}
      </div>
    </div>
  );
}
