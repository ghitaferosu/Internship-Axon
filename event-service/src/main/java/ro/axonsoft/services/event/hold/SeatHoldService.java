package ro.axonsoft.services.event.hold;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.axonsoft.services.event.exception.EventNotFoundException;
import ro.axonsoft.services.event.exception.EventNotOnSaleException;
import ro.axonsoft.services.event.exception.HoldExpiredException;
import ro.axonsoft.services.event.exception.HoldNotOwnedException;
import ro.axonsoft.services.event.exception.SeatAlreadyHeldException;
import ro.axonsoft.services.event.exception.TicketNotFoundException;
import ro.axonsoft.services.event.messaging.TicketEventMessage;
import ro.axonsoft.services.event.model.Event;
import ro.axonsoft.services.event.model.EventStatus;
import ro.axonsoft.services.event.model.Ticket;
import ro.axonsoft.services.event.model.TicketStatus;
import ro.axonsoft.services.event.repository.EventRepository;
import ro.axonsoft.services.event.repository.TicketRepository;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Orchestrates seat holds: the Redis lock in {@link SeatLockService} is the
 * source of truth for "who owns this seat right now", and the `tickets` row
 * is the queryable projection of that (what a status-reading API would
 * show). Every write here acquires/verifies the lock first, then updates the
 * row - never the other way around.
 */
@Service
public class SeatHoldService {

    // How long a hold lasts before Redis auto-expires it and the seat
    // returns to the pool. A held seat whose Redis key has since expired but
    // whose ticket row still says HELD is stale data - reconciled lazily
    // below the next time someone tries to hold that same seat.
    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    private final SeatLockService seatLockService;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final ApplicationEventPublisher events;

    public SeatHoldService(SeatLockService seatLockService, TicketRepository ticketRepository,
                           EventRepository eventRepository, ApplicationEventPublisher events) {
        this.seatLockService = seatLockService;
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.events = events;
    }

    @Transactional
    public Ticket hold(Long eventId, Long seatId, Long holderId) {
        requireOnSale(eventId);
        Ticket ticket = requireTicket(eventId, seatId);
        String holderToken = holderId.toString();

        if (!seatLockService.tryAcquire(eventId, seatId, holderToken, HOLD_TTL)) {
            throw new SeatAlreadyHeldException(seatId);
        }

        // A SOLD seat should never be re-held even though the lock was free
        // (nothing should be holding a lock for it in the first place, but
        // this is the defense-in-depth check against a bug that would).
        if (ticket.getStatus() == TicketStatus.SOLD) {
            seatLockService.release(eventId, seatId, holderToken);
            throw new SeatAlreadyHeldException(seatId);
        }

        ticket.setStatus(TicketStatus.HELD);
        ticket.setHeldBy(holderId);
        ticket.setHeldUntil(OffsetDateTime.now().plus(HOLD_TTL));
        Ticket saved=ticketRepository.save(ticket);
        events.publishEvent(TicketEventMessage.held(eventId, seatId,saved));
        return saved;
    }

    @Transactional
    public void release(Long eventId, Long seatId, Long holderId) {
        Ticket ticket = requireTicket(eventId, seatId);
        String holderToken = holderId.toString();

        if (!seatLockService.renew(eventId, seatId, holderToken, HOLD_TTL)) {
            throw new HoldNotOwnedException(seatId);
        }

        if (ticket.getStatus() == TicketStatus.HELD && holderId.equals(ticket.getHeldBy())) {
            ticket.setStatus(TicketStatus.AVAILABLE);
            ticket.setHeldBy(null);
            ticket.setHeldUntil(null);
            ticketRepository.save(ticket);
            events.publishEvent(TicketEventMessage.released(eventId,seatId,ticket,holderId));
        }
        events.publishEvent(new SeatLockReleaseRequested(eventId, seatId, holderToken));
    }

    @Transactional
    public Ticket confirm(Long eventId, Long seatId, Long holderId) {
        Ticket ticket = requireTicket(eventId, seatId);
        String holderToken = holderId.toString();

        // Confirming consumes the hold - release rather than renew, a SOLD
        // seat has no further need of one.
        if (!seatLockService.renew(eventId, seatId, holderToken, HOLD_TTL)) {
            throw new HoldNotOwnedException(seatId);
        }

        if (ticket.getStatus() != TicketStatus.HELD || !holderId.equals(ticket.getHeldBy())) {
            throw new HoldNotOwnedException(seatId);
        }

        if (ticket.getHeldUntil() == null || ticket.getHeldUntil().isBefore(OffsetDateTime.now())) {
            throw new HoldExpiredException(seatId);
        }

        ticket.setStatus(TicketStatus.SOLD);
        ticket.setOwnerId(holderId);
        ticket.setHeldBy(null);
        ticket.setHeldUntil(null);
        Ticket saved = ticketRepository.save(ticket);

        events.publishEvent(TicketEventMessage.sold(eventId, seatId, saved));
        events.publishEvent(new SeatLockReleaseRequested(eventId, seatId, holderToken));
        return saved;
    }

    private void requireOnSale(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        if (event.getStatus() != EventStatus.LIVE) {
            throw new EventNotOnSaleException(eventId, event.getStatus());
        }
    }

    private Ticket requireTicket(Long eventId, Long seatId) {
        return ticketRepository.findByEvent_IdAndSeat_Id(eventId, seatId)
                .orElseThrow(() -> new TicketNotFoundException(eventId, seatId));
    }
}
