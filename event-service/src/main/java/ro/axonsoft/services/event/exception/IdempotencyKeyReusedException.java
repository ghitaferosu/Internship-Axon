package ro.axonsoft.services.event.exception;

public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException(String key) {
        super("Cheia de idempotență '" + key + "' a fost deja folosită pentru o altă plată");
    }
}
