package ro.axonsoft.services.event.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ro.axonsoft.services.event.model.Ticket;
import ro.axonsoft.services.event.repository.TicketRepository;
import ro.axonsoft.services.event.ticket.DTO.MyTicket;
import ro.axonsoft.services.event.ticket.DTO.TicketHistory;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;

    private MyTicket convert(Ticket ticket) {
        return new MyTicket(ticket.getId(), ticket.getEvent().getId(), ticket.getSeat().getId(), ticket.getSeat().getSection().getId(),
                ticket.getSeat().getRow(), ticket.getSeat().getSeatNumber(), ticket.getStatus().name(), ticket.getPriceCents(),
                ticket.getHeldUntil());

    }
    public List<MyTicket> ticketsFor(Long ownerId, Long eventId) {
        return ticketRepository.findMyTickets(ownerId, eventId).stream().map(this::convert).toList();
    }

    public List<TicketHistory> historyFor(Long ownerId) {
        return ticketRepository.findMyTicketHistory(ownerId);
    }

}
