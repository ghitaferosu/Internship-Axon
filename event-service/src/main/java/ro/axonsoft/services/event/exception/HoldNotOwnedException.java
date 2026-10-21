package ro.axonsoft.services.event.exception;

public class HoldNotOwnedException extends RuntimeException {
    public HoldNotOwnedException(Long seatId) {
        super("Nu ai o rezervare activă pentru locul " + seatId + " (poate a expirat deja)");
    }
}
