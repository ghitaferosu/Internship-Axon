package ro.axonsoft.services.gateway.dto.event_dto;

import java.time.Instant;

// Mirrors event-service's PerformerScheduleDTO - a performer's appearance
// within an event, schedule included. EventSummaryResponse (the search
// listing) stays names-only on purpose; only EventWithSeatMapResponse
// carries start/end.
public record PerformerScheduleResponse(String performerName, Instant startHour, Instant endHour) {
}
