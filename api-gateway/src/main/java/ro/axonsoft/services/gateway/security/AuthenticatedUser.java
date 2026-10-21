package ro.axonsoft.services.gateway.security;

import java.util.List;

public record AuthenticatedUser(Long id, String email, List<String> roles) {
}
