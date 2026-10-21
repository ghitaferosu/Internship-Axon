package ro.axonsoft.services.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.axonsoft.services.user.model.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
