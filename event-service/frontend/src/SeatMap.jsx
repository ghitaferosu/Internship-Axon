import { useEffect, useRef, useState } from 'react';
import Panzoom from '@panzoom/panzoom';
import { getSectionSeatPages } from './api.js';
import { seatPosition, sectionBoundingBox, sectionCentroid, sectionPolygonPoints } from './geometry.js';

const STAGE_RADIUS = 40;
const MARGIN = 40;

// How much of the viewport a zoomed-in section should fill (leaves a little
// breathing room around its edges instead of touching the frame exactly).
const ZOOM_FILL_RATIO = 0.85;
const MIN_SCALE = 1; // can't zoom out past the initial full-layout view
const MAX_SCALE = 30;

function computeViewBox(sections) {
  const boxes = sections.map(sectionBoundingBox);
  const minX = Math.min(-STAGE_RADIUS, ...boxes.map((b) => b.x));
  const minY = Math.min(-STAGE_RADIUS, ...boxes.map((b) => b.y));
  const maxX = Math.max(STAGE_RADIUS, ...boxes.map((b) => b.x + b.width));
  const maxY = Math.max(STAGE_RADIUS, ...boxes.map((b) => b.y + b.height));

  return {
    x: minX - MARGIN,
    y: minY - MARGIN,
    width: maxX - minX + MARGIN * 2,
    height: maxY - minY + MARGIN * 2
  };
}

// Client-side only: which seats are picked, not whether they're actually
// available. No held/sold-seat awareness here — that's a separate concern.
function seatKey(sectionId, seat) {
  return `${sectionId}:${seat.row}:${seat.seatNumber}`;
}

