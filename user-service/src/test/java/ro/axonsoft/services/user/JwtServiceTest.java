package ro.axonsoft.services.user;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.axonsoft.services.user.model.Role;
import ro.axonsoft.services.user.model.User;
import ro.axonsoft.services.user.security.JwtService;
import ro.axonsoft.services.user.security.UserPrincipal;

import javax.crypto.SecretKey;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "J3ME1PCRd2hSuP20NnWFhKZCcb/d4oxYl/cRIVM68i8=";
    private static final long ACCESS_TTL = 1_800_000L;
    private static final long REFRESH_TTL = 604_800_000L;

    private JwtService jwtService;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", ACCESS_TTL);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", REFRESH_TTL);

        Role role = new Role();
        role.setName("ROLE_USER");
        User user = new User();
        user.setId(1L);
        user.setEmail("denis@test.com");
        user.setPassword("hash");
        user.getRoles().add(role);
        principal = new UserPrincipal(user);
    }

    private Claims claims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void accessTokenContainsIdAndRoles() {
        Claims c = claims(jwtService.generateToken(principal));

        assertThat(c.getSubject()).isEqualTo("denis@test.com");
        assertThat(c.get("type", String.class)).isEqualTo("ACCESS");
        assertThat(((Number) c.get("uid")).longValue()).isEqualTo(1L);

        List<String> roles = c.get("roles", List.class);
        assertThat(roles).containsExactly("ROLE_USER");
    }

    @Test
    void refreshTokenType() {
        Claims c = claims(jwtService.generateRefreshToken(principal));

        assertThat(c.get("type", String.class)).isEqualTo("REFRESH");
        assertThat(c.getSubject()).isEqualTo("denis@test.com");
    }

    @Test
    void expirationTimes() {
        Claims access = claims(jwtService.generateToken(principal));
        Claims refresh = claims(jwtService.generateRefreshToken(principal));

        assertThat(access.getExpiration().getTime() - access.getIssuedAt().getTime()).isEqualTo(ACCESS_TTL);
        assertThat(refresh.getExpiration().getTime() - refresh.getIssuedAt().getTime()).isEqualTo(REFRESH_TTL);
    }
}


