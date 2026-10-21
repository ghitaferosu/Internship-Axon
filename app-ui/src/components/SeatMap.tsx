import { useEffect, useRef, useState } from 'react'
import type { CSSProperties, MouseEvent } from 'react'
import Panzoom, { type PanzoomObject } from '@panzoom/panzoom'
import { getSectionSeatPages } from '../api/venueLayout'
import { seatLabel, seatPosition, seatRadius, sectionBoundingBox, sectionCentroid, sectionPolygonPoints, type BoundingBox } from '../lib/geometry'
import type { Seat, SectionLayout, VenueLayout } from '../types/venueLayout'

const STAGE_RADIUS = 40
const MARGIN = 40

// How much of the viewport a zoomed-in section should fill (leaves a little
// breathing room around its edges instead of touching the frame exactly).
const ZOOM_FILL_RATIO = 0.85
const MIN_SCALE = 1 // can't zoom out past the initial full-layout view
const MAX_SCALE = 30
// Matches Panzoom's own `step` option below, so the +/- buttons zoom by the
// same per-click amount as one wheel notch.
const ZOOM_STEP = 0.3

function computeViewBox(sections: SectionLayout[]): BoundingBox {
    const boxes = sections.map(sectionBoundingBox)
    const minX = Math.min(-STAGE_RADIUS, ...boxes.map((b) => b.x))
    const minY = Math.min(-STAGE_RADIUS, ...boxes.map((b) => b.y))
    const maxX = Math.max(STAGE_RADIUS, ...boxes.map((b) => b.x + b.width))
    const maxY = Math.max(STAGE_RADIUS, ...boxes.map((b) => b.y + b.height))

    return {
        x: minX - MARGIN,
        y: minY - MARGIN,
        width: maxX - minX + MARGIN * 2,
        height: maxY - minY + MARGIN * 2,
    }
}

// Client-side only: which seats are picked, not whether they're actually
// available. No held/sold-seat awareness here - that's a separate concern.
function seatKey(sectionId: number, seat: Seat): string {
    return `${sectionId}:${seat.row}:${seat.seatNumber}`
}

type SectionSeatsState = Seat[] | 'loading' | { error: string }

interface SeatMapProps {
    layout: VenueLayout
}

