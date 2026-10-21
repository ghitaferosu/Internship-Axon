package ro.axonsoft.services.event.feedingJob.DTOs;

import java.util.List;

public record VenueSeedingResponseDTO(
        Long id,
        String name,
        String address,
        int noSeats,
        List<SectionSeedingResponseDTO> sections
) {}
