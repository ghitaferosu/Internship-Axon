package ro.axonsoft.services.event.hold;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.axonsoft.services.event.messaging.TicketEventMessage;
import ro.axonsoft.services.event.model.Ticket;
import ro.axonsoft.services.event.model.TicketStatus;
import ro.axonsoft.services.event.repository.TicketRepository;

import java.time.OffsetDateTime;

@Service
public class ExpiredHoldReleaser {

    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher events;

    public ExpiredHoldReleaser(TicketRepository ticketRepository, ApplicationEventPublisher events) {
        this.ticketRepository = ticketRepository;
        this.events = events;
    }

    @Transactional
    public boolean release(Long eventId, Long seatId) {
        return ticketRepository.findByEvent_IdAndSeat_Id(eventId, seatId)
                .map(this::release)
                .orElse(false);
    }

    @Transactional
    public boolean release(Ticket ticket) {
        OffsetDateTime now = OffsetDateTime.now();
        Long eventId = ticket.getEvent().getId();
        Long seatId = ticket.getSeat().getId();
        Long previousHolder = ticket.getHeldBy();

        if (ticketRepository.releaseIfStillExpired(ticket.getId(), now) != 1) {
            return false;
        }

        ticket.setStatus(TicketStatus.AVAILABLE);
        ticket.setHeldBy(null);
        ticket.setHeldUntil(null);
        events.publishEvent(TicketEventMessage.released(eventId, seatId, ticket, previousHolder));
        return true;
    }
}
