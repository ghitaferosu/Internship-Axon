package ro.axonsoft.services.event.layout.DTOs;

import com.fasterxml.jackson.annotation.JsonInclude;
import ro.axonsoft.services.event.model.TicketStatus;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeatWithStatusDTO(Long id, int row, int seatNumber, TicketStatus status, OffsetDateTime heldUntil) {
}
