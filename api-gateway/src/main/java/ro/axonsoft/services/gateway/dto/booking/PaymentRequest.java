package ro.axonsoft.services.gateway.dto.booking;

import java.util.UUID;

public record PaymentRequest(Integer amountCents, String idempotencyKey,
                             UUID orderId, Integer orderSize) {
}
