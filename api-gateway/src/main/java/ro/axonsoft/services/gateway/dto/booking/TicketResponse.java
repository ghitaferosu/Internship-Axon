package ro.axonsoft.services.gateway.dto.booking;

public record TicketResponse(Long ticketId, String status, Long heldBy, String heldUntil, int priceCents) {
}
