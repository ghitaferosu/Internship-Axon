// controller/SeatHoldController.java
package ro.axonsoft.services.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.axonsoft.services.gateway.client.EventClient;
import ro.axonsoft.services.gateway.dto.event_dto.HoldRequest;
import ro.axonsoft.services.gateway.dto.event_dto.TicketResponse;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/events/{eventId}/seats/{seatId}")
public class SeatHoldController {

    private final EventClient eventClient;

    @PostMapping("/confirm")
    public TicketResponse confirm(@PathVariable Long eventId, @PathVariable Long seatId, @RequestBody HoldRequest request) {
        return eventClient.confirm(eventId, seatId, request);
    }
}