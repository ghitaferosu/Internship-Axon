package ro.axonsoft.services.event.eventSearch;

import java.time.Instant;
import java.util.List;

public record EventSummaryDTO(Long id, String name, String country, String city, Instant eventStartHour, String status,
                              String venueName, List<String> performers) {

    public static EventSummaryDTO fromEventAndPerformers(EventDTO event, List<String> performers) {
        return new EventSummaryDTO(event.id(), event.name(), event.country(), event.city(), event.eventStartHour(), event.status(),
                event.venueName(), performers);
    }
}
