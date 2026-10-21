package ro.axonsoft.services.event.eventSearch;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.hibernate.query.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@Validated
public class EventSearchController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final EventSearchService eventSearchService;
    private final EventDetailService eventDetailService;

    public EventSearchController(EventSearchService eventSearchService, EventDetailService eventDetailService) {
        this.eventSearchService = eventSearchService;
        this.eventDetailService = eventDetailService;
    }

    @GetMapping
    public PageResponse<EventSummaryDTO> search(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateTo,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(1) int size,
            @RequestParam(defaultValue = "false") boolean includeUnpublished
    ){
        EventSearchCriteria criteria = new EventSearchCriteria(
                country,
                city,
                dateFrom,
                dateTo,
                keyword
        );

        return eventSearchService.searchEvent(criteria,page,size,includeUnpublished);
    }

    // Week-3 deliverable: an event together with its seat map (venue layout),
    // in one call. Per-section seats + ticket status stay paginated behind
    // GET /sections/{sectionId}/seats?eventId=... - see EventWithSeatMapDTO.
    @GetMapping("/{eventId}")
    public EventWithSeatMapDTO getEvent(@PathVariable Long eventId,
                                        @RequestParam(defaultValue = "false") boolean includeUnpublished) {
        return eventDetailService.getEventWithSeatMap(eventId, includeUnpublished);
    }

}
