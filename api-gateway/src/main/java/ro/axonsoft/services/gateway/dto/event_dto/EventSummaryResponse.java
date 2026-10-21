package ro.axonsoft.services.gateway.dto.event_dto;

import java.time.Instant;
import java.util.List;

public record EventSummaryResponse(Long id, String name, String country, String city, Instant eventStartHour, String status,
                                   String venueName, List<String> performers) {
}
