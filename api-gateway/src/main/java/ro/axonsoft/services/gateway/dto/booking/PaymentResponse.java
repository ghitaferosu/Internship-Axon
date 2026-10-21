package ro.axonsoft.services.gateway.dto.booking;

public record PaymentResponse(Long paymentId, Long ticketId, Long payerId, int amountCents, String status, String createdAt) {
}
