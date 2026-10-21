package ro.axonsoft.services.gateway.dto.event_dto;

import java.util.List;

// vertices is the real delivery point for the drawn section outline - every
// viewer (buyer or admin) fetches this to render the actual polygon, not
// just the seat-placement box (angle/origin/rowSpacing/colSpacing) below it.
// rowSpacing (row-to-row) and colSpacing (seat-to-seat) are independent -
// this record must mirror event-service's SectionLayoutDTO field-for-field,
// since EventClient's Feign interface deserializes event-service's response
// straight into it (and this record's fields are, in turn, exactly what gets
// re-serialized out to app-ui) - a field missing here silently drops out of
// both directions. Was missing colSpacing entirely (stale comment claimed it
// was removed in favor of one shared rowSpacing, which was never true here -
// see SectionGeometryCalculator/geometry.ts), so app-ui's effectiveColSpacing()
// fallback silently substituted rowSpacing for every section fetched through
// the gateway, over-spreading seats past their section's outline.
public record SectionLayoutResponse(
        Long id,
        String name,
        double angle,
        int rowStart,
        int rowEnd,
        int seatStart,
        int seatEnd,
        int seatCount,
        int originX,
        int originY,
        int rowSpacing,
        int colSpacing,
        List<PointResponse> vertices
) {}
