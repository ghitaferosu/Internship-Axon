package ro.axonsoft.services.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.axonsoft.services.event.model.Venue;

import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue,Long> {
    Optional<Venue> findByNameAndAddress(String name,String address);

    // Newest-first: the picker's main use case is "find the venue I just
    // created", not alphabetical browsing.
    List<Venue> findAllByOrderByIdDesc();
}
