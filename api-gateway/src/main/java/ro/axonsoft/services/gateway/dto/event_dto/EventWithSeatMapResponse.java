package ro.axonsoft.services.gateway.dto.event_dto;

import java.time.Instant;
import java.util.List;

public record EventWithSeatMapResponse(
        Long id,
        String name,
        String country,
        String city,
        Instant eventStartHour,
        String status,
        List<PerformerScheduleResponse> performers,
        VenueLayoutResponse venue

) {
}
