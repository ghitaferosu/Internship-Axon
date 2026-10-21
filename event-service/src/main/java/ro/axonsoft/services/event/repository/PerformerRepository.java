package ro.axonsoft.services.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.axonsoft.services.event.model.Performer;

import java.util.List;

public interface PerformerRepository extends JpaRepository<Performer,Long> {
    List<Performer> findByNameContainingIgnoreCase(String name);

    // Newest-first: the picker's main use case is "find the performer I just
    // created", not alphabetical browsing.
    List<Performer> findAllByOrderByIdDesc();
}
