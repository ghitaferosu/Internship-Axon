package ro.axonsoft.services.gateway.dto.event_dto;

import java.time.Instant;
import java.time.OffsetDateTime;

public record TicketStatusMessage(Long eventId, Long seatId, String status, Instant occurredAt, OffsetDateTime heldUntil) {
}
