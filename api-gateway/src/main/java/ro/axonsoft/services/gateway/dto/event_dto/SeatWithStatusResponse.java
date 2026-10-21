package ro.axonsoft.services.gateway.dto.event_dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeatWithStatusResponse(Long id, int row, int seatNumber, String status, OffsetDateTime heldUntil) {
}
