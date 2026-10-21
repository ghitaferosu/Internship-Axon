package ro.axonsoft.services.event.exception;

public class HoldExpiredException extends RuntimeException {
    public HoldExpiredException(Long seatId) {
        super("Rezervarea pentru locul " + seatId + " a expirat");
    }
}