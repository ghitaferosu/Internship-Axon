package ro.axonsoft.services.user.dto.kafkaEvents;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID eventId,
        Long userId,
        String email,
        Instant occurredAt
) {
}
