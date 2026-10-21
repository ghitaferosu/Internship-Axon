import { useEffect, useMemo, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import Panzoom, { type PanzoomObject } from '@panzoom/panzoom'
import { getEventSectionSeatPages } from '../api/events'
import { seatLabel, seatPosition, seatRadius, sectionBoundingBox, sectionCentroid, sectionPolygonPoints, type BoundingBox } from '../lib/geometry'
import type { EventWithSeatMap, SeatWithStatus, TicketStatus } from '../types/booking'
import type { SectionLayout } from '../types/venueLayout'

const STAGE_RADIUS = 40
const MARGIN = 40
const ZOOM_FILL_RATIO = 0.85
const MIN_SCALE = 1
const MAX_SCALE = 30
// Matches Panzoom's own `step` option below, so the +/- buttons zoom by the
// same per-click amount as one wheel notch.
const ZOOM_STEP = 0.3

// What a rendered seat actually looks like right now - distinct from
// TicketStatus because "mine" overrides whatever status a stale fetch or an
// out-of-order websocket message might otherwise show for a seat the buyer
// is actively holding/paying for in this tab.
type SeatVisualState = 'available' | 'held-by-other' | 'sold' | 'mine-held' | 'mine-paying' | 'mine-paid'

const SEAT_COLOR: Record<SeatVisualState, { fill: string; stroke?: string }> = {
    available: { fill: '#7fb3d9' },
    'held-by-other': { fill: '#e0a030' },
    sold: { fill: '#4a4a4a' },
    'mine-held': { fill: '#3ddc84', stroke: '#1c8a4c' },
    'mine-paying': { fill: '#3ddc84', stroke: '#f5c518' },
    'mine-paid': { fill: '#1c8a4c', stroke: '#0d5c30' },
}

const SEAT_STATE_LABEL: Record<SeatVisualState, string> = {
    available: 'liber',
    'held-by-other': 'rezervat',
    sold: 'vândut',
    'mine-held': 'rezervat de tine',
    'mine-paying': 'se procesează plata',
    'mine-paid': 'bilet confirmat',
}

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

type SectionSeatsState = SeatWithStatus[] | 'loading' | { error: string }

// Cart membership drives most of a seat's visual state; mine-paying/mine-paid
// need one more bit than a plain Set, hence a status map instead of a Set.
export type MineStatus = 'held' | 'paying' | 'paid'

export interface LiveSeatStatus {
    status: TicketStatus
    heldUntil?: string | null
}

interface BookingSeatMapProps {
    event: EventWithSeatMap
    liveStatus: Map<number, LiveSeatStatus>
    mineStatus: Map<number, MineStatus>
    onSeatClick: (seat: SeatWithStatus, section: SectionLayout) => void
    selectable?: boolean
}

function expiresAt(heldUntil: string | null | undefined): number | null {
    if (!heldUntil) return null
    const at = new Date(heldUntil).getTime()
    return Number.isNaN(at) ? null : at
}

function visualState(
    seat: SeatWithStatus,
    live: LiveSeatStatus | undefined,
    mine: MineStatus | undefined,
    nowMs: number,
): SeatVisualState {
    if (mine === 'paid') return 'mine-paid'
    if (mine === 'paying') return 'mine-paying'
    if (mine === 'held') return 'mine-held'
    const status = live?.status ?? seat.status ?? 'AVAILABLE'
    if (status === 'SOLD') return 'sold'
    if (status === 'HELD') {
        const heldUntil = expiresAt(live ? live.heldUntil : seat.heldUntil)
        return heldUntil !== null && heldUntil <= nowMs ? 'available' : 'held-by-other'
    }
    return 'available'
}

export default function BookingSeatMap({ event, liveStatus, mineStatus, onSeatClick, selectable = true }: BookingSeatMapProps) {
    const layout = event.venue
    const [expandedSectionId, setExpandedSectionId] = useState<number | null>(null)
    const [seatsBySection, setSeatsBySection] = useState<Record<number, SectionSeatsState>>({})
    const [nowMs, setNowMs] = useState(() => Date.now())
    const svgRef = useRef<SVGSVGElement | null>(null)
    const contentRef = useRef<SVGGElement | null>(null)
    const panzoomRef = useRef<PanzoomObject | null>(null)

    const viewBox = computeViewBox(layout.sections)

    const nextExpiryAt = useMemo(() => {
        const pending: number[] = []
        const consider = (heldUntil: string | null | undefined) => {
            const at = expiresAt(heldUntil)
            if (at !== null && at > nowMs) pending.push(at)
        }

        liveStatus.forEach((live) => {
            if (live.status === 'HELD') consider(live.heldUntil)
        })
        Object.values(seatsBySection).forEach((seats) => {
            if (!Array.isArray(seats)) return
            seats.forEach((seat) => {
                if (seat.status === 'HELD' && !liveStatus.has(seat.id)) consider(seat.heldUntil)
            })
        })

        return pending.length > 0 ? Math.min(...pending) : null
    }, [liveStatus, seatsBySection, nowMs])

    useEffect(() => {
        if (nextExpiryAt === null) return undefined
        const timer = setTimeout(() => setNowMs(Date.now()), Math.max(0, nextExpiryAt - Date.now()) + 50)
        return () => clearTimeout(timer)
    }, [nextExpiryAt])

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
            for await (const pageSeats of getEventSectionSeatPages(section.id, event.id)) {
                setSeatsBySection((prev) => {
                    const existing = Array.isArray(prev[section.id]) ? (prev[section.id] as SeatWithStatus[]) : []
                    return { ...prev, [section.id]: [...existing, ...pageSeats] }
                })
            }
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Failed to load seats'
            setSeatsBySection((prev) => ({ ...prev, [section.id]: { error: message } }))
        }
    }

    return (
        <div style={{ position: 'relative' }}>
            <svg
                ref={svgRef}
                viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`}
                style={{ width: '100%', height: '65vh', background: '#111', borderRadius: 8, display: 'block' }}
            >
                <style>{`
                    .seat-dot { transition: opacity 120ms ease, stroke-width 120ms ease; }
                    .seat-dot:hover { opacity: 0.8; stroke: #fff; stroke-width: 2px; }
                    .section-outline:hover polygon { fill-opacity: 0.85; }
                    .zoom-btn:hover { background: rgba(255,255,255,0.18); }
                    .zoom-btn:active { background: rgba(255,255,255,0.28); }
                `}</style>
                <g ref={contentRef}>
                    <rect x={viewBox.x} y={viewBox.y} width={viewBox.width} height={viewBox.height} fill="#111" />

                    <circle cx={0} cy={0} r={STAGE_RADIUS} fill="#333" stroke="#666" />
                    <text x={0} y={0} fill="#aaa" fontSize={12} textAnchor="middle" dominantBaseline="middle">
                        SCENĂ
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
                                        const state = visualState(seat, liveStatus.get(seat.id), mineStatus.get(seat.id), nowMs)
                                        const color = SEAT_COLOR[state]
                                        const clickable = selectable && (state === 'available' || state === 'mine-held')
                                        return (
                                            <circle
                                                key={seat.id}
                                                className={clickable ? 'seat-dot' : undefined}
                                                cx={pos.x}
                                                cy={pos.y}
                                                r={seatRadius(section)}
                                                fill={color.fill}
                                                stroke={color.stroke ?? 'none'}
                                                strokeWidth={color.stroke ? 2 : 0}
                                                opacity={selectable ? 1 : 0.45}
                                                style={{cursor: clickable ? 'pointer' : 'not-allowed'}}
                                                onClick={(domEvent) => {
                                                    domEvent.stopPropagation()
                                                    if (clickable) onSeatClick(seat, section)
                                                }}
                                            >
                                                <title>
                                                    {seatLabel(seat.row, seat.seatNumber)} —{' '}
                                                    {selectable ? SEAT_STATE_LABEL[state] : 'nu este încă pus în vânzare'}
                                                </title>
                                            </circle>
                                        )
                                    })}

                                {isExpanded && seats === 'loading' && (
                                    <text x={centroid.x} y={centroid.y} fill="#aaa" fontSize={12} textAnchor="middle">
                                        Se încarcă locurile...
                                    </text>
                                )}

                                {isExpanded && seats !== undefined && seats !== 'loading' && !Array.isArray(seats) && (
                                    <text x={centroid.x} y={centroid.y} fill="#e07" fontSize={12} textAnchor="middle">
                                        Locurile nu s-au putut încărca
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
                    gap: '0.75rem',
                }}
            >
                <Legend color={SEAT_COLOR.available.fill} label="Liber" />
                <Legend color={SEAT_COLOR['held-by-other'].fill} label="Rezervat" />
                <Legend color={SEAT_COLOR.sold.fill} label="Vândut" />
                <Legend color={SEAT_COLOR['mine-held'].fill} label="Rezervat de tine" />
                <Legend color={SEAT_COLOR['mine-paid'].fill} label="Biletele tale" />
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

// Top-right zoom cluster - buttons on top of the existing wheel-zoom/drag-pan,
// which keeps working unchanged alongside these. SeatMap.tsx/VenueEditor.tsx
// each keep their own copy since these components don't otherwise share a UI
// module.
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
        <div style={{ position: 'absolute', top: 8, right: 8, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <button type="button" className="zoom-btn" onClick={onZoomIn} style={buttonStyle} title="Mărește" aria-label="Mărește">
                +
            </button>
            <button type="button" className="zoom-btn" onClick={onZoomOut} style={buttonStyle} title="Micșorează" aria-label="Micșorează">
                −
            </button>
            <button type="button" className="zoom-btn" onClick={onReset} style={buttonStyle} title="Resetează vizualizarea" aria-label="Resetează vizualizarea">
                ⟳
            </button>
        </div>
    )
}
