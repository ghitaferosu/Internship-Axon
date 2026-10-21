package ro.axonsoft.services.event.ticket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ro.axonsoft.services.event.model.TicketStatus;
import ro.axonsoft.services.event.repository.TicketRepository;
import ro.axonsoft.services.event.ticket.DTO.SeatStatusRow;
import ro.axonsoft.services.event.ticket.DTO.SeatStatusView;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatStatusServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private SeatStatusService seatStatusService;

    @Test
    void returnsNothingWhenEventIsMissing() {
        assertThat(seatStatusService.statusesFor(null, List.of(10L))).isEmpty();
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void returnsNothingWhenThereAreNoSeats() {
        assertThat(seatStatusService.statusesFor(1L, List.of())).isEmpty();
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void mapsEachSeatToItsStatus() {
        when(ticketRepository.findStatuses(1L, List.of(10L, 11L))).thenReturn(List.of(new SeatStatusRow(10L, TicketStatus.SOLD, null),
                new SeatStatusRow(11L, TicketStatus.AVAILABLE, null)));
        Map<Long, SeatStatusView> statuses = seatStatusService.statusesFor(1L, List.of(10L, 11L));
        assertThat(statuses).containsExactlyInAnyOrderEntriesOf(Map.of(10L, new SeatStatusView(TicketStatus.SOLD, null), 11L, new SeatStatusView(TicketStatus.AVAILABLE, null)));
    }

    @Test
    void reportsExpiredHoldAsAvailable() {
        OffsetDateTime past = OffsetDateTime.now().minusMinutes(1);
        when(ticketRepository.findStatuses(1L, List.of(10L))).thenReturn(List.of(new SeatStatusRow(10L, TicketStatus.HELD, past)));
        assertThat(seatStatusService.statusesFor(1L, List.of(10L))).containsEntry(10L, new SeatStatusView(TicketStatus.AVAILABLE, null));
    }

    @Test
    void keepsHoldThatHasNotExpiredYet() {
        OffsetDateTime future = OffsetDateTime.now().plusMinutes(10);
        when(ticketRepository.findStatuses(1L, List.of(10L))).thenReturn(List.of(new SeatStatusRow(10L, TicketStatus.HELD, future)));
        assertThat(seatStatusService.statusesFor(1L, List.of(10L))).containsEntry(10L, new SeatStatusView(TicketStatus.HELD, future));
    }
}
