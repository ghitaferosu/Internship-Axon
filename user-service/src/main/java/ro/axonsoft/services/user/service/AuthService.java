package ro.axonsoft.services.user.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.axonsoft.services.user.dto.AuthResponse;
import ro.axonsoft.services.user.dto.LoginRequest;
import ro.axonsoft.services.user.dto.RegisterRequest;
import ro.axonsoft.services.user.dto.UserResponse;
import ro.axonsoft.services.user.dto.kafkaEvents.UserRegisteredEvent;
import ro.axonsoft.services.user.exception.AccountNotFound;
import ro.axonsoft.services.user.exception.DuplicatedEmail;
import ro.axonsoft.services.user.exception.MissingRole;
import ro.axonsoft.services.user.exception.WrongPassword;
import ro.axonsoft.services.user.exception.InvalidRefreshToken;
import ro.axonsoft.services.user.kafka.UserEventPublisher;
import ro.axonsoft.services.user.model.OutboxEvent;
import ro.axonsoft.services.user.model.RefreshToken;
import ro.axonsoft.services.user.model.Role;
import ro.axonsoft.services.user.model.User;
import ro.axonsoft.services.user.repository.OutboxRepository;
import ro.axonsoft.services.user.repository.RefreshTokenRepo;
import ro.axonsoft.services.user.repository.RoleRepository;
import ro.axonsoft.services.user.repository.UserRepository;
import ro.axonsoft.services.user.security.JwtService;
import ro.axonsoft.services.user.security.UserPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepo refreshTokenRepo;
    private final JwtService jwtService;
    private final TokenHashService tokenHashService;
    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;
    private final UserEventPublisher userEventPublisher;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    private record IssuedTokens(String accessToken, String refreshToken, RefreshToken entity) {}

    private IssuedTokens issueTokens(User user, String userAgent) {
        UserPrincipal userPrincipal = new UserPrincipal(user);
        String newAccessToken = jwtService.generateToken(userPrincipal);
        String newRefreshToken = jwtService.generateRefreshToken(userPrincipal);

        RefreshToken refreshTokenObj = new RefreshToken();
        refreshTokenObj.setUser(user);
        refreshTokenObj.setTokenHash(tokenHashService.sha256(newRefreshToken));
        refreshTokenObj.setExpiresAt(Instant.now().plusMillis(refreshExpiration));
        refreshTokenObj.setUserAgent(userAgent);
        RefreshToken savedNewToken = refreshTokenRepo.saveAndFlush(refreshTokenObj);

        return new IssuedTokens(newAccessToken, newRefreshToken, savedNewToken);
    }



    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serializarea evenimentului a eșuat", e);
        }
    }
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository
                .findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent e : batch) {
            userEventPublisher.publish(e.getTopic(), e.getAggregateId(), e.getPayload());
            e.setPublishedAt(Instant.now());
        }
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if(userRepository.existsByEmail(request.email())) {
            throw new DuplicatedEmail("Există deja un cont cu această adresă de email");
        }

        Role userRole = roleRepository.findByName("ROLE_USER").orElseThrow(() -> new MissingRole("ROLE_USER"));

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.getRoles().add(userRole);

        User savedUser = userRepository.save(user);

        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID(),
                savedUser.getId(),
                savedUser.getEmail(),
                Instant.now()
        );

        OutboxEvent outboxEvent = new OutboxEvent(UUID.randomUUID(), "USER", savedUser.getId().toString(), "UserRegistered",
                "users.registered", toJson(event));
        outboxRepository.save(outboxEvent);

        return new UserResponse(savedUser.getId(), savedUser.getEmail());
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent) {
        User user = userRepository.findByEmail(request.email().trim()).orElseThrow(() -> new AccountNotFound("Autentificare Eșuată"));

        if(!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new WrongPassword("Autentificare Eșuată");
        }

        IssuedTokens tokens =  issueTokens(user,  userAgent);

        return new AuthResponse(tokens.accessToken(), tokens.refreshToken());
    }

    @Transactional(noRollbackFor = InvalidRefreshToken.class)
    public AuthResponse refresh(String refreshTokenString, String userAgent) {
        try {
            jwtService.parseRefreshToken(refreshTokenString);
        } catch (JwtException e) {
            throw new InvalidRefreshToken("Token de reîmprospătare invalid");
        }
        String tokenHash = tokenHashService.sha256(refreshTokenString);
        Optional<RefreshToken> refreshTokenOptional = refreshTokenRepo.findByTokenHashWithLock(tokenHash);

        Instant now = Instant.now();

        if(refreshTokenOptional.isPresent()) {
            RefreshToken refreshToken = refreshTokenOptional.get();
            if(refreshToken.isRevoked()) {
                refreshTokenRepo.revokeAllByUserId(refreshToken.getUser().getId(), now);
                throw new InvalidRefreshToken("Token revocat");
            }

            User user = refreshToken.getUser();

            IssuedTokens tokens = issueTokens(user, userAgent);

            refreshToken.setRevokedAt(now);
            refreshToken.setReplacedBy(tokens.entity.getId());
            refreshTokenRepo.save(refreshToken);

            return new AuthResponse(tokens.accessToken(), tokens.refreshToken());

        } else {
            throw new InvalidRefreshToken("Token de reîmprospătare inexistent");
        }
    }
}
