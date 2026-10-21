package ro.axonsoft.services.event.exception;

public class SectionNotFoundException extends RuntimeException {
    public SectionNotFoundException(Long sectionId) {
        super("Secțiunea nu a fost găsită: " + sectionId);
    }
}
