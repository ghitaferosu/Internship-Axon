package ro.axonsoft.services.gateway.dto.event_dto;

import java.time.OffsetDateTime;

public record TicketResponse(
        Long ticketId, TicketStatus status,
        Long heldBy, OffsetDateTime heldUntil
) {
}
