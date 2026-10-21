package ro.axonsoft.services.event.exception;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(Long eventId, Long seatId) {
        super("Nu există bilet pentru evenimentul " + eventId + " și locul " + seatId);
    }
}
