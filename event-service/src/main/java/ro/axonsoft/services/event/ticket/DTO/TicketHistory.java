package ro.axonsoft.services.event.ticket.DTO;

import ro.axonsoft.services.event.model.TicketStatus;

import java.time.OffsetDateTime;

public record TicketHistory(Long ticketId, Long eventId, String eventName, OffsetDateTime eventStartHour, String city, String venueName,
                            String sectionName, int row, int seatNumber, TicketStatus status, int priceCents, OffsetDateTime heldUntil) {
}
