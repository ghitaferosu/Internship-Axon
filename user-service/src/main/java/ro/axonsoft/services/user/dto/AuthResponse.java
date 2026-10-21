package ro.axonsoft.services.user.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {
}
