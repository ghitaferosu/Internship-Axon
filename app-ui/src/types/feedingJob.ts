// Mirrors event-service's data-feeding-job DTOs: PerformerSeedingRequestDTO/
// PerformerSeedingResponseDTO (DataFeedingPerformersController) and
// EventSeedingRequestDTO/EventPerformersSeedingResponseDTO/
// PerformerAppearanceSeedingRequestDTO/PerformerAppearanceSeedingResponseDTO
// (DataFeedingEventsController). Venue seeding's own types already live in
// types/venueLayout.ts (VenueDrawRequest/VenueSeedingResponse) - unchanged.

export interface PerformerSeedingRequest {
    name: string
    genre: string
    description: string
}

export interface PerformerSeedingResponse {
    id: number
    name: string
    description: string
    genre: string
}

export interface PerformerAppearanceSeedingRequest {
    performerId: number
    startHour: string
    endHour: string
}

export interface PerformerAppearanceSeedingResponse {
    id: number
    performerId: number
    performerName: string
    startHour: string
    endHour: string
}

export interface EventSeedingRequest {
    venueId: number
    name: string
    country: string
    city: string
    eventStartHour: string
    basePriceCents: number
    performers: PerformerAppearanceSeedingRequest[]
}

export type EventStatus = 'DRAFT' | 'SETTING_UP' | 'LIVE'

export interface EventPerformersSeedingResponse {
    id: number
    venueId: number
    name: string
    country: string
    city: string
    eventStartHour: string
    status: EventStatus
    performers: PerformerAppearanceSeedingResponse[]
}
