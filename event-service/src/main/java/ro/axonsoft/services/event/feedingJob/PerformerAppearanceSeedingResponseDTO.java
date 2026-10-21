package ro.axonsoft.services.event.feedingJob;

import java.time.OffsetDateTime;

public record PerformerAppearanceSeedingResponseDTO(
        Long id,            // the events_performer row's own ID
        Long performerId,
        String performerName,   // see note below
        OffsetDateTime startHour,
        OffsetDateTime endHour
)
{}
