package ro.axonsoft.services.event.feedingJob.DTOs;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import ro.axonsoft.services.event.feedingJob.PerformerAppearanceSeedingRequestDTO;

import java.time.OffsetDateTime;
import java.util.List;

public record EventSeedingRequestDTO(
        Long venueId,
        String name,
        String country,
        String city,
        OffsetDateTime eventStartHour,
        @NotNull @PositiveOrZero Integer basePriceCents,
        List<PerformerAppearanceSeedingRequestDTO> performers
) {
}
