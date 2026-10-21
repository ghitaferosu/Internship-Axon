package ro.axonsoft.services.user.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import ro.axonsoft.services.user.dto.kafkaEvents.UserRegisteredEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WelcomeEmailServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String KEY = "welcome-email:" + EVENT_ID;

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private WelcomeEmailService newService() {
        WelcomeEmailService service = new WelcomeEmailService(mailSender, redisTemplate);
        ReflectionTestUtils.setField(service, "from", "noreply@test.com");
        return service;
    }

    private UserRegisteredEvent anEvent() {
        return new UserRegisteredEvent(EVENT_ID, 1L, "adela@test.com", Instant.now());
    }

    private void claimSucceeds(boolean succeeds) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(succeeds);
    }

    @Test
    void sendsTheEmailTheFirstTime() {
        claimSucceeds(true);

        newService().sendWelcome(anEvent());

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void doesNotSendAgainForTheSameEventId() {
        claimSucceeds(false);

        newService().sendWelcome(anEvent());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void releasesTheMarkerWhenSendingFails() {
        claimSucceeds(true);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> newService().sendWelcome(anEvent()))
                .isInstanceOf(MailSendException.class);

        verify(redisTemplate).delete(KEY);
    }
}