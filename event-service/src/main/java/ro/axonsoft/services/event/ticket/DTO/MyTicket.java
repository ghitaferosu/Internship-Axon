package ro.axonsoft.services.event.ticket.DTO;

import java.time.OffsetDateTime;

public record MyTicket(Long ticketId, Long eventId, Long seatId, Long sectionId, int row, int seatNumber, String status, int priceCents, OffsetDateTime heldUntil) {
}
