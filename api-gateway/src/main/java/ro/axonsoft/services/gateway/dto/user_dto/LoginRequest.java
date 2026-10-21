package ro.axonsoft.services.gateway.dto.user_dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest (
        @NotBlank @Email String email,
        @NotBlank String password
){}