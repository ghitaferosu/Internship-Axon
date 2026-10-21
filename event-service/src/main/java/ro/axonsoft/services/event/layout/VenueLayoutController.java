package ro.axonsoft.services.event.layout;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ro.axonsoft.services.event.layout.DTOs.VenueLayoutResponseDTO;
import ro.axonsoft.services.event.layout.DTOs.VenueSummaryDTO;

import java.util.List;

@RestController
public class VenueLayoutController {

    private final VenueLayoutService venueLayoutService;

    public VenueLayoutController(VenueLayoutService venueLayoutService) {
        this.venueLayoutService = venueLayoutService;
    }

    // List endpoint: every venue, summary-only - the picker DataFeedingEventsController's
    // callers need for an event's venueId (previously only a by-id lookup existed).
    @GetMapping("/venues")
    public List<VenueSummaryDTO> listVenues() {
        return venueLayoutService.listVenues();
    }

    @GetMapping("/venues/{venueId}/layout")
    public VenueLayoutResponseDTO getLayout(@PathVariable Long venueId) {
        return venueLayoutService.getLayout(venueId);
    }
}
