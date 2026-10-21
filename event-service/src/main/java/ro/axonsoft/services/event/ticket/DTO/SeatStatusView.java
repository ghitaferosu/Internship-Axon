package ro.axonsoft.services.event.ticket.DTO;

import ro.axonsoft.services.event.model.TicketStatus;

import java.time.OffsetDateTime;

// Statusul unui loc asa cum il vede clientul: heldUntil e populat doar cat
// timp hold-ul e inca valid, ca browserul sa stie exact cand expira.
public record SeatStatusView(TicketStatus status, OffsetDateTime heldUntil) {
}
