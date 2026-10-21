package ro.axonsoft.services.gateway.dto.event_dto;

public record PaymentRequest(Long payerId, Integer amountCents, String idempotencyKey) {
}
