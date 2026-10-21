package ro.axonsoft.services.event.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({VenueNotFoundException.class, SectionNotFoundException.class, PerformerNotFoundException.class, InvalidPerformerAppearanceException.class})
    public ResponseEntity<String> handleSeedingExceptions(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler({TicketNotFoundException.class, EventNotFoundException.class})
    public ResponseEntity<String> handleTicketNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler({SeatAlreadyHeldException.class, HoldNotOwnedException.class, HoldExpiredException.class, IdempotencyKeyReusedException.class, EventNotOnSaleException.class})
    public ResponseEntity<String> handleHoldConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validare eșuată");
        return ResponseEntity.badRequest().body(message);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<String> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .orElse("Validare eșuată");
        return ResponseEntity.badRequest().body(message);
    }

    @ExceptionHandler(PaymentAmountMismatchException.class)
    public ResponseEntity<String> handlePaymentAmount(PaymentAmountMismatchException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Eroare internă");
    }
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<String> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest().body("Header lipsă: "+ex.getHeaderName());
    }
}
