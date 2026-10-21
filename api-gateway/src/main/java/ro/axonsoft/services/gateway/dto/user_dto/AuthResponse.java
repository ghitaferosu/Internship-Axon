package ro.axonsoft.services.gateway.dto.user_dto;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {
}
