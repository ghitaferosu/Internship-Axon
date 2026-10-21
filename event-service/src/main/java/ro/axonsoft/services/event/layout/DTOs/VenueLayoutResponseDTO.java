package ro.axonsoft.services.event.layout.DTOs;

import java.util.List;

public record VenueLayoutResponseDTO(
        Long id,
        String name,
        String address,
        int noSeats,
        List<SectionLayoutDTO> sections
) {}
