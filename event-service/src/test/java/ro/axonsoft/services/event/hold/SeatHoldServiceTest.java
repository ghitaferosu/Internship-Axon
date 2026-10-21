package ro.axonsoft.services.event.hold;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ro.axonsoft.services.event.exception.EventNotFoundException;
import ro.axonsoft.services.event.exception.EventNotOnSaleException;
import ro.axonsoft.services.event.exception.HoldNotOwnedException;
import ro.axonsoft.services.event.exception.SeatAlreadyHeldException;
import ro.axonsoft.services.event.exception.TicketNotFoundException;
import ro.axonsoft.services.event.messaging.TicketEventMessage;
import ro.axonsoft.services.event.messaging.TicketEventType;
import ro.axonsoft.services.event.model.Event;
import ro.axonsoft.services.event.model.EventStatus;
import ro.axonsoft.services.event.model.Seat;
import ro.axonsoft.services.event.model.Section;
import ro.axonsoft.services.event.model.Ticket;
import ro.axonsoft.services.event.model.TicketStatus;
import ro.axonsoft.services.event.model.Venue;
import ro.axonsoft.services.event.repository.EventRepository;
import ro.axonsoft.services.event.repository.TicketRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {

    @Mock
    private SeatLockService seatLockService;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ApplicationEventPublisher events;

    private SeatHoldService newService() {
        return new SeatHoldService(seatLockService, ticketRepository, eventRepository, events);
    }

    private Event onSaleEvent() {
        Event event = new Event(new Venue("National Arena", "123 Main St", 1200),
                "Rock Fest", "Romania", "Cluj-Napoca", OffsetDateTime.now());
        event.setStatus(EventStatus.LIVE);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        return event;
    }

    private Ticket availableTicket() {
        Venue venue = new Venue("National Arena", "123 Main St", 1200);
        Section section = new Section(venue, "A");
        Seat seat = new Seat(section, 1, 1);
        Event event = new Event(venue, "Rock Fest", "Romania", "Cluj-Napoca", OffsetDateTime.now());
        Ticket ticket = new Ticket(event, seat, 5000);
        ticket.setId(99L);
        return ticket;
    }

    @Test
    void holdAcquiresLockAndMarksTicketHeld() {
        onSaleEvent();
        Ticket ticket = availableTicket();
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.of(ticket));
        when(seatLockService.tryAcquire(eq(1L), eq(10L), eq("42"), any(Duration.class))).thenReturn(true);
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        Ticket result = newService().hold(1L, 10L, 42L);

        assertThat(result.getStatus()).isEqualTo(TicketStatus.HELD);
        assertThat(result.getHeldBy()).isEqualTo(42L);
        assertThat(result.getHeldUntil()).isNotNull();
    }

    @Test
    void holdThrowsWhenLockIsAlreadyTaken() {
        onSaleEvent();
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.of(availableTicket()));
        when(seatLockService.tryAcquire(eq(1L), eq(10L), eq("42"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> newService().hold(1L, 10L, 42L))
                .isInstanceOf(SeatAlreadyHeldException.class);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void holdReleasesTheLockAndThrowsWhenTicketIsAlreadySold() {
        onSaleEvent();
        Ticket ticket = availableTicket();
        ticket.setStatus(TicketStatus.SOLD);
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.of(ticket));
        when(seatLockService.tryAcquire(eq(1L), eq(10L), eq("42"), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> newService().hold(1L, 10L, 42L))
                .isInstanceOf(SeatAlreadyHeldException.class);

        verify(seatLockService).release(1L, 10L, "42");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void holdThrowsWhenTicketMissing() {
        onSaleEvent();
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().hold(1L, 10L, 42L))
                .isInstanceOf(TicketNotFoundException.class);

        verifyNoInteractions(seatLockService);
    }

    @Test
    void holdThrowsWhenEventIsNotOnSaleYet() {
        Event event = new Event(new Venue("National Arena", "123 Main St", 1200),
                "Rock Fest", "Romania", "Cluj-Napoca", OffsetDateTime.now());
        event.setStatus(EventStatus.SETTING_UP);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> newService().hold(1L, 10L, 42L))
                .isInstanceOf(EventNotOnSaleException.class);

        verifyNoInteractions(seatLockService, ticketRepository);
    }

    @Test
    void holdThrowsWhenEventMissing() {
        when(eventRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().hold(1L, 10L, 42L))
                .isInstanceOf(EventNotFoundException.class);

        verifyNoInteractions(seatLockService, ticketRepository);
    }

    @Test
    void releaseClearsHeldFieldsWhenCallerOwnsTheLock() {
        Ticket ticket = availableTicket();
        ticket.setStatus(TicketStatus.HELD);
        ticket.setHeldBy(42L);
        ticket.setHeldUntil(OffsetDateTime.now().plusMinutes(5));
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.of(ticket));

        when(seatLockService.renew(eq(1L), eq(10L), eq("42"), any(Duration.class))).thenReturn(true);

        newService().release(1L, 10L, 42L);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.AVAILABLE);
        assertThat(ticket.getHeldBy()).isNull();
        assertThat(ticket.getHeldUntil()).isNull();
        verify(ticketRepository).save(ticket);
    }

    @Test
    void releaseThrowsWhenCallerDoesNotOwnTheLock() {
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.of(availableTicket()));
        when(seatLockService.renew(eq(1L), eq(10L), eq("42"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> newService().release(1L, 10L, 42L))
                .isInstanceOf(HoldNotOwnedException.class);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void confirmMarksTicketSoldAndSetsOwner() {
        Ticket ticket = availableTicket();

        ticket.setStatus(TicketStatus.HELD);
        ticket.setHeldBy(42L);
        ticket.setHeldUntil(OffsetDateTime.now().plusMinutes(5));

        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.of(ticket));
        when(seatLockService.renew(eq(1L), eq(10L), eq("42"), any(Duration.class))).thenReturn(true);
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        Ticket result = newService().confirm(1L, 10L, 42L);

        assertThat(result.getStatus()).isEqualTo(TicketStatus.SOLD);
        assertThat(result.getOwnerId()).isEqualTo(42L);
        assertThat(result.getHeldBy()).isNull();
    }

    @Test
    void confirmThrowsWhenCallerDoesNotOwnTheLock() {
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.of(availableTicket()));
        when(seatLockService.renew(eq(1L), eq(10L), eq("42"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> newService().confirm(1L, 10L, 42L))
                .isInstanceOf(HoldNotOwnedException.class);
    }


    @Test
    void holdPublishesSeatHeldEvent() {
        onSaleEvent();
        Ticket ticket = availableTicket();
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.of(ticket));
        when(seatLockService.tryAcquire(eq(1L), eq(10L), eq("42"), any(Duration.class))).thenReturn(true);
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        newService().hold(1L, 10L, 42L);

        ArgumentCaptor<TicketEventMessage> captor = ArgumentCaptor.forClass(TicketEventMessage.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(TicketEventType.SEAT_HELD);
        assertThat(captor.getValue().partitionKey()).isEqualTo("1:10");
    }

    @Test
    void holdPublishesNothingWhenLockIsTaken() {
        onSaleEvent();
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.of(availableTicket()));
        when(seatLockService.tryAcquire(eq(1L), eq(10L), eq("42"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> newService().hold(1L, 10L, 42L))
                .isInstanceOf(SeatAlreadyHeldException.class);

        verifyNoInteractions(events);
    }

    @Test
    void confirmRejectsWhenTicketIsNotHeldByCaller() {
        Ticket ticket = availableTicket();
        ticket.setStatus(TicketStatus.HELD);
        ticket.setHeldBy(99L);                    // alt holder
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.of(ticket));
        when(seatLockService.renew(eq(1L), eq(10L), eq("42"), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> newService().confirm(1L, 10L, 42L))
                .isInstanceOf(HoldNotOwnedException.class);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void confirmReleasesTheLockOnlyAfterCommit() {
        Ticket ticket = availableTicket();
        ticket.setStatus(TicketStatus.HELD);
        ticket.setHeldBy(42L);
        ticket.setHeldUntil(OffsetDateTime.now().plusMinutes(5));
        when(ticketRepository.findByEvent_IdAndSeat_Id(1L, 10L)).thenReturn(Optional.of(ticket));
        when(seatLockService.renew(eq(1L), eq(10L), eq("42"), any(Duration.class))).thenReturn(true);
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        newService().confirm(1L, 10L, 42L);

        verify(seatLockService, never()).release(any(), any(), any());
        verify(events).publishEvent(new SeatLockReleaseRequested(1L, 10L, "42"));
    }
}
