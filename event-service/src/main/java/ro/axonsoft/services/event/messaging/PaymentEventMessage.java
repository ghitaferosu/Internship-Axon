package ro.axonsoft.services.event.messaging;

import ro.axonsoft.services.event.model.Payment;
import ro.axonsoft.services.event.model.Ticket;
import ro.axonsoft.services.event.payment.DTOs.PaymentRequestDTO;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentEventMessage(
        UUID messageId,
        String type,
        Long paymentId,
        Long eventId,
        Long seatId,
        Long ticketId,
        Long payerId,
        int amountCents,
        UUID orderId,
        Integer orderSize,
        String eventName,
        String venueName,
        String city,
        OffsetDateTime eventStartHour,
        String sectionName,
        int rowNumber,
        int seatNumber,
        OffsetDateTime occurredAt
) {
    public String partitionKey() {
        return eventId + ":" + seatId;
    }

    public static PaymentEventMessage succeeded(Long eventId, Long seatId,
                                                Payment payment, PaymentRequestDTO request) {
        Ticket ticket = payment.getTicket();
        var event = ticket.getEvent();
        var seat = ticket.getSeat();

        return new PaymentEventMessage(
                UUID.randomUUID(), "payment.succeeded",
                payment.getId(), eventId, seatId, ticket.getId(),
                payment.getPayerId(), payment.getAmountCents(),
                request.orderId(), request.orderSize(),
                event.getName(),
                event.getVenue().getName(),
                event.getCity(),
                event.getEventStartHour(),
                seat.getSection().getSectionName(),
                seat.getRow(),
                seat.getSeatNumber(),
                OffsetDateTime.now());
    }
}
