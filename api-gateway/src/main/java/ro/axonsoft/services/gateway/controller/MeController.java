package ro.axonsoft.services.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.axonsoft.services.gateway.client.EventClient;
import ro.axonsoft.services.gateway.dto.event_dto.MyTicketResponse;
import ro.axonsoft.services.gateway.dto.event_dto.TicketHistoryResponse;

import java.util.List;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final EventClient eventClient;

    @GetMapping("/tickets")
    public List<MyTicketResponse> myTickets(@RequestParam(required = false) Long eventId) {
        return eventClient.myTickets(eventId);
    }

    @GetMapping("/tickets/history")
    public List<TicketHistoryResponse> myTicketsHistory() {
        return eventClient.myTicketHistory();
    }
}
