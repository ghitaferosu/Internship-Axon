package ro.axonsoft.services.event.feedingJob;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.axonsoft.services.event.feedingJob.DTOs.PerformerSeedingResponseDTO;
import ro.axonsoft.services.event.repository.PerformerRepository;

import java.util.List;

// Read-side counterpart to DataFeedingPerformersController's write-only
// POST /admin/performers - previously there was no way to see which
// performers already exist, so an event's performer appearances could only
// reference an id you'd copied down yourself right after creating it.
@RestController
public class PerformerController {

    private final PerformerRepository performerRepository;

    public PerformerController(PerformerRepository performerRepository) {
        this.performerRepository = performerRepository;
    }

    @GetMapping("/performers")
    public List<PerformerSeedingResponseDTO> listPerformers() {
        return performerRepository.findAllByOrderByIdDesc().stream()
                .map(p -> new PerformerSeedingResponseDTO(p.getId(), p.getName(), p.getDescription(), p.getGenre()))
                .toList();
    }
}
