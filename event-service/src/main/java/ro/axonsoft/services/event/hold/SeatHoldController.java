package ro.axonsoft.services.event.hold;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.axonsoft.services.event.hold.DTOs.TicketResponseDTO;

@RestController
@RequestMapping("/events/{eventId}/seats/{seatId}")
public class SeatHoldController {

    private final SeatHoldService seatHoldService;

    public SeatHoldController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    @PostMapping("/hold")
    public ResponseEntity<TicketResponseDTO> hold(
            @PathVariable Long eventId, @PathVariable Long seatId, @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(TicketResponseDTO.from(seatHoldService.hold(eventId, seatId, userId)));
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(
            @PathVariable Long eventId, @PathVariable Long seatId, @RequestHeader("X-User-Id") Long userId) {
        seatHoldService.release(eventId, seatId, userId);
        return ResponseEntity.noContent().build();
    }
}
