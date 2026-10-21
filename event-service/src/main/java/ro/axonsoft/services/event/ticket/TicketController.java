package ro.axonsoft.services.event.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ro.axonsoft.services.event.ticket.DTO.MyTicket;
import ro.axonsoft.services.event.ticket.DTO.TicketHistory;

import java.util.List;

@RestController
@RequestMapping("/api/me/tickets")
@RequiredArgsConstructor

public class TicketController {
    private final TicketService ticketService;

    @GetMapping
    public List<MyTicket> myTickets(@RequestHeader("X-User-Id") Long userId, @RequestParam(required = false) Long eventId) {
        return ticketService.ticketsFor(userId, eventId);
    }

    @GetMapping("/history")
    public List<TicketHistory> myTicketsHistory(@RequestHeader("X-User-Id") Long userId) {
        return ticketService.historyFor(userId);
    }
}
