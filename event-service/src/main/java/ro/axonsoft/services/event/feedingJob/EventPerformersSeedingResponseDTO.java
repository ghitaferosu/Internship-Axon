package ro.axonsoft.services.event.feedingJob;

import ro.axonsoft.services.event.model.EventStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record EventPerformersSeedingResponseDTO
        (Long id,
         Long venueId,
         String name,
         String country,
         String city,
         OffsetDateTime eventStartHour,
         EventStatus status,
         List<PerformerAppearanceSeedingResponseDTO> performers
)
{}
