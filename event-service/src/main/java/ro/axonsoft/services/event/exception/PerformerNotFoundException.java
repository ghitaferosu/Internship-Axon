package ro.axonsoft.services.event.exception;

public class PerformerNotFoundException extends RuntimeException {
    public PerformerNotFoundException(Long performerId) {
        super("Artistul nu a fost găsit: " + performerId);
    }
}
