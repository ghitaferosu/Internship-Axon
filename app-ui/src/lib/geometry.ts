import type { Point, SectionLayout } from '../types/venueLayout'

export interface BoundingBox {
    x: number
    y: number
    width: number
    height: number
}

// rowSpacing (row-to-row) and colSpacing (seat-to-seat) are independent - one
// shared value used to force whichever axis was tighter onto the other,
// under-filling a section whose length:width ratio didn't happen to match its
// rows:seatsPerRow ratio. Sections seeded before colSpacing existed persisted
// it as 0 (Java's int default, never populated) - fall back to rowSpacing for
// those rather than collapsing every seat's perpendicular offset to zero.
export function effectiveColSpacing(section: SectionLayout): number {
    return section.colSpacing > 0 ? section.colSpacing : section.rowSpacing
}

// Mirrors the backend's SectionGeometryCalculator convention: a section's
// `angle`/`origin`/`rowSpacing`/`colSpacing` (from the layout endpoint)
// describe where row `rowStart` sits and how far apart rows/seats are;
// individual seat positions are only ever needed once a section is expanded,
// so they're computed here rather than sent over the wire for every seat.
export function seatPosition(section: SectionLayout, row: number, seatNumber: number): Point {
    const { angle, originX, originY, rowSpacing, rowStart, seatStart, seatEnd } = section
    const mid = (seatStart + seatEnd) / 2
    const rowOffset = (row - rowStart) * rowSpacing
    const perpOffset = (seatNumber - mid) * effectiveColSpacing(section)

    // Rows extend away from the stage along `angle`; seats within a row spread
    // along the perpendicular axis - the same rotate-by-angle step
    // SectionGeometryCalculator applies server-side to a whole polygon,
    // applied here to a single (rowOffset, perpOffset) point instead.
    const cos = Math.cos(angle)
    const sin = Math.sin(angle)
    return {
        x: originX + rowOffset * cos - perpOffset * sin,
        y: originY + rowOffset * sin + perpOffset * cos,
    }
}

// Bounding box of a section's drawn outline - used for the SVG viewBox and
// for zooming a section to fill the viewport. This is a viewport-math
// helper only; the outline itself (not this box) is what actually gets
// rendered - see SeatMap.tsx's <polygon points={sectionPolygonPoints(...)}>.
export function sectionBoundingBox(section: SectionLayout): BoundingBox {
    const xs = section.vertices.map((v) => v.x)
    const ys = section.vertices.map((v) => v.y)
    const minX = Math.min(...xs)
    const maxX = Math.max(...xs)
    const minY = Math.min(...ys)
    const maxY = Math.max(...ys)

    return { x: minX, y: minY, width: maxX - minX, height: maxY - minY }
}

// Centroid of a section's drawn outline - used to center its name label.
// Same convention SectionGeometryCalculator uses server-side to derive angle.
export function sectionCentroid(section: SectionLayout): Point {
    const sum = section.vertices.reduce((acc, v) => ({ x: acc.x + v.x, y: acc.y + v.y }), { x: 0, y: 0 })
    return { x: sum.x / section.vertices.length, y: sum.y / section.vertices.length }
}

// SVG <polygon points="..."> attribute value from a section's drawn vertices.
export function sectionPolygonPoints(section: SectionLayout): string {
    return section.vertices.map((v) => `${v.x},${v.y}`).join(' ')
}

// Desired total gap between adjacent seat edges (in venue units) when
// spacing is generous enough to afford it, and the smallest radius that
// still reads as a seat rather than a speck - both only ever apply below
// the hard ceiling in seatRadius() itself, never above it.
const SEAT_GAP = 4
const SEAT_VISUAL_FLOOR = 1.5

// Seat dot radius: sized off whichever of rowSpacing/colSpacing is tighter
// (they're independent - row-to-row vs seat-to-seat). `spacing / 2` is a
// hard ceiling - a radius past it means adjacent seats touch or overlap.
// This used to be a flat floor of 1.5 with no such ceiling, which was fine
// as long as spacing stayed generous, but once SectionGeometryCalculator's
// own floor let spacing clamp down to 1-2 units, 1.5 could be *larger* than
// spacing/2, rendering seats as a solid overlapping blob instead of a
// legible grid. SectionGeometryCalculator now rejects sections that dense at
// write time rather than silently clamping (see its own MIN_SPACING) - this
// ceiling is what keeps already-seeded/legacy sections safe too, regardless
// of how tight their stored spacing turns out to be. Shared by SeatMap.tsx
// and BookingSeatMap.tsx so seat sizing never drifts between them.
export function seatRadius(section: SectionLayout): number {
    const spacing = Math.min(section.rowSpacing, effectiveColSpacing(section))
    const maxRadius = spacing / 2
    const desired = Math.max(SEAT_VISUAL_FLOOR, maxRadius - SEAT_GAP / 2)
    return Math.min(desired, maxRadius)
}

// Shared "Row X, seat Y" wording for seat tooltips/labels, kept in one place
// so SeatMap.tsx and BookingSeatMap.tsx never drift on phrasing.
export function seatLabel(row: number, seatNumber: number): string {
    return `Rândul ${row}, locul ${seatNumber}`
}
