package ro.axonsoft.services.event.layout;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ro.axonsoft.services.event.feedingJob.DTOs.PageDTO;
import ro.axonsoft.services.event.layout.DTOs.SeatResponseDTO;
import ro.axonsoft.services.event.layout.DTOs.SeatWithStatusDTO;
import ro.axonsoft.services.event.ticket.DTO.SeatStatusView;
import ro.axonsoft.services.event.ticket.SeatStatusService;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SectionSeatsService {

    private final VenueLayoutService venueLayoutService;
    private final SeatStatusService seatStatusService;

    public PageDTO<SeatWithStatusDTO> getSeats(Long sectionId, int page, int size, Long eventId) {
        PageDTO<SeatResponseDTO> layout = venueLayoutService.getSeats(sectionId, page, size);
        List<Long> seatIds = layout.content().stream().map(SeatResponseDTO::id).toList();
        Map<Long, SeatStatusView> statuses = seatStatusService.statusesFor(eventId, seatIds);

        List<SeatWithStatusDTO> content = layout.content().stream().map(seat -> {
            SeatStatusView status = statuses.get(seat.id());
            return new SeatWithStatusDTO(seat.id(), seat.row(), seat.seatNumber(),
                    status == null ? null : status.status(),
                    status == null ? null : status.heldUntil());
        }).toList();
        return new PageDTO<>(content, layout.page(), layout.pageSize(), layout.totalElements(), layout.totalPages());
    }
}
