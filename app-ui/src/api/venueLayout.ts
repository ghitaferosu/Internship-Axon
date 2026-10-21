import type { Page, Seat, VenueDrawRequest, VenueLayout, VenueSeedingResponse, VenueSummary } from '../types/venueLayout'

import {getJson, postJson} from './client'

export function getVenueLayout(venueId: string | number): Promise<VenueLayout> {
    return getJson<VenueLayout>(`/api/venues/${venueId}/layout`)
}

// Picker source for admin forms (EventEditor's venueId, SeatMapPage's venue
// selector) - newest-first (VenueRepository.findAllByOrderByIdDesc), so a
// just-seeded venue is right at the top.
export function getVenues(): Promise<VenueSummary[]> {
    return getJson<VenueSummary[]>('/api/venues')
}

export function saveVenue(venue: VenueDrawRequest): Promise<VenueSeedingResponse> {
    return postJson<VenueSeedingResponse>('/api/admin/venues', venue)
}

const SEATS_PAGE_SIZE = 200

// Backend paginates /sections/{id}/seats (see VenueLayoutService.getSeats) so
// no single request/response is unbounded for a huge section. This yields
// each page's seats as they arrive, so the caller can render incrementally
// instead of waiting on the whole section to load.
export async function* getSectionSeatPages(
    sectionId: number,
    size = SEATS_PAGE_SIZE,
): AsyncGenerator<Seat[]> {
    let page = 0
    while (true) {
        const pageDto = await getJson<Page<Seat>>(`/api/sections/${sectionId}/seats`, { page, size })
        yield pageDto.content
        if (page >= pageDto.totalPages - 1) break
        page += 1
    }
}
