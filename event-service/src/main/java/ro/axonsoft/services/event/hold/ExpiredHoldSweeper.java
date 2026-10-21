package ro.axonsoft.services.event.hold;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.axonsoft.services.event.model.Ticket;
import ro.axonsoft.services.event.repository.TicketRepository;

import java.time.OffsetDateTime;

@Component
public class ExpiredHoldSweeper {
    private final TicketRepository ticketRepository;
    private final ExpiredHoldReleaser expiredHoldReleaser;

    public ExpiredHoldSweeper(TicketRepository ticketRepository, ExpiredHoldReleaser expiredHoldReleaser) {
        this.ticketRepository = ticketRepository;
        this.expiredHoldReleaser = expiredHoldReleaser;
    }

    @Scheduled(fixedRate = 5_000)
    @Transactional
    public void releaseExpiredHolds() {
        for (Ticket ticket : ticketRepository.findExpiredHolds(OffsetDateTime.now())) {
            expiredHoldReleaser.release(ticket);
        }
    }
}
