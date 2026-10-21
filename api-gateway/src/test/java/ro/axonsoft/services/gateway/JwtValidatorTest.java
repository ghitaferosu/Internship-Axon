package ro.axonsoft.services.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.axonsoft.services.gateway.security.JwtValidator;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidatorTest {

    private static final String SECRET = "J3ME1PCRd2hSuP20NnWFhKZCcb/d4oxYl/cRIVM68i8=";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    private static final SecretKey ALT_KEY = Jwts.SIG.HS256.key().build();

    private JwtValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JwtValidator();
        ReflectionTestUtils.setField(validator, "secretKey", SECRET);
    }

    private String token(SecretKey signingKey, String type, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(Map.of("uid", 1, "roles", List.of("ROLE_USER"), "type", type))
                .subject("denis@test.com")
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(signingKey)
                .compact();
    }

    @Test
    void acceptValidAccessToken() {
        Claims c = validator.parseAccessToken(token(KEY, "ACCESS", 60_000));

        assertThat(c.getSubject()).isEqualTo("denis@test.com");
        assertThat(c.get("type", String.class)).isEqualTo("ACCESS");
        assertThat(((Number) c.get("uid")).longValue()).isEqualTo(1L);
        List<String> roles = c.get("roles", List.class);
        assertThat(roles).containsExactly("ROLE_USER");
    }

    @Test
    void rejectRefreshToken() {
        String refresh = token(KEY, "REFRESH", 60_000);

        assertThatThrownBy(() -> validator.parseAccessToken(refresh)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectInvalidToken() {
        String invalid = token(ALT_KEY, "ACCESS", 60_000);
        assertThatThrownBy(() -> validator.parseAccessToken(invalid)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectExpiredToken() {
        String expired = token(KEY, "ACCESS", -1_000);

        assertThatThrownBy(() -> validator.parseAccessToken(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectModifiedToken() {
        String[] parts = token(KEY, "ACCESS", 60_000).split("\\.");
        String modified = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "XY." + parts[2];

        assertThatThrownBy(() -> validator.parseAccessToken(modified)).isInstanceOf(JwtException.class);
    }
}



