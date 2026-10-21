package ro.axonsoft.services.gateway.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ro.axonsoft.services.gateway.security.AuthenticatedUser;

@Configuration
public class FeignConfig {
    @Bean
    public RequestInterceptor userContextInterceptor() {
        return template -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
                template.header("X-User-Id", String.valueOf(user.id()));
                template.header("X-User-Email", user.email());
                template.header("X-User-Roles", String.join(",", user.roles()));
            }
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String ua = attrs.getRequest().getHeader("User-Agent");
                if (ua != null) {
                    template.header("X-User-Agent", ua);
                }
            }
        };
    }
    @Bean
    public RequestInterceptor internalSecretInterceptor(
            @Value("${internal.shared-secret}") String sharedSecret) {
        return template -> template.header("X-Internal-Secret", sharedSecret);
    }
}
