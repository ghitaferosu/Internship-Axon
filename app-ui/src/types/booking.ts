// Mirrors event-service's booking-flow DTOs: EventDTO/PageResponse
// (eventSearch), EventWithSeatMapDTO (eventSearch), SeatWithStatusDTO
// (layout), TicketResponseDTO (hold), PaymentResponseDTO (payment).

import type { VenueLayout } from './venueLayout'

export type EventStatus = 'DRAFT' | 'SETTING_UP' | 'LIVE'
export type TicketStatus = 'AVAILABLE' | 'HELD' | 'SOLD'
export type PaymentStatus = 'SUCCEEDED'

// GET /api/events - one row of the search results (EventDTO).
export interface EventSummary {
    id: number
    name: string
    country: string
    city: string
    eventStartHour: string
    status: EventStatus
    performers: string[]
    venueName: string
}

// GET /api/events?... - Spring Data Page-shaped (PageResponse.java).
export interface EventSearchPage {
    content: EventSummary[]
    page: number
    pageSize: number
    totalElements: number
    totalPages: number
}

export interface EventSearchCriteria {
    country?: string
    city?: string
    dateFrom?: string
    dateTo?: string
    keyword?: string
}

// A performer's appearance within an event, schedule included
// (PerformerScheduleDTO). EventSummary above stays names-only on purpose -
// only the single-event detail view carries start/end.
export interface PerformerSchedule {
    performerName: string
    startHour: string
    endHour: string
}

// GET /api/events/{eventId} - event + its venue's seat map in one call
// (EventWithSeatMapDTO). venue carries section geometry/counts only, same
// O(sections) shape as VenueLayout - actual seats+status stay paginated
// per section (getEventSectionSeatPages below).
export interface EventWithSeatMap {
    id: number
    name: string
    country: string
    city: string
    eventStartHour: string
    status: EventStatus
    performers: PerformerSchedule[]
    venue: VenueLayout
}

// GET /sections/{sectionId}/seats?eventId=... - a seat plus its live ticket
// status for that event (SeatWithStatusDTO). status is absent (undefined,
// @JsonInclude NON_NULL on the backend) when eventId wasn't passed.
export interface SeatWithStatus {
    id: number
    row: number
    seatNumber: number
    status?: TicketStatus
    heldUntil?: string | null
}

export interface Page<T> {
    content: T[]
    page: number
    pageSize: number
    totalElements: number
    totalPages: number
}

// Response of hold/confirm (TicketResponseDTO). priceCents rides along so
// the booking page has, right from the hold response, the exact amount
// PaymentRequestDTO must carry - no separate price lookup.
export interface TicketResponse {
    ticketId: number
    status: TicketStatus
    heldBy: number | null
    heldUntil: string | null
    priceCents: number
}

// POST /api/events/{eventId}/seats/{seatId}/pay request body
export interface PaymentRequest {
    amountCents: number
    idempotencyKey: string
    orderId: string
    orderSize: number
}

// Response of pay (PaymentResponseDTO).
export interface PaymentResponse {
    paymentId: number
    ticketId: number
    payerId: number
    amountCents: number
    status: PaymentStatus
    createdAt: string
}
