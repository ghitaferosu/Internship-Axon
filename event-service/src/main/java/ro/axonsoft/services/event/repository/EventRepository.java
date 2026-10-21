package ro.axonsoft.services.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ro.axonsoft.services.event.model.Event;
import ro.axonsoft.services.event.model.EventStatus;
import ro.axonsoft.services.event.model.Seat;

import java.time.OffsetDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event,Long>, JpaSpecificationExecutor<Event>,EventFuzzySearchRepository {

}
