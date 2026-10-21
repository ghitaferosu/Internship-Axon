package ro.axonsoft.services.event.hold;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ro.axonsoft.services.event.messaging.TicketEventMessage;
import ro.axonsoft.services.event.messaging.TicketEventType;
import ro.axonsoft.services.event.model.Event;
import ro.axonsoft.services.event.model.Seat;
import ro.axonsoft.services.event.model.Section;
import ro.axonsoft.services.event.model.Ticket;
import ro.axonsoft.services.event.model.TicketStatus;
import ro.axonsoft.services.event.model.Venue;
import ro.axonsoft.services.event.repository.TicketRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredHoldReleaserTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private ApplicationEventPublisher events;

    private ExpiredHoldReleaser newReleaser() {
        return new ExpiredHoldReleaser(ticketRepository, events);
    }

    private Ticket anExpiredHold() {
        Venue venue = new Venue("National Arena", "123 Main St", 1200);
        venue.setId(7L);
        Event event = new Event(venue, "Rock Fest", "Romania", "Cluj-Napoca",
                OffsetDateTime.now().plusDays(30));
        event.setId(1L);

        Section section = new Section();
        section.setId(3L);
        Seat seat = new Seat(section, 5, 11);
        seat.setId(42L);

        Ticket ticket = new Ticket(event, seat, 15000);
        ticket.setId(100L);
        ticket.setStatus(TicketStatus.HELD);
        ticket.setHeldBy(9L);
        ticket.setHeldUntil(OffsetDateTime.now().minusSeconds(1));
        return ticket;
    }

    @Test
    void releasesTheSeatAndAnnouncesIt() {
        Ticket ticket = anExpiredHold();
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 42L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.releaseIfStillExpired(anyLong(), any())).thenReturn(1);

        boolean released = newReleaser().release(1L, 42L);

        assertThat(released).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.AVAILABLE);
        assertThat(ticket.getHeldBy()).isNull();
        assertThat(ticket.getHeldUntil()).isNull();

        ArgumentCaptor<TicketEventMessage> published = ArgumentCaptor.forClass(TicketEventMessage.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue().type()).isEqualTo(TicketEventType.SEAT_RELEASED);
        assertThat(published.getValue().seatId()).isEqualTo(42L);
        assertThat(published.getValue().holderId()).isEqualTo(9L);
    }

    @Test
    void staysSilentWhenSomeoneElseAlreadyTookTheSeat() {
        Ticket ticket = anExpiredHold();
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 42L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.releaseIfStillExpired(anyLong(), any())).thenReturn(0);

        boolean released = newReleaser().release(1L, 42L);

        assertThat(released).isFalse();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.HELD);
        verify(events, never()).publishEvent(any(TicketEventMessage.class));
    }

    @Test
    void ignoresAKeyWithoutATicket() {
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 42L)).thenReturn(Optional.empty());

        assertThat(newReleaser().release(1L, 42L)).isFalse();

        verify(events, never()).publishEvent(any(TicketEventMessage.class));
    }
}
