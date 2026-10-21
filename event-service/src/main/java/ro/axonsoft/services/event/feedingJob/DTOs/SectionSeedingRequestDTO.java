package ro.axonsoft.services.event.feedingJob.DTOs;

import ro.axonsoft.services.event.layout.SectionGeometryCalculator.Point;

import java.util.List;

// Computed bundle (from SectionGeometryCalculator.compute()) + the original
// drawn vertices, ready to persist onto Section. Not the same type as
// SectionDrawRequestDTO, which carries only raw points before geometry runs.
public record SectionSeedingRequestDTO(
        String name,
        double angle,
        double originX,
        double originY,
        int rowSpacing,
        int colSpacing,
        int rowStart,
        int rowEnd,
        int seatStart,
        int seatEnd,
        List<Point> vertices
) {}
