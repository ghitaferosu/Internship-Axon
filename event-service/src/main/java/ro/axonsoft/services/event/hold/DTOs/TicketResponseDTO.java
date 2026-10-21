package ro.axonsoft.services.event.hold.DTOs;

import ro.axonsoft.services.event.model.Ticket;
import ro.axonsoft.services.event.model.TicketStatus;

import java.time.OffsetDateTime;

// priceCents rides along so the client has, right from the /hold response,
// the exact amount PaymentService.pay() will require in PaymentRequestDTO -
// no separate ticket-price lookup endpoint needed for the booking flow.
public record TicketResponseDTO(Long ticketId, TicketStatus status, Long heldBy, OffsetDateTime heldUntil, int priceCents) {
    public static TicketResponseDTO from(Ticket ticket) {
        return new TicketResponseDTO(
                ticket.getId(), ticket.getStatus(), ticket.getHeldBy(), ticket.getHeldUntil(), ticket.getPriceCents());
    }
}
