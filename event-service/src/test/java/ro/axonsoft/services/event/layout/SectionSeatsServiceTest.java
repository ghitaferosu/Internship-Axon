package ro.axonsoft.services.event.layout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ro.axonsoft.services.event.feedingJob.DTOs.PageDTO;
import ro.axonsoft.services.event.layout.DTOs.SeatResponseDTO;
import ro.axonsoft.services.event.layout.DTOs.SeatWithStatusDTO;
import ro.axonsoft.services.event.model.TicketStatus;
import ro.axonsoft.services.event.ticket.DTO.SeatStatusView;
import ro.axonsoft.services.event.ticket.SeatStatusService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionSeatsServiceTest {

    @Mock
    private VenueLayoutService venueLayoutService;
    @Mock
    private SeatStatusService seatStatusService;

    @InjectMocks
    private SectionSeatsService sectionSeatsService;

    private PageDTO<SeatResponseDTO> layoutPage() {
        return new PageDTO<>(List.of(new SeatResponseDTO(10L, 1, 1), new SeatResponseDTO(11L, 1, 2)),
                0, 200, 2, 1);
    }

    @Test
    void leavesStatusEmptyWhenNoEventGiven() {
        when(venueLayoutService.getSeats(5L, 0, 200)).thenReturn(layoutPage());
        when(seatStatusService.statusesFor(null, List.of(10L, 11L))).thenReturn(Map.of());
        PageDTO<SeatWithStatusDTO> result = sectionSeatsService.getSeats(5L, 0, 200, null);

        assertThat(result.content()).containsExactly(new SeatWithStatusDTO(10L, 1, 1, null, null),
                new SeatWithStatusDTO(11L, 1, 2, null, null));
    }

    @Test
    void keepsPaginationFromTheLayout() {
        when(venueLayoutService.getSeats(5L, 0, 200)).thenReturn(layoutPage());
        when(seatStatusService.statusesFor(null, List.of(10L, 11L))).thenReturn(Map.of());
        PageDTO<SeatWithStatusDTO> result = sectionSeatsService.getSeats(5L, 0, 200, null);

        assertThat(result.page()).isZero();
        assertThat(result.pageSize()).isEqualTo(200);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void pairsEachSeatWithItsStatus() {
        when(venueLayoutService.getSeats(5L, 0, 200)).thenReturn(layoutPage());
        when(seatStatusService.statusesFor(1L, List.of(10L, 11L))).thenReturn(Map.of(10L, new SeatStatusView(TicketStatus.SOLD, null),
                11L, new SeatStatusView(TicketStatus.AVAILABLE, null)));
        PageDTO<SeatWithStatusDTO> result = sectionSeatsService.getSeats(5L, 0, 200, 1L);

        assertThat(result.content()).containsExactly(new SeatWithStatusDTO(10L, 1, 1, TicketStatus.SOLD, null),
                new SeatWithStatusDTO(11L, 1, 2, TicketStatus.AVAILABLE, null));
    }

    @Test
    void leavesStatusEmptyForSeatWithoutTicket() {
        when(venueLayoutService.getSeats(5L, 0, 200)).thenReturn(layoutPage());
        when(seatStatusService.statusesFor(1L, List.of(10L, 11L)))
                .thenReturn(Map.of(10L, new SeatStatusView(TicketStatus.SOLD, null)));
        PageDTO<SeatWithStatusDTO> result = sectionSeatsService.getSeats(5L, 0, 200, 1L);

        assertThat(result.content()).containsExactly(new SeatWithStatusDTO(10L, 1, 1, TicketStatus.SOLD, null),
                new SeatWithStatusDTO(11L, 1, 2, null, null));
    }
}
