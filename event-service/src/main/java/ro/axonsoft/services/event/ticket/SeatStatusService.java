package ro.axonsoft.services.event.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ro.axonsoft.services.event.model.TicketStatus;
import ro.axonsoft.services.event.ticket.DTO.SeatStatusRow;
import ro.axonsoft.services.event.ticket.DTO.SeatStatusView;
import ro.axonsoft.services.event.repository.TicketRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class SeatStatusService {

    private final TicketRepository ticketRepository;

    public Map<Long, SeatStatusView> statusesFor(Long eventId, List<Long> seatIds) {
        if(eventId == null || seatIds.isEmpty()) {
            return Map.of();
        }
        OffsetDateTime  now = OffsetDateTime.now();
        return ticketRepository.findStatuses(eventId, seatIds).stream().collect(Collectors.toMap(SeatStatusRow::seatId, row -> status(row, now)));
    }

    private SeatStatusView status(SeatStatusRow row, OffsetDateTime now) {
        if (row.status() == TicketStatus.HELD && row.heldUntil() != null && row.heldUntil().isBefore(now)) {
            return new SeatStatusView(TicketStatus.AVAILABLE, null);
        }
        // heldUntil pleaca spre client doar pentru un hold inca valid: pe baza
        // lui browserul isi programeaza singur eliberarea locului la secunda.
        if (row.status() == TicketStatus.HELD) {
            return new SeatStatusView(TicketStatus.HELD, row.heldUntil());
        }
        return new SeatStatusView(row.status(), null);
    }
}
