package ro.axonsoft.services.event.feedingJob.DTOs;

import java.util.List;

// noSeats is deliberately absent here (unlike VenueSeedingResponseDTO): the
// frontend has no live seat-count preview while drawing (deferred, see
// venue-authoring-rework in project memory), so total capacity can only be
// known after SectionGeometryCalculator has run - it's computed inside
// seedVenue() from the actual generated grid, not supplied by the caller.
public record VenueSeedingRequestDTO(
        String name,
        String address,
        List<SectionSeedingRequestDTO> sections
) {}
