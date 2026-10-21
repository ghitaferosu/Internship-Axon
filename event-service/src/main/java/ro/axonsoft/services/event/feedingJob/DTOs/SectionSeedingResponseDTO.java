package ro.axonsoft.services.event.feedingJob.DTOs;

import ro.axonsoft.services.event.layout.SectionGeometryCalculator.Point;

import java.util.List;

// Mirrors SectionSeedingRequestDTO with a generated id - the frontend gets
// back exactly what was persisted, vertices included, after save.
public record SectionSeedingResponseDTO(
        Long id,
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
