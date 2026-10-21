package ro.axonsoft.services.event.eventSearch;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.axonsoft.services.event.exception.EventNotFoundException;
import ro.axonsoft.services.event.layout.DTOs.VenueLayoutResponseDTO;
import ro.axonsoft.services.event.layout.VenueLayoutService;
import ro.axonsoft.services.event.model.Event;
import ro.axonsoft.services.event.model.EventStatus;
import ro.axonsoft.services.event.repository.EventPerformerRepository;
import ro.axonsoft.services.event.repository.EventRepository;

import java.util.List;

@Service
public class EventDetailService {

    private final EventRepository eventRepository;
    private final VenueLayoutService venueLayoutService;
    private final EventPerformerRepository eventPerformerRepository;

    public EventDetailService(EventRepository eventRepository, VenueLayoutService venueLayoutService, EventPerformerRepository eventPerformerRepository) {
        this.eventRepository = eventRepository;
        this.venueLayoutService = venueLayoutService;
        this.eventPerformerRepository = eventPerformerRepository;
    }

    @Transactional(readOnly = true)
    public EventWithSeatMapDTO getEventWithSeatMap(Long eventId, boolean includeUnpublished) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (!includeUnpublished && event.getStatus() != EventStatus.LIVE) {
            throw new EventNotFoundException(eventId);
        }

        // venue is a LAZY proxy - getId() reads the FK already held on the
        // proxy, no extra query, and getLayout() is cached by venueId anyway.
        VenueLayoutResponseDTO venue = venueLayoutService.getLayout(event.getVenue().getId());

        List<PerformerScheduleDTO> performers = eventPerformerRepository.findPerformerNames(List.of(eventId)).stream().map(PerformerScheduleDTO::from).toList();
        return EventWithSeatMapDTO.from(event, venue, performers);
    }
}
