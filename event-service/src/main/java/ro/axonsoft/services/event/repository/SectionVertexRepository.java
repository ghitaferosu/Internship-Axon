package ro.axonsoft.services.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.axonsoft.services.event.model.SectionVertex;

import java.util.List;

public interface SectionVertexRepository extends JpaRepository<SectionVertex, Long> {
    List<SectionVertex> findBySectionIdOrderByVertexOrderAsc(Long sectionId);
}
