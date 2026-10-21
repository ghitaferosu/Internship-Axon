package ro.axonsoft.services.event.feedingJob;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.axonsoft.services.event.feedingJob.DTOs.PerformerSeedingRequestDTO;
import ro.axonsoft.services.event.feedingJob.DTOs.PerformerSeedingResponseDTO;

import java.util.List;

@RestController
public class DataFeedingPerformersController {

    private final PerformerSeedingService performerSeedingService;

    public DataFeedingPerformersController(PerformerSeedingService performerSeedingService) {
        this.performerSeedingService = performerSeedingService;
    }

    @PostMapping("/admin/performers")
    public ResponseEntity<List<PerformerSeedingResponseDTO>> seedPerformers(@RequestBody List<PerformerSeedingRequestDTO> requests) {
        return ResponseEntity.status(HttpStatus.CREATED).body(performerSeedingService.seedPerformers(requests));
    }
}
