package ro.axonsoft.services.event.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.axonsoft.services.event.model.Seat;


import java.util.List;

public interface SeatRepository extends JpaRepository<Seat,Long> {
    List<Seat> findBySectionId(Long sectionId);
    long countBySectionId(Long sectionId);

    Page<Seat> findBySectionIdOrderByRowAscSeatNumberAsc(Long sectionId, Pageable pageable);
}
