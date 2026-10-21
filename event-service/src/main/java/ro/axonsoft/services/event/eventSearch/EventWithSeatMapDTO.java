package ro.axonsoft.services.event.eventSearch;

import ro.axonsoft.services.event.layout.DTOs.VenueLayoutResponseDTO;
import ro.axonsoft.services.event.model.Event;

import java.time.Instant;
import java.util.List;

// The Week-3 "event together with its seat map" deliverable. venue carries
// section geometry/counts only (VenueLayoutService.getLayout is already
// @Cacheable) - it deliberately does NOT inline every seat, same O(sections)
// reasoning as VenueLayoutResponseDTO itself. Callers still page through
// actual seats (+ ticket status) per section via
// GET /sections/{sectionId}/seats?eventId=..., using venue.sections[].id.
public record EventWithSeatMapDTO(
        Long id,
        String name,
        String country,
        String city,
        Instant eventStartHour,
        String status,
        List<PerformerScheduleDTO> performers,
        VenueLayoutResponseDTO venue
) {
    public static EventWithSeatMapDTO from(Event event, VenueLayoutResponseDTO venue, List<PerformerScheduleDTO> performers) {
        return new EventWithSeatMapDTO(
                event.getId(),
                event.getName(),
                event.getCountry(),
                event.getCity(),
                event.getEventStartHour().toInstant(),
                event.getStatus().name(),
                performers,
                venue
        );
    }
}
