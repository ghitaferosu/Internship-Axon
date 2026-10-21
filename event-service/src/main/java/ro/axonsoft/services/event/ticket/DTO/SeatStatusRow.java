package ro.axonsoft.services.event.ticket.DTO;

import ro.axonsoft.services.event.model.TicketStatus;

import java.time.OffsetDateTime;

public record SeatStatusRow(Long seatId, TicketStatus status, OffsetDateTime heldUntil) {
}
