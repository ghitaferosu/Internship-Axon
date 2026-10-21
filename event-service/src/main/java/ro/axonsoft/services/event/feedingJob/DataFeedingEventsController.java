package ro.axonsoft.services.event.feedingJob;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.axonsoft.services.event.feedingJob.DTOs.EventSeedingRequestDTO;

import java.util.List;

@RestController
public class DataFeedingEventsController {

    private final EventSeedingService eventSeedingService;

    public DataFeedingEventsController(EventSeedingService eventSeedingService) {
        this.eventSeedingService = eventSeedingService;
    }

    @PostMapping("/admin/events")
    public ResponseEntity<List<EventPerformersSeedingResponseDTO>> seedEvents(@Valid @RequestBody List<EventSeedingRequestDTO> requests) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventSeedingService.seedEvents(requests));
    }
}
