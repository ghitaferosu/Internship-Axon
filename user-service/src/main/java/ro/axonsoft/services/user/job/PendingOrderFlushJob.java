package ro.axonsoft.services.user.job;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.axonsoft.services.user.notification.TicketPurchaseEmailService;

@Component
@RequiredArgsConstructor
public class PendingOrderFlushJob {

    private final TicketPurchaseEmailService ticketPurchaseEmailService;

    @Scheduled(fixedDelay = 30_000)
    public void flushStaleOrders() {
        ticketPurchaseEmailService.flushStaleOrders();
    }
}
