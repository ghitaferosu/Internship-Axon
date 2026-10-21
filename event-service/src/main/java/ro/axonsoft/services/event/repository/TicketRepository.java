package ro.axonsoft.services.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ro.axonsoft.services.event.model.Ticket;
import ro.axonsoft.services.event.ticket.DTO.SeatStatusRow;
import ro.axonsoft.services.event.ticket.DTO.TicketHistory;

import java.time.OffsetDateTime;
import java.util.List;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByEvent_IdAndSeat_Id(Long eventId, Long seatId);

    @Modifying
    @Query(value = """
            INSERT INTO tickets (event_id, seat_id, status, price_cents, created_at, updated_at)
            SELECT :eventId, s.id, 'AVAILABLE', :priceCents, now(), now()
            FROM seats s
            JOIN sections sec ON s.section_id = sec.id
            WHERE sec.venue_id = :venueId
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void materializeTickets(@Param("eventId") Long eventId, @Param("venueId") Long venueId, @Param("priceCents") int priceCents);


    @Query("""
        SELECT t.seat.id as seatId, t.status as status, t.heldUntil as heldUntil
        FROM Ticket t
        WHERE t.event.id = :eventId and t.seat.id in :seatIds""")
    List<SeatStatusRow> findStatuses(@Param("eventId") Long eventId, @Param("seatIds") List<Long> seatIds);



    @Query("""
        SELECT t
        FROM Ticket t JOIN FETCH t.seat s JOIN FETCH s.section
        WHERE (t.ownerId = :userId OR (t.heldBy = :userId AND t.heldUntil > CURRENT_TIMESTAMP)) AND (:eventId IS NULL or t.event.id = :eventId)""")
    List<Ticket> findMyTickets(@Param("userId") Long userId, @Param("eventId") Long eventId);

    @Query("SELECT t FROM Ticket t WHERE t.status = ro.axonsoft.services.event.model.TicketStatus.HELD "
            + "AND t.heldUntil IS NOT NULL AND t.heldUntil < :now")
    List<Ticket> findExpiredHolds(@Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE Ticket t SET t.status = ro.axonsoft.services.event.model.TicketStatus.AVAILABLE, "
            + "t.heldBy = NULL, t.heldUntil = NULL, t.updatedAt = :now "
            + "WHERE t.id = :ticketId AND t.status = ro.axonsoft.services.event.model.TicketStatus.HELD "
            + "AND t.heldUntil < :now")
    int releaseIfStillExpired(@Param("ticketId") Long ticketId, @Param("now") OffsetDateTime now);

    @Query("""
        SELECT t.id as ticketId, e.id as eventId, e.name as eventName, e.eventStartHour as eventStartHour, e.city as city, v.name as venueName,
               sec.sectionName as sectionName, s.row as row, s.seatNumber as seatNumber, t.status as status, t.priceCents as priceCents, t.heldUntil as heldUntil
        FROM Ticket t JOIN t.seat s JOIN s.section sec JOIN t.event e JOIN e.venue v
        WHERE t.ownerId = :userId OR (t.heldBy = :userId AND t.heldUntil > CURRENT_TIMESTAMP)
        ORDER BY e.eventStartHour DESC""")
    List<TicketHistory> findMyTicketHistory(@Param("userId") Long userId);
}

