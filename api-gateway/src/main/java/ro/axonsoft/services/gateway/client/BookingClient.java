package ro.axonsoft.services.gateway.client;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ro.axonsoft.services.gateway.dto.booking.PaymentRequest;
import ro.axonsoft.services.gateway.dto.booking.PaymentResponse;
import ro.axonsoft.services.gateway.dto.booking.TicketResponse;


@FeignClient(name = "event-service-booking", url = "${services.event-service.url}")
public interface BookingClient {

    @PostMapping("/events/{eventId}/seats/{seatId}/hold")
    TicketResponse hold(@PathVariable Long eventId, @PathVariable Long seatId);

    @PostMapping("/events/{eventId}/seats/{seatId}/release")
    void release(@PathVariable Long eventId, @PathVariable Long seatId);

    @PostMapping("/events/{eventId}/seats/{seatId}/pay")
    PaymentResponse pay(@PathVariable Long eventId, @PathVariable Long seatId,
                        @RequestBody PaymentRequest request);
}
