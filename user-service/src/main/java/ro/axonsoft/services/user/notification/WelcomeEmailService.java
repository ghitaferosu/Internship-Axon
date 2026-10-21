package ro.axonsoft.services.user.notification;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import ro.axonsoft.services.user.dto.kafkaEvents.UserRegisteredEvent;

import java.time.Duration;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class WelcomeEmailService {

    private static final Duration SENT_MARKER_TTL=Duration.ofDays(7);
    private static final String KEY_PREFIX="welcome-email:";

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    @Value("${application.mail.from}")
    private String from;

    public void sendWelcome(UserRegisteredEvent event) {

        String key = KEY_PREFIX + event.eventId();

        if(!claim(key)) {
            log.debug("Welcome email already sent for {}", event.eventId());
            return;
        }
        try{
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(event.email());
            message.setSubject("Confirmare cont - Bun venit pe platforma!");
            message.setText("Salut! Contul tau a fost creat cu succes.");
            mailSender.send(message);
        } catch (RuntimeException e) {
            redisTemplate.delete(key);
            throw e;
        }
    }

    private boolean claim(String key) {
        Boolean claimed=redisTemplate.opsForValue().setIfAbsent(key,"1",SENT_MARKER_TTL);
        return Boolean.TRUE.equals(claimed);
    }

}
