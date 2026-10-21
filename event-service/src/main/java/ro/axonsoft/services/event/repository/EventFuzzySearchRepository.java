package ro.axonsoft.services.event.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ro.axonsoft.services.event.eventSearch.EventDTO;

import java.time.OffsetDateTime;
import java.util.List;

public interface EventFuzzySearchRepository {

    String FROM_WHERE = """
    FROM events e
    JOIN venues v ON v.id = e.venue_id
    WHERE (:country IS NULL OR similarity(e.country, :country) > :threshold)
      AND (:city IS NULL OR similarity(e.city, :city) > :threshold)
      AND (CAST(:dateFrom AS timestamptz) IS NULL OR e.event_start_hour >= CAST(:dateFrom AS timestamptz))
      AND (CAST(:dateTo AS timestamptz) IS NULL OR e.event_start_hour <= CAST(:dateTo AS timestamptz))
      AND (CAST(:includeUnpublished AS boolean) = TRUE OR e.status = 'LIVE')
      AND (
            :keyword IS NULL
            OR similarity(e.name, :keyword) > :threshold
            OR EXISTS (
                SELECT 1 FROM events_performers ep
                JOIN performers p ON p.id = ep.performer_id
                WHERE ep.event_id = e.id AND similarity(p.name, :keyword) > :threshold
            )
      )
    """;

    String SEARCH_SQL = """
    SELECT
        e.id AS id,
        e.name AS name,
        e.country AS country,
        e.city AS city,
        e.event_start_hour AS eventStartHour,
        e.status AS status,
        v.name AS venueName
    """ + FROM_WHERE + """
    ORDER BY
        CASE WHEN :keyword IS NULL THEN 0 ELSE similarity(e.name, :keyword) END DESC
    """;

    @Query(value = SEARCH_SQL, nativeQuery = true)
    List<EventDTO> searchFuzzy(
            @Param("country") String country,
            @Param("city") String city,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            @Param("keyword") String keyword,
            @Param("includeUnpublished") boolean includeUnpublished,
            @Param("threshold") double threshold,
            Pageable pageable
    );

    @Query(value = "SELECT count(*) " + FROM_WHERE, nativeQuery = true)
    long countFuzzy(
            @Param("country") String country,
            @Param("city") String city,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            @Param("keyword") String keyword,
            @Param("includeUnpublished") boolean includeUnpublished,
            @Param("threshold") double threshold
    );

    @Query(value = "EXPLAIN " + SEARCH_SQL, nativeQuery = true)
    List<String> explainFuzzy(
            @Param("country") String country,
            @Param("city") String city,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            @Param("keyword") String keyword,
            @Param("includeUnpublished") boolean includeUnpublished,
            @Param("threshold") double threshold
    );
}
