package ro.axonsoft.services.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ro.axonsoft.services.event.eventSearch.EventPerformerName;
import ro.axonsoft.services.event.model.EventPerformer;
import java.util.List;

public interface EventPerformerRepository extends JpaRepository<EventPerformer,Long> {
    List<EventPerformer> findByEventId(Long eventId);
    List<EventPerformer> findByPerformerId(Long performerId);


    @Query("""
            SELECT ep.event.id as eventId, p.name as performerName, ep.startHour, ep.endHour
            FROM EventPerformer ep JOIN ep.performer p
            WHERE ep.event.id IN :eventIds
            ORDER BY ep.startHour""")
    List<EventPerformerName> findPerformerNames(@Param("eventIds") List<Long> eventIds);

}
