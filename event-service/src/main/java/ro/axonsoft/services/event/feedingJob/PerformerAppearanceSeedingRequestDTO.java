package ro.axonsoft.services.event.feedingJob;

import java.time.OffsetDateTime;

public record PerformerAppearanceSeedingRequestDTO(Long performerId, OffsetDateTime startHour, OffsetDateTime endHour) {
}
