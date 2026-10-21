package ro.axonsoft.services.event.exception;

public class PaymentAmountMismatchException  extends RuntimeException {
    public PaymentAmountMismatchException(int expected, int received) {
        super("Suma așteptată era " + expected + " bani, dar s-a primit " + received);
    }
}