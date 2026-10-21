package ro.axonsoft.services.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.axonsoft.services.event.model.Section;

import java.util.List;

public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByVenueId(Long venueId);
}
