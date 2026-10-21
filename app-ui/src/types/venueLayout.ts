// Mirrors event-service's VenueLayoutResponseDTO / SectionLayoutDTO / SeatResponseDTO /
// PageDTO, plus the venue-authoring draw/save DTOs (VenueDrawRequestDTO /
// SectionDrawRequestDTO / VenueSeedingResponseDTO / SectionSeedingResponseDTO).
//
// Section geometry is angle + drawn-polygon based (SectionGeometryCalculator),
// not the older compass-direction one: a section carries `angle` (radians)
// and its drawn outline (`vertices`), not a `direction` enum. rowSpacing and
// colSpacing are independent (row-to-row vs seat-to-seat) so a section's
// rendered grid fills its actual drawn outline instead of one shared value
// getting pulled down to whichever axis is tighter.

export interface Point {
    x: number
    y: number
}

export interface SectionLayout {
    id: number
    name: string
    angle: number
    rowStart: number
    rowEnd: number
    seatStart: number
    seatEnd: number
    seatCount: number
    originX: number
    originY: number
    rowSpacing: number
    colSpacing: number
    vertices: Point[]
}

export interface VenueLayout {
    id: number
    name: string
    address: string
    noSeats: number
    sections: SectionLayout[]
}

// GET /venues - picker row (VenueSummaryDTO). Deliberately lighter than
// VenueLayout: no sections, so listing every venue stays O(venues).
export interface VenueSummary {
    id: number
    name: string
    address: string
    noSeats: number
}

export interface Seat {
    id: number
    row: number
    seatNumber: number
}

export interface Page<T> {
    content: T[]
    page: number
    pageSize: number
    totalElements: number
    totalPages: number
}

// --- Venue authoring (VenueEditor "Save venue") ---

export interface SectionDrawRequest {
    name: string
    points: Point[]
    rows: number
    seatsPerRow: number
    // Facing angle (radians), when already known - a Rectangle/Trapezoid built
    // from VenueEditor's parametric shape state carries its real angle here
    // (possibly rotated by the admin); omitted/undefined for a freeform-drawn
    // section, where SectionGeometryCalculator falls back to guessing it from
    // the polygon's centroid. See SectionGeometryCalculator.compute()'s
    // explicit-angle overload for why the guess is wrong once a shape can be
    // rotated to face any direction.
    angle?: number
}

export interface VenueDrawRequest {
    name: string
    address: string
    sections: SectionDrawRequest[]
}

// Matches VenueSeedingResponseDTO/SectionSeedingResponseDTO: what actually
// got persisted, vertices included, returned after a successful save.
export interface SectionSeedingResponse {
    id: number
    name: string
    angle: number
    originX: number
    originY: number
    rowSpacing: number
    colSpacing: number
    rowStart: number
    rowEnd: number
    seatStart: number
    seatEnd: number
    vertices: Point[]
}

export interface VenueSeedingResponse {
    id: number
    name: string
    address: string
    noSeats: number
    sections: SectionSeedingResponse[]
}
