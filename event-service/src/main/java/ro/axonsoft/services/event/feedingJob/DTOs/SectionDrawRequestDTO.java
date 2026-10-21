package ro.axonsoft.services.event.feedingJob.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ro.axonsoft.services.event.layout.SectionGeometryCalculator.Point;

import java.util.List;

// angle is optional: null for a freeform-drawn section (SectionGeometryCalculator
// falls back to guessing a facing angle from the polygon's centroid, as before);
// present for a section built from a predefined shape (Rectangle/Trapezoid), whose
// frontend shape state already knows its true orientation exactly - see
// SectionGeometryCalculator.compute()'s explicit-angle overload for why the guess
// is actively wrong once a shape can be rotated to face any direction.
public record SectionDrawRequestDTO(
        @NotBlank(message = "section name is required") String name,
        @NotEmpty(message = "section outline requires at least one point") List<Point> points,
        @NotNull(message = "rows is required") @Positive Integer rows,
        @NotNull(message = "seatsPerRow is required") @Positive Integer seatsPerRow,
        Double angle
) {}
