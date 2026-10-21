package ro.axonsoft.services.event.eventSearch;

import java.time.OffsetDateTime;

public record EventSearchCriteria(
        String country,
        String city,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo,
        String keyword
) {
}
