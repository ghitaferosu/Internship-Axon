package ro.axonsoft.services.event.eventSearch;

import java.time.Instant;

// A performer's appearance within an event, schedule included - what
// EventWithSeatMapDTO exposes per performer. EventSummaryDTO (the search
// listing) stays names-only on purpose; only the single-event detail view
// carries start/end.
public record PerformerScheduleDTO(String performerName, Instant startHour, Instant endHour) {
    public static PerformerScheduleDTO from(EventPerformerName ep) {
        return new PerformerScheduleDTO(ep.performerName(), ep.startHour().toInstant(), ep.endHour().toInstant());
    }
}
