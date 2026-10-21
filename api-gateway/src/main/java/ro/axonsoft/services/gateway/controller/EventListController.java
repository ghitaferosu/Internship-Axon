package ro.axonsoft.services.gateway.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ro.axonsoft.services.gateway.client.EventClient;
import ro.axonsoft.services.gateway.dto.event_dto.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class EventListController {

    private static final String ORGANIZER_ROLE = "ROLE_ORGANIZER";

    private final EventClient eventClient;

    @GetMapping("/events")
    public PageResponse<EventSummaryResponse> searchEvents(@RequestParam(required = false) String country,
                                                           @RequestParam(required = false) String city,
                                                           @RequestParam(required = false) String dateFrom,
                                                           @RequestParam(required = false) String dateTo,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return eventClient.searchEvents(country, city, dateFrom, dateTo, keyword, page, size, isOrganizer());
    }

    @GetMapping("/venues/{venueId}/layout")
    public VenueLayoutResponse getVenueLayout(@PathVariable Long venueId) {
        return eventClient.getVenueLayout(venueId);
    }

    @GetMapping("/sections/{sectionId}/seats")
    public PageResponse<SeatWithStatusResponse> getSectionSeats(@PathVariable Long sectionId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "200") int size,
                                                                @RequestParam(required = false) Long eventId) {
        return eventClient.getSeatsBySection(sectionId, page, size, eventId);
    }

    @GetMapping("/events/{eventId}")
    public EventWithSeatMapResponse getEvent(@PathVariable Long eventId) {
        return eventClient.getEvent(eventId, isOrganizer());
    }

    private boolean isOrganizer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ORGANIZER_ROLE::equals);
    }

    @GetMapping("/venues")
    public List<VenueSummaryResponse> listVenues() {
        return eventClient.listVenues();
    }

    @GetMapping("/performers")
    public List<PerformerResponse> listPerformers() {
        return eventClient.listPerformers();
    }

}
