package ro.axonsoft.services.event.exception;

public class VenueNotFoundException extends RuntimeException {
    public VenueNotFoundException(Long venueId) {
        super("Locația nu a fost găsită: " + venueId);
    }
}
