package ro.axonsoft.services.event.eventSearch;


import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.axonsoft.services.event.repository.EventPerformerRepository;
import ro.axonsoft.services.event.repository.EventRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EventSearchService {
    private static final double THRESHOLD = 0.3;

    private static final long EXACT_COUNT_LIMIT = 5_000;
    private static final Pattern PLAN_ROWS = Pattern.compile("rows=(\\d+)");

    private final EventRepository eventRepository;
    private final EventPerformerRepository eventPerformerRepository;

    public EventSearchService(EventRepository eventRepository, EventPerformerRepository eventPerformerRepository) {
        this.eventRepository = eventRepository;
        this.eventPerformerRepository = eventPerformerRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryDTO> searchEvent(EventSearchCriteria criteria, int page, int size,
                                                     boolean includeUnpublished){
        String country = blankToNull(criteria.country());
        String city = blankToNull(criteria.city());
        OffsetDateTime dateFrom = criteria.dateFrom();
        OffsetDateTime dateTo = criteria.dateTo();
        String keyword = blankToNull(criteria.keyword());

        List<EventDTO> events = eventRepository.searchFuzzy(
                country, city, dateFrom, dateTo, keyword, includeUnpublished, THRESHOLD,
                PageRequest.of(page, size));

        long atLeast = events.isEmpty() ? 0 : (long) page * size + events.size();
        long totalElements = Math.max(
                totalFor(country, city, dateFrom, dateTo, keyword, includeUnpublished), atLeast);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        Map<Long, List<String>> performers = performersFor(events.stream().map(EventDTO::id).toList());
        List<EventSummaryDTO> content = events.stream()
                .map(event -> EventSummaryDTO.fromEventAndPerformers(event, performers.getOrDefault(event.id(), List.of())))
                .toList();

        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    private long totalFor(String country, String city, OffsetDateTime dateFrom, OffsetDateTime dateTo,
                          String keyword, boolean includeUnpublished) {
        OptionalLong estimate = estimateRows(country, city, dateFrom, dateTo, keyword, includeUnpublished);
        if (estimate.isPresent() && estimate.getAsLong() >= EXACT_COUNT_LIMIT) {
            return estimate.getAsLong();
        }
        return eventRepository.countFuzzy(country, city, dateFrom, dateTo, keyword, includeUnpublished, THRESHOLD);
    }

    private OptionalLong estimateRows(String country, String city, OffsetDateTime dateFrom, OffsetDateTime dateTo,
                                      String keyword, boolean includeUnpublished) {
        List<String> plan = eventRepository.explainFuzzy(
                country, city, dateFrom, dateTo, keyword, includeUnpublished, THRESHOLD);

        for (String line : plan) {
            if (line == null) {
                continue;
            }
            Matcher matcher = PLAN_ROWS.matcher(line);
            if (matcher.find()) {
                return OptionalLong.of(Long.parseLong(matcher.group(1)));
            }
        }
        return OptionalLong.empty();
    }

    private Map<Long, List<String>> performersFor(List<Long> eventIds) {
        if(eventIds.isEmpty()) {
            return Map.of();
        }
        return eventPerformerRepository.findPerformerNames(eventIds).stream().collect(Collectors.groupingBy(EventPerformerName::eventId,
                Collectors.mapping(EventPerformerName::performerName, Collectors.toList())));
    }

    private String blankToNull(String value){
        return (value == null || value.isBlank()) ? null : value;
    }
}
