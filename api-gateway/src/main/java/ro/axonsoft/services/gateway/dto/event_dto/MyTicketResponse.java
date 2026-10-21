package ro.axonsoft.services.gateway.dto.event_dto;

import java.time.OffsetDateTime;

public record MyTicketResponse(Long ticketId, Long eventId, Long seatId, Long sectionId, int row, int seatNumber, String status, int priceCents, OffsetDateTime heldUntil) {
}
