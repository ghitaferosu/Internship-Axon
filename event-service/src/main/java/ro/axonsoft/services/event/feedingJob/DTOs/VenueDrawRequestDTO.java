package ro.axonsoft.services.event.feedingJob.DTOs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record VenueDrawRequestDTO(
        @NotBlank(message = "venue name is required") String name,
        @NotBlank(message = "address is required") String address,
        @NotEmpty(message = "a venue needs at least one section")
        List<@Valid SectionDrawRequestDTO> sections
) {}