export default function SeatMap({ layout }: SeatMapProps) {
    const [expandedSectionId, setExpandedSectionId] = useState<number | null>(null)
    const [seatsBySection, setSeatsBySection] = useState<Record<number, SectionSeatsState>>({})
    const [selectedSeats, setSelectedSeats] = useState<Set<string>>(() => new Set())
    const svgRef = useRef<SVGSVGElement | null>(null)
    const contentRef = useRef<SVGGElement | null>(null)
    const panzoomRef = useRef<PanzoomObject | null>(null)

    const viewBox = computeViewBox(layout.sections)

    // Panzoom drives wheel-zoom and drag-pan on the content group. Its
    // transform is `scale(s) translate(x px, y px)` with transform-origin 0 0
    // on SVG elements, and a CSS px on an SVG child is one user unit of the
    // coordinate system its viewBox establishes - so "px" here is really just
    // our own geometry units, which zoomToSection() below relies on.
    useEffect(() => {
        const contentEl = contentRef.current
        const svgEl = svgRef.current
        if (!contentEl || !svgEl) return undefined

        const panzoom = Panzoom(contentEl, {
            minScale: MIN_SCALE,
            maxScale: MAX_SCALE,
            step: ZOOM_STEP,
            animate: true,
            cursor: 'grab',
            // Bind pan/drag listening to the <svg> itself rather than just the
            // content group, so a drag can start anywhere in the box - including
            // any letterboxed margin outside the venue content - not only on top
            // of rendered shapes.
            canvas: true,
        })
        panzoomRef.current = panzoom
        svgEl.addEventListener('wheel', panzoom.zoomWithWheel, { passive: false })

        return () => {
            svgEl.removeEventListener('wheel', panzoom.zoomWithWheel)
            panzoom.destroy()
            panzoomRef.current = null
        }
    }, [layout])

    // Zoom+pan so `box` (same coordinate space as the SVG viewBox) fills the
    // viewport, centered. Passing `box: null` resets to the full layout view.
    function zoomToBox(box: BoundingBox | null) {
        const panzoom = panzoomRef.current
        if (!panzoom) return

        // The *unclamped* fill-ratio scale, before MIN_SCALE gets a say - used
        // below to detect the case where a section is already too large (close
        // to, or bigger than, the full layout) to actually zoom into.
        const naturalScale = box ? Math.min(viewBox.width / box.width, viewBox.height / box.height) * ZOOM_FILL_RATIO : null
        const targetScale = naturalScale !== null ? Math.min(MAX_SCALE, Math.max(MIN_SCALE, naturalScale)) : MIN_SCALE

        // If the natural scale is already at or below the full-layout floor,
        // clamping still lands on MIN_SCALE - i.e. this doesn't actually zoom
        // in at all. Centering on the section's own (generally off-center) box
        // in that case would just pan the still-fully-zoomed-out view away from
        // the venue's center for no reason, shoving the rest of the layout
        // (possibly the stage itself) out of frame. Fall back to the same
        // venue-centered framing as the box:null reset instead.
        const canActuallyZoomIn = naturalScale !== null && naturalScale > MIN_SCALE
        const centerX = box && canActuallyZoomIn ? box.x + box.width / 2 : viewBox.x + viewBox.width / 2
        const centerY = box && canActuallyZoomIn ? box.y + box.height / 2 : viewBox.y + viewBox.height / 2
        const viewCenterX = viewBox.x + viewBox.width / 2
        const viewCenterY = viewBox.y + viewBox.height / 2

        // Both calls resolve synchronously (Panzoom snapshots x/y/scale before
        // deferring the actual style write to rAF), so they coalesce into one
        // smooth transition instead of visibly stepping through zoom-then-pan.
        panzoom.zoom(targetScale, { animate: true, force: true })
        panzoom.pan(viewCenterX / targetScale - centerX, viewCenterY / targetScale - centerY, {
            animate: true,
            force: true,
        })
    }

    // Zoom in/out by one step, anchored on whatever content point is
    // currently centered in the viewport - not the venue's global center -
    // so repeated clicks zoom toward wherever the buyer is already looking
    // (including mid-section, after a manual pan) instead of jumping back to
    // the stage every time. Mirrors zoomToBox's own pan/zoom math above.
    function zoomBy(factor: number) {
        const panzoom = panzoomRef.current
        if (!panzoom) return
        const currentScale = panzoom.getScale()
        const targetScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, currentScale * factor))
        if (targetScale === currentScale) return

        const pan = panzoom.getPan()
        const viewCenterX = viewBox.x + viewBox.width / 2
        const viewCenterY = viewBox.y + viewBox.height / 2
        const pivotX = viewCenterX / currentScale - pan.x
        const pivotY = viewCenterY / currentScale - pan.y

        panzoom.zoom(targetScale, { animate: true, force: true })
        panzoom.pan(viewCenterX / targetScale - pivotX, viewCenterY / targetScale - pivotY, {
            animate: true,
            force: true,
        })
    }

    function resetView() {
        setExpandedSectionId(null)
        zoomToBox(null)
    }

    async function toggleSection(section: SectionLayout) {
        if (expandedSectionId === section.id) {
            setExpandedSectionId(null)
            zoomToBox(null)
            return
        }

        setExpandedSectionId(section.id)
        zoomToBox(sectionBoundingBox(section))
        if (seatsBySection[section.id]) return

        setSeatsBySection((prev) => ({ ...prev, [section.id]: 'loading' }))
        try {
            // Pages stream in one at a time; each one is appended so seats render
            // progressively instead of the whole section blocking on one big fetch.
            for await (const pageSeats of getSectionSeatPages(section.id)) {
                setSeatsBySection((prev) => {
                    const existing = Array.isArray(prev[section.id]) ? (prev[section.id] as Seat[]) : []
                    return { ...prev, [section.id]: [...existing, ...pageSeats] }
                })
            }
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Failed to load seats'
            setSeatsBySection((prev) => ({ ...prev, [section.id]: { error: message } }))
        }
    }

    function toggleSeat(section: SectionLayout, seat: Seat, event: MouseEvent<SVGCircleElement>) {
        // Stop the click from bubbling up to the section's own onClick, which
        // would otherwise collapse/zoom the section back out.
        event.stopPropagation()
        const key = seatKey(section.id, seat)
        setSelectedSeats((prev) => {
            const next = new Set(prev)
            if (next.has(key)) {
                next.delete(key)
            } else {
                next.add(key)
            }
            return next
        })
    }

    return (
        <div style={{ position: 'relative' }}>
            <svg
                ref={svgRef}
                viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`}
                style={{ width: '100%', height: '70vh', background: '#111', borderRadius: 8, display: 'block' }}
            >
                <style>{`
                    .seat-dot { transition: opacity 120ms ease, stroke-width 120ms ease; }
                    .seat-dot:hover { opacity: 0.8; stroke: #fff; stroke-width: 2px; }
                    .section-outline:hover polygon { fill-opacity: 0.85; }
                    .zoom-btn:hover { background: rgba(255,255,255,0.18); }
                    .zoom-btn:active { background: rgba(255,255,255,0.28); }
                `}</style>
                <g ref={contentRef}>
                    {/* Fills the whole viewBox so drag-to-pan works from empty space too,
                        not just on top of the stage/sections themselves. */}
                    <rect x={viewBox.x} y={viewBox.y} width={viewBox.width} height={viewBox.height} fill="#111" />

                    <circle cx={0} cy={0} r={STAGE_RADIUS} fill="#333" stroke="#666" />
                    <text x={0} y={0} fill="#aaa" fontSize={12} textAnchor="middle" dominantBaseline="middle">
                        STAGE
                    </text>

                    {layout.sections.map((section) => {
                        const isExpanded = section.id === expandedSectionId
                        const centroid = sectionCentroid(section)
                        const seats = seatsBySection[section.id]

                        return (
                            <g
                                key={section.id}
                                className="section-outline"
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

                                {isExpanded &&
                                    Array.isArray(seats) &&
                                    seats.map((seat) => {
                                        const pos = seatPosition(section, seat.row, seat.seatNumber)
                                        const isSelected = selectedSeats.has(seatKey(section.id, seat))
                                        return (
                                            <circle
                                                key={`${seat.row}-${seat.seatNumber}`}
                                                className="seat-dot"
                                                cx={pos.x}
                                                cy={pos.y}
                                                r={seatRadius(section)}
                                                fill={isSelected ? '#3ddc84' : '#7fb3d9'}
                                                stroke={isSelected ? '#1c8a4c' : 'none'}
                                                strokeWidth={isSelected ? 2 : 0}
                                                style={{ cursor: 'pointer' }}
                                                onClick={(event) => toggleSeat(section, seat, event)}
                                            >
                                                <title>{seatLabel(seat.row, seat.seatNumber)}</title>
                                            </circle>
                                        )
                                    })}

                                {isExpanded && seats === 'loading' && (
                                    <text x={centroid.x} y={centroid.y} fill="#aaa" fontSize={12} textAnchor="middle">
                                        Loading seats…
                                    </text>
                                )}

                                {isExpanded && seats !== undefined && seats !== 'loading' && !Array.isArray(seats) && (
                                    <text x={centroid.x} y={centroid.y} fill="#e07" fontSize={12} textAnchor="middle">
                                        Failed to load seats
                                    </text>
                                )}
                            </g>
                        )
                    })}
                </g>
            </svg>

            <ZoomControls
                onZoomIn={() => zoomBy(Math.exp(ZOOM_STEP))}
                onZoomOut={() => zoomBy(Math.exp(-ZOOM_STEP))}
                onReset={resetView}
            />

            <div
                style={{
                    position: 'absolute',
                    bottom: 8,
                    left: 8,
                    background: 'rgba(0,0,0,0.7)',
                    color: '#fff',
                    borderRadius: 6,
                    padding: '0.5rem 0.75rem',
                    fontSize: 12,
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.75rem',
                }}
            >
                <Legend color="#7fb3d9" label="Available" />
                <Legend color="#3ddc84" label="Selected" />
                {selectedSeats.size > 0 && (
                    <>
                        <span style={{ opacity: 0.5 }}>•</span>
                        <span>
                            {selectedSeats.size} seat{selectedSeats.size === 1 ? '' : 's'} selected
                        </span>
                        <button
                            type="button"
                            onClick={() => setSelectedSeats(new Set())}
                            style={{
                                background: 'rgba(255,255,255,0.12)',
                                color: '#fff',
                                border: 'none',
                                borderRadius: 4,
                                padding: '0.2rem 0.5rem',
                                cursor: 'pointer',
                            }}
                        >
                            Clear
                        </button>
                    </>
                )}
            </div>
        </div>
    )
}

function Legend({ color, label }: { color: string; label: string }) {
    return (
        <span style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <span style={{ width: 10, height: 10, borderRadius: '50%', background: color, display: 'inline-block' }} />
            {label}
        </span>
    )
}

// Shared top-right zoom cluster for all three seat-map/editor surfaces (each
// keeps its own copy since they don't otherwise share a components module) -
// buttons on top of the existing wheel-zoom/drag-pan, which keeps working
// unchanged alongside these.
function ZoomControls({ onZoomIn, onZoomOut, onReset }: { onZoomIn: () => void; onZoomOut: () => void; onReset: () => void }) {
    const buttonStyle: CSSProperties = {
        width: 28,
        height: 28,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'rgba(0,0,0,0.7)',
        color: '#fff',
        border: '1px solid rgba(255,255,255,0.25)',
        borderRadius: 6,
        fontSize: 15,
        lineHeight: 1,
        cursor: 'pointer',
    }
    return (
        <div style={{position: 'absolute', top: 8, right: 8, display: 'flex', flexDirection: 'column', gap: 4}}>
            <button type="button" className="zoom-btn" onClick={onZoomIn} style={buttonStyle} title="Mărește"
                    aria-label="Mărește">
                +
            </button>
            <button type="button" className="zoom-btn" onClick={onZoomOut} style={buttonStyle} title="Micșorează"
                    aria-label="Micșorează">
                −
            </button>
            <button type="button" className="zoom-btn" onClick={onReset} style={buttonStyle}
                    title="Resetează vizualizarea" aria-label="Resetează vizualizarea">
                ⟳
            </button>
        </div>
    )
}
