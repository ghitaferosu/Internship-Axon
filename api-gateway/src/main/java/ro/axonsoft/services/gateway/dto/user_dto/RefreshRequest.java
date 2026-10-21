package ro.axonsoft.services.gateway.dto.user_dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String token) {
}
