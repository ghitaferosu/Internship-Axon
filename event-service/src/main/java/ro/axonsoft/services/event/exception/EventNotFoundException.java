package ro.axonsoft.services.event.exception;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(Long eventId) {
        super("Evenimentul nu a fost găsit: " + eventId);
    }
}
