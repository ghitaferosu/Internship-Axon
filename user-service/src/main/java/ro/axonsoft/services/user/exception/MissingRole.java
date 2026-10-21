package ro.axonsoft.services.user.exception;

public class MissingRole extends RuntimeException {
    public MissingRole(String role) {
        super("Rolul " + role + " nu a fost găsit");
    }
}
