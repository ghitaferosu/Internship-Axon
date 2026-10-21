package ro.axonsoft.services.event.hold;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;



@Component
public class SeatLockReleaser {

    private final SeatLockService seatLockService;

    public SeatLockReleaser(SeatLockService seatLockService) {
        this.seatLockService = seatLockService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReleaseRequested(SeatLockReleaseRequested request) {
        seatLockService.release(request.eventId(), request.seatId(), request.holderToken());
    }
}
