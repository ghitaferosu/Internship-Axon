package ro.axonsoft.services.event.feedingJob;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.axonsoft.services.event.exception.InvalidPerformerAppearanceException;
import ro.axonsoft.services.event.exception.PerformerNotFoundException;
import ro.axonsoft.services.event.exception.VenueNotFoundException;
import ro.axonsoft.services.event.feedingJob.DTOs.EventSeedingRequestDTO;
import ro.axonsoft.services.event.model.*;
import ro.axonsoft.services.event.repository.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EventSeedingService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final PerformerRepository performerRepository;
    private final EventPerformerRepository eventPerformerRepository;
    private final TicketRepository ticketRepository;
    private final SeatRepository seatRepository;

    public EventSeedingService(EventRepository eventRepository,
                               VenueRepository venueRepository,
                               PerformerRepository performerRepository,
                               EventPerformerRepository eventPerformerRepository,
                               TicketRepository ticketRepository, SeatRepository seatRepository) {
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.performerRepository = performerRepository;
        this.eventPerformerRepository = eventPerformerRepository;
        this.ticketRepository = ticketRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public List<EventPerformersSeedingResponseDTO> seedEvents(List<EventSeedingRequestDTO> requests) {
        return requests.stream().map(this::seedEvent).toList();
    }

    private EventPerformersSeedingResponseDTO seedEvent(EventSeedingRequestDTO request) {
        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new VenueNotFoundException(request.venueId()));

        Event event = eventRepository.save(
                new Event(venue, request.name(), request.country(), request.city(), request.eventStartHour()));

        event.setStatus(EventStatus.SETTING_UP);
        eventRepository.save(event);

        ticketRepository.materializeTickets(event.getId(), venue.getId(), request.basePriceCents());

        event.setStatus(EventStatus.LIVE);
        eventRepository.save(event);

        List<PerformerAppearanceSeedingResponseDTO> appearances = Optional.ofNullable(request.performers())
                .orElse(List.of())
                .stream()
                .map(appearance -> seedAppearance(event, appearance))
                .toList();

        return new EventPerformersSeedingResponseDTO(
                event.getId(),
                venue.getId(),
                event.getName(),
                event.getCountry(),
                event.getCity(),
                event.getEventStartHour(),
                event.getStatus(),
                appearances
        );
    }


    private PerformerAppearanceSeedingResponseDTO seedAppearance(Event event, PerformerAppearanceSeedingRequestDTO appearance) {
        if (!appearance.endHour().isAfter(appearance.startHour())) {
            throw new InvalidPerformerAppearanceException(
                    "Ora de final trebuie să fie după ora de început pentru artistul " + appearance.performerId());
        }

        Performer performer = performerRepository.findById(appearance.performerId())
                .orElseThrow(() -> new PerformerNotFoundException(appearance.performerId()));

        EventPerformer eventPerformer = new EventPerformer(event, performer, appearance.startHour(), appearance.endHour());
        eventPerformer = eventPerformerRepository.save(eventPerformer);

        return new PerformerAppearanceSeedingResponseDTO(
                eventPerformer.getId(),
                performer.getId(),
                performer.getName(),
                appearance.startHour(),
                appearance.endHour()
        );
    }
}
