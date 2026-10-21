package ro.axonsoft.services.event.feedingJob;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.axonsoft.services.event.feedingJob.DTOs.PerformerSeedingRequestDTO;
import ro.axonsoft.services.event.feedingJob.DTOs.PerformerSeedingResponseDTO;
import ro.axonsoft.services.event.model.Performer;
import ro.axonsoft.services.event.repository.PerformerRepository;

import java.util.List;

@Service
public class PerformerSeedingService {

    private final PerformerRepository performerRepository;

    public PerformerSeedingService(PerformerRepository performerRepository) {
        this.performerRepository = performerRepository;
    }

    @Transactional
    public List<PerformerSeedingResponseDTO> seedPerformers(List<PerformerSeedingRequestDTO> requests) {
        List<Performer> performers = requests.stream()
                .map(request -> new Performer(request.name(), request.genre(), request.description()))
                .toList();

        return performerRepository.saveAll(performers).stream()
                .map(performer -> new PerformerSeedingResponseDTO(
                        performer.getId(), performer.getName(), performer.getDescription(), performer.getGenre()))
                .toList();
    }
}
