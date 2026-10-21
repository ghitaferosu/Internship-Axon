import type {
    EventPerformersSeedingResponse,
    EventSeedingRequest,
    PerformerSeedingRequest,
    PerformerSeedingResponse,
} from '../types/feedingJob'

import { getJson, postJson } from './client'

// Picker source for EventEditor's performer appearances - newest-first
// (PerformerRepository.findAllByOrderByIdDesc), so a just-created performer
// is right at the top.
export function getPerformers(): Promise<PerformerSeedingResponse[]> {
    return getJson<PerformerSeedingResponse[]>('/api/performers')
}

// Both feeding-job endpoints take/return arrays (DataFeedingPerformersController
// /DataFeedingEventsController seed in batches) - the admin forms below just
// happen to submit one-element arrays at a time.
export function seedPerformers(requests: PerformerSeedingRequest[]): Promise<PerformerSeedingResponse[]> {
    return postJson<PerformerSeedingResponse[]>('/api/admin/performers', requests)
}

export function seedEvents(requests: EventSeedingRequest[]): Promise<EventPerformersSeedingResponse[]> {
    return postJson<EventPerformersSeedingResponse[]>('/api/admin/events', requests)
}
