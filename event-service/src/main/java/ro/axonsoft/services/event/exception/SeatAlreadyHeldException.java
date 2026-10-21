package ro.axonsoft.services.event.exception;

public class SeatAlreadyHeldException extends RuntimeException {
    public SeatAlreadyHeldException(Long seatId) {
        super("Locul " + seatId + " este deja rezervat de altcineva");
    }
}