export default function SeatMap({ layout }) {
  const [expandedSectionId, setExpandedSectionId] = useState(null);
  const [seatsBySection, setSeatsBySection] = useState({});
  const [selectedSeats, setSelectedSeats] = useState(() => new Set());
  const svgRef = useRef(null);
  const contentRef = useRef(null);
  const panzoomRef = useRef(null);

  const viewBox = computeViewBox(layout.sections);

  // Panzoom drives wheel-zoom and drag-pan on the content group. Its
  // transform is `scale(s) translate(x px, y px)` with transform-origin 0 0
  // on SVG elements, and a CSS px on an SVG child is one user unit of the
  // coordinate system its viewBox establishes — so "px" here is really just
  // our own geometry units, which zoomToSection() below relies on.
  useEffect(() => {
    const contentEl = contentRef.current;
    const svgEl = svgRef.current;
    if (!contentEl || !svgEl) return undefined;

    const panzoom = Panzoom(contentEl, {
      minScale: MIN_SCALE,
      maxScale: MAX_SCALE,
      step: 0.3,
      animate: true,
      cursor: 'grab',
      // Bind pan/drag listening to the <svg> itself rather than just the
      // content group, so a drag can start anywhere in the box — including
      // any letterboxed margin outside the venue content — not only on top
      // of rendered shapes.
      canvas: true
    });
    panzoomRef.current = panzoom;
    svgEl.addEventListener('wheel', panzoom.zoomWithWheel, { passive: false });

    return () => {
      svgEl.removeEventListener('wheel', panzoom.zoomWithWheel);
      panzoom.destroy();
      panzoomRef.current = null;
    };
  }, [layout]);

  // Zoom+pan so `box` (same coordinate space as the SVG viewBox) fills the
  // viewport, centered. Passing `box: null` resets to the full layout view.
  function zoomToBox(box) {
    const panzoom = panzoomRef.current;
    if (!panzoom) return;

    const targetScale = box
      ? Math.min(
          MAX_SCALE,
          Math.max(MIN_SCALE, Math.min(viewBox.width / box.width, viewBox.height / box.height) * ZOOM_FILL_RATIO)
        )
      : MIN_SCALE;

    const centerX = box ? box.x + box.width / 2 : viewBox.x + viewBox.width / 2;
    const centerY = box ? box.y + box.height / 2 : viewBox.y + viewBox.height / 2;
    const viewCenterX = viewBox.x + viewBox.width / 2;
    const viewCenterY = viewBox.y + viewBox.height / 2;

    // Both calls resolve synchronously (Panzoom snapshots x/y/scale before
    // deferring the actual style write to rAF), so they coalesce into one
    // smooth transition instead of visibly stepping through zoom-then-pan.
    panzoom.zoom(targetScale, { animate: true, force: true });
    panzoom.pan(viewCenterX / targetScale - centerX, viewCenterY / targetScale - centerY, {
      animate: true,
      force: true
    });
  }

  async function toggleSection(section) {
    if (expandedSectionId === section.id) {
      setExpandedSectionId(null);
      zoomToBox(null);
      return;
    }

    setExpandedSectionId(section.id);
    zoomToBox(sectionBoundingBox(section));
    if (seatsBySection[section.id]) return;

    setSeatsBySection((prev) => ({ ...prev, [section.id]: 'loading' }));
    try {
      // Pages stream in one at a time; each one is appended so seats render
      // progressively instead of the whole section blocking on one big fetch.
      for await (const pageSeats of getSectionSeatPages(section.id)) {
        setSeatsBySection((prev) => {
          const existing = Array.isArray(prev[section.id]) ? prev[section.id] : [];
          return { ...prev, [section.id]: [...existing, ...pageSeats] };
        });
      }
    } catch (err) {
      setSeatsBySection((prev) => ({ ...prev, [section.id]: { error: err.message } }));
    }
  }

  function toggleSeat(section, seat, event) {
    // Stop the click from bubbling up to the section's own onClick, which
    // would otherwise collapse/zoom the section back out.
    event.stopPropagation();
    const key = seatKey(section.id, seat);
    setSelectedSeats((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  return (
    <div style={{ position: 'relative' }}>
      <svg
        ref={svgRef}
        viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`}
        style={{ width: '100%', height: '70vh', background: '#111', borderRadius: 8, display: 'block' }}
      >
        <g ref={contentRef}>
          {/* Fills the whole viewBox so drag-to-pan works from empty space too,
              not just on top of the stage/sections themselves. */}
          <rect x={viewBox.x} y={viewBox.y} width={viewBox.width} height={viewBox.height} fill="#111" />

          <circle cx={0} cy={0} r={STAGE_RADIUS} fill="#333" stroke="#666" />
          <text x={0} y={0} fill="#aaa" fontSize={12} textAnchor="middle" dominantBaseline="middle">
            STAGE
          </text>

          {layout.sections.map((section) => {
            const isExpanded = section.id === expandedSectionId;
            const centroid = sectionCentroid(section);
            const seats = seatsBySection[section.id];

            return (
              <g
                key={section.id}
                onClick={() => toggleSection(section)}
                style={{ cursor: 'pointer' }}
                opacity={expandedSectionId && !isExpanded ? 0.35 : 1}
              >
                {/* The drawn outline itself, not a computed box - buyers see
                    the actual section shape. The seat-placement box (angle/
                    origin/rowSpacing) stays purely an internal aid, never
                    rendered as the boundary. */}
                <polygon
                  points={sectionPolygonPoints(section)}
                  fill={isExpanded ? 'none' : '#2b5b84'}
                  stroke="#7fb3d9"
                  strokeDasharray={isExpanded ? '4 2' : undefined}
                />
                {!isExpanded && (
                  <text
                    x={centroid.x}
                    y={centroid.y}
                    fill="#fff"
                    fontSize={14}
                    textAnchor="middle"
                    dominantBaseline="middle"
                  >
                    {section.name}
                  </text>
                )}

                {isExpanded && Array.isArray(seats) &&
                  seats.map((seat) => {
                    const pos = seatPosition(section, seat.row, seat.seatNumber);
                    const isSelected = selectedSeats.has(seatKey(section.id, seat));
                    return (
                      <circle
                        key={`${seat.row}-${seat.seatNumber}`}
                        cx={pos.x}
                        cy={pos.y}
                        r={section.rowSpacing / 2 - 2}
                        fill={isSelected ? '#3ddc84' : '#7fb3d9'}
                        stroke={isSelected ? '#1c8a4c' : 'none'}
                        strokeWidth={isSelected ? 2 : 0}
                        style={{ cursor: 'pointer' }}
                        onClick={(event) => toggleSeat(section, seat, event)}
                      />
                    );
                  })}

                {isExpanded && seats === 'loading' && (
                  <text x={centroid.x} y={centroid.y} fill="#aaa" fontSize={12} textAnchor="middle">
                    Loading seats…
                  </text>
                )}

                {isExpanded && seats && seats.error && (
                  <text x={centroid.x} y={centroid.y} fill="#e07" fontSize={12} textAnchor="middle">
                    Failed to load seats
                  </text>
                )}
              </g>
            );
          })}
        </g>
      </svg>

      {expandedSectionId && (
        <button
          onClick={() => {
            setExpandedSectionId(null);
            zoomToBox(null);
          }}
          style={{ position: 'absolute', top: 8, right: 8 }}
        >
          Reset view
        </button>
      )}

      {selectedSeats.size > 0 && (
        <div
          style={{
            position: 'absolute',
            bottom: 8,
            left: 8,
            background: 'rgba(0,0,0,0.7)',
            color: '#fff',
            borderRadius: 6,
            padding: '0.5rem 0.75rem',
            fontSize: 13,
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem'
          }}
        >
          <span>
            {selectedSeats.size} seat{selectedSeats.size === 1 ? '' : 's'} selected
          </span>
          <button onClick={() => setSelectedSeats(new Set())}>Clear</button>
        </div>
      )}
    </div>
  );
}
