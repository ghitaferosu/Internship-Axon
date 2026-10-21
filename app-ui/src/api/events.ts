import type {
    EventSearchCriteria,
    EventSearchPage,
    EventWithSeatMap,
    Page,
    SeatWithStatus,
} from '../types/booking'

import { getJson } from './client'


export function searchEvents(criteria: EventSearchCriteria, page: number, size: number): Promise<EventSearchPage> {
    return getJson<EventSearchPage>('/api/events', { ...criteria, page, size })
}

export function getEventWithSeatMap(eventId: number | string): Promise<EventWithSeatMap> {
    return getJson<EventWithSeatMap>(`/api/events/${eventId}`)
}

const SEATS_PAGE_SIZE = 200

// Same paginated-streaming shape as getSectionSeatPages in venueLayout.ts,
// but with eventId passed through so each seat also carries its live ticket
// status (SeatWithStatusDTO) instead of just row/seatNumber identity.
export async function* getEventSectionSeatPages(
    sectionId: number,
    eventId: number,
    size = SEATS_PAGE_SIZE,
): AsyncGenerator<SeatWithStatus[]> {
    let page = 0
    while (true) {
        const pageDto = await getJson<Page<SeatWithStatus>>(
            `/api/sections/${sectionId}/seats`,
            { eventId, page, size },
        )   
        yield pageDto.content
        if (page >= pageDto.totalPages - 1) break
        page += 1
    }
}
