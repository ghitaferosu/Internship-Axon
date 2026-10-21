package ro.axonsoft.services.gateway.client;


import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ro.axonsoft.services.gateway.dto.event_dto.*;

import java.util.List;

@FeignClient(name = "event-service", url = "${services.event-service.url}")
public interface EventClient {

    @GetMapping("/venues/{venueId}/layout")
    VenueLayoutResponse getVenueLayout(@PathVariable Long venueId);

    @GetMapping("/sections/{sectionId}/seats")
    PageResponse<SeatWithStatusResponse> getSeatsBySection(@PathVariable Long sectionId,
                                                           @RequestParam int page,
                                                           @RequestParam int size,
                                                           @RequestParam(required = false) Long eventId);

    @GetMapping("/api/events")
    PageResponse<EventSummaryResponse> searchEvents(@RequestParam(required = false) String country,
                                                    @RequestParam(required = false) String city,
                                                    @RequestParam(required = false) String dateFrom,
                                                    @RequestParam(required = false) String dateTo,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam int page,
                                                    @RequestParam int size,
                                                    @RequestParam boolean includeUnpublished);

    @PostMapping("/events/{eventId}/seats/{seatId}/confirm")
    TicketResponse confirm(@PathVariable Long eventId, @PathVariable Long seatId, @RequestBody HoldRequest request);

    @GetMapping("/api/me/tickets")
    List<MyTicketResponse> myTickets(@RequestParam(required = false) Long eventId);

    @GetMapping("/api/events/{eventId}")
    EventWithSeatMapResponse getEvent(@PathVariable Long eventId,
                                      @RequestParam boolean includeUnpublished);

    @GetMapping("/venues")
    List<VenueSummaryResponse> listVenues();

    @GetMapping("/performers")
    List<PerformerResponse> listPerformers();

    @GetMapping("/api/me/tickets/history")
    List<TicketHistoryResponse> myTicketHistory();
}
