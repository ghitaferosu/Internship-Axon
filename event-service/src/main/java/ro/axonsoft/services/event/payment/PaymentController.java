package ro.axonsoft.services.event.payment;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.axonsoft.services.event.payment.DTOs.PaymentRequestDTO;
import ro.axonsoft.services.event.payment.DTOs.PaymentResponseDTO;

@RestController
@RequestMapping("/events/{eventId}/seats/{seatId}")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/pay")
    public ResponseEntity<PaymentResponseDTO> pay(
            @PathVariable Long eventId, @PathVariable Long seatId,
            @RequestHeader ("X-User-Id") Long userId,
            @Valid @RequestBody PaymentRequestDTO request) {
        return ResponseEntity.ok(paymentService.pay(eventId, seatId, userId, request));
    }
}