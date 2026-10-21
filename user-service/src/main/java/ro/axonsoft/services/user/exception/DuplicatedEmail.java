package ro.axonsoft.services.user.exception;

public class DuplicatedEmail extends RuntimeException {
    public DuplicatedEmail(String message) {
        super(message);
    }
}
