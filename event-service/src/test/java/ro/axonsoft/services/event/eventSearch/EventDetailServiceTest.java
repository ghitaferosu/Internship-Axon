package ro.axonsoft.services.event.eventSearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ro.axonsoft.services.event.exception.EventNotFoundException;
import ro.axonsoft.services.event.layout.DTOs.VenueLayoutResponseDTO;
import ro.axonsoft.services.event.layout.VenueLayoutService;
import ro.axonsoft.services.event.model.Event;
import ro.axonsoft.services.event.model.EventStatus;
import ro.axonsoft.services.event.model.Venue;
import ro.axonsoft.services.event.repository.EventPerformerRepository;
import ro.axonsoft.services.event.repository.EventRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDetailServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private VenueLayoutService venueLayoutService;
    @Mock
    private EventPerformerRepository eventPerformerRepository;

    private EventDetailService newService() {
        return new EventDetailService(eventRepository, venueLayoutService, eventPerformerRepository);
    }

    private Event anEvent() {
        Venue venue = new Venue("National Arena", "123 Main St", 1200);
        venue.setId(7L);
        Event event = new Event(venue, "Rock Fest", "Romania", "Cluj-Napoca",
                OffsetDateTime.of(2026, 9, 1, 20, 0, 0, 0, ZoneOffset.UTC));
        event.setId(1L);
        event.setStatus(EventStatus.LIVE);
        return event;
    }

    @Test
    void combinesEventAndVenueLayout() {
        Event event = anEvent();
        VenueLayoutResponseDTO layout = new VenueLayoutResponseDTO(7L, "National Arena", "123 Main St", 1200, List.of());
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(venueLayoutService.getLayout(7L)).thenReturn(layout);

        EventWithSeatMapDTO result = newService().getEventWithSeatMap(1L, false);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Rock Fest");
        assertThat(result.country()).isEqualTo("Romania");
        assertThat(result.city()).isEqualTo("Cluj-Napoca");
        assertThat(result.status()).isEqualTo("LIVE");
        assertThat(result.venue()).isSameAs(layout);
    }

    @Test
    void throwsWhenEventMissing() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().getEventWithSeatMap(99L, false))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void hidesAnUnpublishedEventFromRegularCallers() {
        Event event = anEvent();
        event.setStatus(EventStatus.DRAFT);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> newService().getEventWithSeatMap(1L, false))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void returnsAnUnpublishedEventToAnOrganizer() {
        Event event = anEvent();
        event.setStatus(EventStatus.SETTING_UP);
        VenueLayoutResponseDTO layout = new VenueLayoutResponseDTO(7L, "National Arena", "123 Main St", 1200, List.of());
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(venueLayoutService.getLayout(7L)).thenReturn(layout);

        EventWithSeatMapDTO result = newService().getEventWithSeatMap(1L, true);

        assertThat(result.status()).isEqualTo("SETTING_UP");
    }
}
