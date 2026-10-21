package ro.axonsoft.services.event.eventSearch;

import java.time.OffsetDateTime;

public record EventPerformerName(Long eventId, String performerName, OffsetDateTime startHour, OffsetDateTime endHour) {
}
