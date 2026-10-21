package ro.axonsoft.services.event.feedingJob;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.axonsoft.services.event.feedingJob.DTOs.VenueDrawRequestDTO;
import ro.axonsoft.services.event.feedingJob.DTOs.VenueSeedingResponseDTO;

// Rebuilt against the drawn-polygon request contract (see
// venue-authoring-rework in project memory): same URL as the old DSL-script
// endpoint, new body - VenueEditor.jsx's "Save venue" payload instead of
// VenueScriptRequestDTO text.
@RestController
public class DataFeedingVenuesController {

    private final VenueSeedingService venueSeedingService;

    public DataFeedingVenuesController(VenueSeedingService venueSeedingService) {
        this.venueSeedingService = venueSeedingService;
    }

    @PostMapping("/admin/venues")
    public ResponseEntity<VenueSeedingResponseDTO> seedVenue(@Valid @RequestBody VenueDrawRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueSeedingService.seedVenue(request));
    }
}
