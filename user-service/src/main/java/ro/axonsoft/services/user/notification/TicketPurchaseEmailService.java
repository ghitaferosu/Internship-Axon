package ro.axonsoft.services.user.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import ro.axonsoft.services.user.dto.kafkaEvents.PaymentSucceededEvent;
import ro.axonsoft.services.user.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketPurchaseEmailService {

    private static final String ITEMS_PREFIX = "ticket-order:";
    private static final String SENT_PREFIX = "ticket-order-email:";
    private static final String PENDING_INDEX = "ticket-orders:pending";

    private static final Duration ITEMS_TTL = Duration.ofHours(24);
    private static final Duration SENT_MARKER_TTL = Duration.ofDays(7);
    private static final Duration INCOMPLETE_ORDER_GRACE = Duration.ofMinutes(2);

    private static final Locale RO = Locale.of("ro", "RO");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", RO);

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${application.mail.from}")
    private String from;

    public void collect(PaymentSucceededEvent event) throws JsonProcessingException {
        String orderKey = event.orderKey();
        String itemsKey = ITEMS_PREFIX + orderKey;
        redisTemplate.opsForHash().put(
                itemsKey, event.messageId().toString(), objectMapper.writeValueAsString(event));
        redisTemplate.expire(itemsKey, ITEMS_TTL);
        redisTemplate.opsForZSet().addIfAbsent(
                PENDING_INDEX, orderKey, Instant.now().getEpochSecond());

        long collected = redisTemplate.opsForHash().size(itemsKey);
        if (collected >= event.expectedSize()) {
            flush(orderKey);
        } else {
            log.debug("Comanda {}: {}/{} bilete, astept restul",
                    orderKey, collected, event.expectedSize());
        }
    }

    public void flushStaleOrders() {
        Set<String> stale = redisTemplate.opsForZSet().rangeByScore(
                PENDING_INDEX, 0, Instant.now().minus(INCOMPLETE_ORDER_GRACE).getEpochSecond());
        if (stale == null) {
            return;
        }
        for (String orderKey : stale) {
            try {
                flush(orderKey);
            } catch (RuntimeException e) {
                log.error("Trimitere esuata pentru comanda incompleta {}", orderKey, e);
            }
        }
    }

    private void flush(String orderKey) {
        if (!claim(SENT_PREFIX + orderKey)) {
            cleanup(orderKey);
            return;
        }

        List<PaymentSucceededEvent> tickets = readItems(orderKey);
        if (tickets.isEmpty()) {
            cleanup(orderKey);
            return;
        }

        var user = userRepository.findById(tickets.getFirst().payerId()).orElse(null);
        if (user == null) {
            log.error("Comanda {}: user {} inexistent", orderKey, tickets.getFirst().payerId());
            cleanup(orderKey);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(user.getEmail());
            message.setSubject(buildSubject(tickets));
            message.setText(buildBody(user.getFirstName(), tickets));
            mailSender.send(message);
            log.info("Email trimis pentru comanda {} ({} bilete)", orderKey, tickets.size());
        } catch (RuntimeException e) {
            redisTemplate.delete(SENT_PREFIX + orderKey);
            throw e;
        }

        cleanup(orderKey);
    }

    private List<PaymentSucceededEvent> readItems(String orderKey) {
        return redisTemplate.opsForHash().values(ITEMS_PREFIX + orderKey).stream()
                .map(raw -> parse((String) raw))
                .sorted(Comparator.comparing(PaymentSucceededEvent::eventName)
                        .thenComparing(PaymentSucceededEvent::sectionName)
                        .thenComparingInt(PaymentSucceededEvent::rowNumber)
                        .thenComparingInt(PaymentSucceededEvent::seatNumber))
                .toList();
    }

    private PaymentSucceededEvent parse(String raw) {
        try {
            return objectMapper.readValue(raw, PaymentSucceededEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Bilet corupt in Redis", e);
        }
    }

    private String buildSubject(List<PaymentSucceededEvent> tickets) {
        String eventName = tickets.getFirst().eventName();
        return tickets.size() == 1
                ? "Biletul tau pentru " + eventName
                : "Cele %d bilete ale tale pentru %s".formatted(tickets.size(), eventName);
    }

    private String buildBody(String firstName, List<PaymentSucceededEvent> tickets) {
        PaymentSucceededEvent first = tickets.getFirst();
        int totalCents = tickets.stream().mapToInt(PaymentSucceededEvent::amountCents).sum();

        StringBuilder body = new StringBuilder("""
                Salut %s,

                Achizitia ta a fost confirmata.

                Eveniment: %s
                Locatie:   %s, %s
                Data:      %s

                Biletele tale:
                """.formatted(
                firstName,
                first.eventName(),
                first.venueName(), first.city(),
                first.eventStartHour().format(DATE_FMT)));

        for (PaymentSucceededEvent t : tickets) {
            body.append("  - Sector %s | Rand %d | Loc %d  -  %s RON  (bilet #%d)%n".formatted(
                    t.sectionName(), t.rowNumber(), t.seatNumber(),
                    formatAmount(t.amountCents()), t.ticketId()));
        }

        body.append("""

                Total platit: %s RON (%d bilete)

                Prezinta acest email la intrare.

                Ticketmaster
                """.formatted(formatAmount(totalCents), tickets.size()));

        return body.toString();
    }

    private String formatAmount(int amountCents) {
        return String.format(Locale.ROOT, "%d.%02d", amountCents / 100, amountCents % 100);
    }

    private boolean claim(String key) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, "1", SENT_MARKER_TTL));
    }

    private void cleanup(String orderKey) {
        redisTemplate.delete(ITEMS_PREFIX + orderKey);
        redisTemplate.opsForZSet().remove(PENDING_INDEX, orderKey);
    }
}
