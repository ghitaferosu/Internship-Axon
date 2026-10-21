package ro.axonsoft.services.event.layout;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.axonsoft.services.event.feedingJob.DTOs.PageDTO;
import ro.axonsoft.services.event.layout.DTOs.SeatWithStatusDTO;

@RestController
public class SectionSeatsController {

    private final SectionSeatsService sectionSeatsService;

    public SectionSeatsController(SectionSeatsService sectionSeatsService) {
        this.sectionSeatsService = sectionSeatsService;
    }

    @GetMapping("/sections/{sectionId}/seats")
    public PageDTO<SeatWithStatusDTO> getSeats(
            @PathVariable Long sectionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size,
            @RequestParam(required = false) Long eventId) {
        return sectionSeatsService.getSeats(sectionId, page, size, eventId);
    }
}
