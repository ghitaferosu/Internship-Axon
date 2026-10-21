package ro.axonsoft.services.user.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String token) {
}
