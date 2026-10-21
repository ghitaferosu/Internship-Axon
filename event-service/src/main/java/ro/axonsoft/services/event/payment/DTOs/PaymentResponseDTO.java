package ro.axonsoft.services.event.payment.DTOs;

import ro.axonsoft.services.event.model.Payment;
import ro.axonsoft.services.event.model.PaymentStatus;

import java.time.OffsetDateTime;

public record PaymentResponseDTO(
        Long paymentId, Long ticketId, Long payerId,
        int amountCents, PaymentStatus status, OffsetDateTime createdAt
) {
    public static PaymentResponseDTO from(Payment payment) {
        return new PaymentResponseDTO(
                payment.getId(), payment.getTicket().getId(), payment.getPayerId(),
                payment.getAmountCents(), payment.getStatus(), payment.getCreatedAt());
    }
}