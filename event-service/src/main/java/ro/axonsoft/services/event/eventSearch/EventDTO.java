package ro.axonsoft.services.event.eventSearch;

import ro.axonsoft.services.event.model.Event;

import java.time.Instant;

public record EventDTO (
        Long id,
        String name,
        String country,
        String city,
        Instant eventStartHour,
        String status,
        String venueName
){
    public static EventDTO from (Event event){
        return new EventDTO(
                event.getId(),
                event.getName(),
                event.getCountry(),
                event.getCity(),
                event.getEventStartHour().toInstant(),
                event.getStatus().name(),
                event.getVenue().getName()
        );
    }
}
