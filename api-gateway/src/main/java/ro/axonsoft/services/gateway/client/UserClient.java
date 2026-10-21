package ro.axonsoft.services.gateway.client;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ro.axonsoft.services.gateway.dto.user_dto.*;

@FeignClient(name = "user-service", url = "${services.user-service.url}")
public interface UserClient {

    @PostMapping("/api/auth/register")
    UserResponse register(@RequestBody RegisterRequest request);

    @PostMapping("/api/auth/login")
    AuthResponse login(@RequestBody LoginRequest request);

    @PostMapping("/api/auth/refresh")
    AuthResponse refresh(@RequestBody RefreshRequest request);
}
