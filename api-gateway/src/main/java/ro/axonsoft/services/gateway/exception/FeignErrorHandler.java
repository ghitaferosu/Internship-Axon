package ro.axonsoft.services.gateway.exception;


import feign.FeignException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class FeignErrorHandler {

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<String> handleFeign(FeignException exception) {
        int status = exception.status();

        if (status <= 0) {
            return ResponseEntity.status(502).contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"BAD_GATEWAY\",\"message\":\"Serviciul nu este disponibil\"}");
        }

        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(exception.contentUTF8());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("Validare eșuată");
        return ResponseEntity.badRequest().body(Map.of("status", "REJECTED", "message", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMalformedJson(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("status", "REJECTED", "message", "Corpul cererii este malformat"));
    }
}
