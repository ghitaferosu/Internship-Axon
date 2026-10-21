package ro.axonsoft.services.gateway.dto.event_dto;

import java.time.OffsetDateTime;

public record PaymentResponse(
        Long paymentId, Long ticketId,
        Long payerId, int amountCents,
        PaymentStatus status, OffsetDateTime createdAt
) {
}
