package ro.axonsoft.services.gateway.dto.event_dto;


import java.time.OffsetDateTime;

public record TicketHistoryResponse(Long ticketId, Long eventId, String eventName, OffsetDateTime eventStartHour, String city, String venueName,
                                    String sectionName, int row, int seatNumber, String status, int priceCents, OffsetDateTime heldUntil) {
}
