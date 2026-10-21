package ro.axonsoft.services.user.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ro.axonsoft.services.user.dto.ApiError;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicatedEmail.class)
    public ResponseEntity<ApiError> handleTakenEmail(DuplicatedEmail error) {
        return ResponseEntity.status(409).body(ApiError.rejected(error.getMessage()));
    }

    @ExceptionHandler(MissingRole.class)
    public ResponseEntity<ApiError> handleMissingRole(MissingRole exception) {
        return ResponseEntity
                .status(500)
                .body(ApiError.rejected(exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleEmailRaceCondition() {
        return ResponseEntity.status(409).body(ApiError.rejected("Cererea nu a putut fi finalizată"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validare eșuată");

        return ResponseEntity
                .badRequest()
                .body(ApiError.rejected(message));
    }

    @ExceptionHandler(AccountNotFound.class)
    public ResponseEntity<ApiError> handleAccountNotFound(AccountNotFound exception) {
        return ResponseEntity.status(401).body(ApiError.rejected(exception.getMessage()));
    }

    @ExceptionHandler(WrongPassword.class)
    public ResponseEntity<ApiError> handleWrongPassword(WrongPassword exception) {
        return ResponseEntity.status(401).body(ApiError.rejected(exception.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshToken.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshToken exception) {
        log.warn("Refresh failed: {}", exception.getMessage());
        return ResponseEntity.status(401).body(ApiError.rejected("Token de reîmprospătare invalid"));
    }
}
