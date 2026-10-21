package ro.axonsoft.services.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.axonsoft.services.gateway.client.BookingClient;
import ro.axonsoft.services.gateway.dto.booking.PaymentRequest;
import ro.axonsoft.services.gateway.dto.booking.PaymentResponse;
import ro.axonsoft.services.gateway.dto.booking.TicketResponse;


@RestController
@RequestMapping("/api/events/{eventId}/seats/{seatId}")
@RequiredArgsConstructor
public class BookingController {

    private final BookingClient bookingClient;

    @PostMapping("/hold")
    public TicketResponse hold(@PathVariable Long eventId,
                               @PathVariable Long seatId) {
        return bookingClient.hold(eventId, seatId);
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@PathVariable Long eventId,
                                        @PathVariable Long seatId) {
        bookingClient.release(eventId, seatId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pay")
    public PaymentResponse pay(@PathVariable Long eventId,
                               @PathVariable Long seatId,
                               @RequestBody PaymentRequest request) {
        return bookingClient.pay(eventId, seatId, request);
    }
}
