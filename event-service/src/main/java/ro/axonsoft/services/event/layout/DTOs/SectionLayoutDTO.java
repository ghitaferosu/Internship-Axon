package ro.axonsoft.services.event.layout.DTOs;

import ro.axonsoft.services.event.layout.SectionGeometryCalculator.Point;

import java.util.List;

// vertices is the real delivery point for the drawn section outline - every
// viewer (buyer or admin) fetches this to render the actual polygon, not
// just the seat-placement box (angle/origin/rowSpacing/colSpacing) below it.
public record SectionLayoutDTO(
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
        List<Point> vertices
) {}
