package ro.axonsoft.services.event.messaging;
import ro.axonsoft.services.event.model.Ticket;
import ro.axonsoft.services.event.model.TicketStatus;

import java.time.OffsetDateTime;
import java.util.UUID;


public record TicketEventMessage(
        UUID messageId,
        TicketEventType type,
        Long eventId,
        Long seatId,
        Long ticketId,
        TicketStatus status,
        Long holderId,
        OffsetDateTime heldUntil,
        OffsetDateTime occurredAt
) {
    public String partitionKey() {
        return eventId + ":" + seatId;
    }

    public static TicketEventMessage held(Long eventId, Long seatId, Ticket ticket) {
        return of(TicketEventType.SEAT_HELD, eventId, seatId, ticket, ticket.getHeldBy());
    }

    public static TicketEventMessage released(Long eventId, Long seatId, Ticket ticket, Long holderId) {
        return of(TicketEventType.SEAT_RELEASED, eventId, seatId, ticket, holderId);
    }

    public static TicketEventMessage sold(Long eventId, Long seatId, Ticket ticket) {
        return of(TicketEventType.SEAT_SOLD, eventId, seatId, ticket, ticket.getOwnerId());
    }

    private static TicketEventMessage of(TicketEventType type, Long eventId, Long seatId,
                                         Ticket ticket, Long holderId) {
        return new TicketEventMessage(
                UUID.randomUUID(),
                type,
                eventId,
                seatId,
                ticket.getId(),
                ticket.getStatus(),
                holderId,
                ticket.getHeldUntil(),
                OffsetDateTime.now()
        );
    }
}