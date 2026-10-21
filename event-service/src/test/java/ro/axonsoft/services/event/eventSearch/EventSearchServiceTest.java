package ro.axonsoft.services.event.eventSearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ro.axonsoft.services.event.repository.EventPerformerRepository;
import ro.axonsoft.services.event.repository.EventRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventSearchServiceTest {

    private static final double THRESHOLD = 0.3;
    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventPerformerRepository eventPerformerRepository;

    private EventSearchService newService() {
        return new EventSearchService(eventRepository, eventPerformerRepository);
    }

    private EventDTO anEvent() {
        return new EventDTO(1L, "Rock Fest", "Romania", "Cluj-Napoca",
                OffsetDateTime.of(2026, 9, 1, 20, 0, 0, 0, ZoneOffset.UTC).toInstant(),
                "LIVE", "National Arena");
    }

    private List<EventDTO> pageOf(EventDTO... events) {
        return List.of(events);
    }

    private void countReturns(long exact) {
        when(eventRepository.countFuzzy(any(), any(), any(), any(), any(), anyBoolean(), anyDouble()))
                .thenReturn(exact);
    }

    @Test
    void passesCriteriaThroughToTheRepository() {
        OffsetDateTime from = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(2026, 12, 31, 0, 0, 0, 0, ZoneOffset.UTC);
        when(eventRepository.searchFuzzy(any(), any(), any(), any(), any(), anyBoolean(), eq(THRESHOLD), eq(PAGEABLE)))
                .thenReturn(pageOf(anEvent()));

        PageResponse<EventSummaryDTO> result = newService()
                .searchEvent(new EventSearchCriteria("Romania", "Cluj", from, to, "rock"), 0, 20, false);

        assertThat(result.content()).hasSize(1);
        verify(eventRepository).searchFuzzy("Romania", "Cluj", from, to, "rock", false, THRESHOLD, PAGEABLE);
    }

    @Test
    void convertsBlankFiltersToNullSoTheQuerySkipsThem() {
        when(eventRepository.searchFuzzy(any(), any(), any(), any(), any(), anyBoolean(), eq(THRESHOLD), eq(PAGEABLE)))
                .thenReturn(pageOf());

        newService().searchEvent(new EventSearchCriteria("", "   ", null, null, ""), 0, 20, false);

        verify(eventRepository).searchFuzzy(null, null, null, null, null, false, THRESHOLD, PAGEABLE);
    }

    @Test
    void keepsNullFiltersAsNull() {
        when(eventRepository.searchFuzzy(any(), any(), any(), any(), any(), anyBoolean(), eq(THRESHOLD), eq(PAGEABLE)))
                .thenReturn(pageOf());

        newService().searchEvent(new EventSearchCriteria(null, null, null, null, null), 0, 20, false);

        verify(eventRepository).searchFuzzy(null, null, null, null, null, false, THRESHOLD, PAGEABLE);
    }

    @Test
    void convertsOnlyBlankFieldsToNull_keepsNonBlankFieldsIntact() {
        when(eventRepository.searchFuzzy(any(), any(), any(), any(), any(), anyBoolean(), eq(THRESHOLD), eq(PAGEABLE)))
                .thenReturn(pageOf());

        newService().searchEvent(new EventSearchCriteria("Romania", "   ", null, null, "rock"), 0, 20, false);

        verify(eventRepository).searchFuzzy("Romania", null, null, null, "rock", false, THRESHOLD, PAGEABLE);
    }

    @Test
    void returnsExactlyWhatRepositoryReturns_multipleResults() {
        EventDTO first = anEvent();
        EventDTO second = new EventDTO(2L, "Jazz Night", "Romania", "Bucuresti",
                OffsetDateTime.of(2026, 10, 5, 19, 0, 0, 0, ZoneOffset.UTC).toInstant(),
                "DRAFT", "Sala Radio");

        when(eventRepository.searchFuzzy(any(), any(), any(), any(), any(), anyBoolean(), eq(THRESHOLD), eq(PAGEABLE)))
                .thenReturn(pageOf(first, second));

        PageResponse<EventSummaryDTO> result = newService()
                .searchEvent(new EventSearchCriteria(null, null, null, null, null), 0, 20, false);

        assertThat(result.content()).containsExactly(EventSummaryDTO.fromEventAndPerformers(first, List.of()),
                EventSummaryDTO.fromEventAndPerformers(second, List.of()));
    }

    @Test
    void returnsEmptyListWhenRepositoryFindsNothing() {
        when(eventRepository.searchFuzzy(any(), any(), any(), any(), any(), anyBoolean(), eq(THRESHOLD), eq(PAGEABLE)))
                .thenReturn(pageOf());

        PageResponse<EventSummaryDTO> result = newService()
                .searchEvent(new EventSearchCriteria(null, null, null, null, "nonexistent"), 0, 20, false);

        assertThat(result.content()).isEmpty();
    }

    @Test
    void keywordWithOnlyWhitespace_convertsToNull() {
        when(eventRepository.searchFuzzy(any(), any(), any(), any(), any(), anyBoolean(), eq(THRESHOLD), eq(PAGEABLE)))
                .thenReturn(pageOf());

        newService().searchEvent(new EventSearchCriteria(null, null, null, null, "   \t  "), 0, 20, false);

        verify(eventRepository).searchFuzzy(null, null, null, null, null, false, THRESHOLD, PAGEABLE);
    }

    @Test
    void asksTheRepositoryForUnpublishedEventsOnlyWhenAllowed() {
        when(eventRepository.searchFuzzy(any(), any(), any(), any(), any(), anyBoolean(), eq(THRESHOLD), eq(PAGEABLE)))
                .thenReturn(pageOf());

        newService().searchEvent(new EventSearchCriteria(null, null, null, null, null), 0, 20, true);

        verify(eventRepository).searchFuzzy(null, null, null, null, null, true, THRESHOLD, PAGEABLE);
    }

    @Test
    void usesFixedSimilarityThreshold_regardlessOfCriteria() {
        when(eventRepository.searchFuzzy(any(), any(), any(), any(), any(), anyBoolean(), anyDouble(), any()))
                .thenReturn(pageOf());

        newService().searchEvent(new EventSearchCriteria("Romania", null, null, null, null), 0, 20, false);

        verify(eventRepository).searchFuzzy(eq("Romania"), eq(null), eq(null), eq(null), eq(null), eq(false), eq(0.3), eq(PAGEABLE));
    }

    @Test
    void passesThroughThePageAndSizeAsAPageable() {
        Pageable secondPage = PageRequest.of(1, 5);
        when(eventRepository.searchFuzzy(any(), any(), any(), any(), any(), anyBoolean(), eq(THRESHOLD), eq(secondPage)))
                .thenReturn(List.of(anEvent()));
        countReturns(6);

        PageResponse<EventSummaryDTO> result = newService()
                .searchEvent(new EventSearchCriteria(null, null, null, null, null), 1, 5, false);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(6);
    }
}
