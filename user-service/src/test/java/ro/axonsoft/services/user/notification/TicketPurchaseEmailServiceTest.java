package ro.axonsoft.services.user.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import ro.axonsoft.services.user.dto.kafkaEvents.PaymentSucceededEvent;
import ro.axonsoft.services.user.model.User;
import ro.axonsoft.services.user.repository.UserRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketPurchaseEmailServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SENT_KEY = "ticket-order-email:" + ORDER_ID;
    private static final Long PAYER_ID = 7L;

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private ZSetOperations<String, String> zSetOps;
    @Mock
    private UserRepository userRepository;

    private final ObjectMapper objectMapper =
            JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private final Map<Object, Object> items = new LinkedHashMap<>();

    private TicketPurchaseEmailService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        doAnswer(invocation -> items.put(invocation.getArgument(1), invocation.getArgument(2)))
                .when(hashOps).put(anyString(), any(), any());
        when(hashOps.size(anyString())).thenAnswer(invocation -> (long) items.size());
        when(hashOps.values(anyString())).thenAnswer(invocation -> new ArrayList<>(items.values()));

        User user = new User();
        user.setId(PAYER_ID);
        user.setEmail("adela@test.com");
        user.setFirstName("Adela");
        when(userRepository.findById(PAYER_ID)).thenReturn(Optional.of(user));

        service = new TicketPurchaseEmailService(mailSender, redisTemplate, userRepository, objectMapper);
        ReflectionTestUtils.setField(service, "from", "noreply@test.com");
    }

    private PaymentSucceededEvent aTicket(int seatNumber, int amountCents, int orderSize) {
        return new PaymentSucceededEvent(
                UUID.randomUUID(), "payment.succeeded", 100L + seatNumber, 1L, 200L + seatNumber,
                PAYER_ID, amountCents, ORDER_ID, orderSize,
                "Coldplay", "Arena Nationala", "Bucuresti",
                OffsetDateTime.parse("2026-09-01T20:00:00+03:00"),
                "Tribuna 1", 5, seatNumber);
    }

    private void claimSucceeds(boolean succeeds) {
        when(valueOps.setIfAbsent(eq(SENT_KEY), anyString(), any(Duration.class))).thenReturn(succeeds);
    }

    @Test
    void waitsUntilEveryTicketOfTheOrderArrived() throws JsonProcessingException {
        claimSucceeds(true);

        service.collect(aTicket(11, 15000, 3));
        service.collect(aTicket(12, 15000, 3));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendsOneEmailListingEveryTicketOfTheOrder() throws JsonProcessingException {
        claimSucceeds(true);

        service.collect(aTicket(12, 15000, 2));
        service.collect(aTicket(11, 25000, 2));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());

        SimpleMailMessage message = sent.getValue();
        assertThat(message.getSubject()).isEqualTo("Cele 2 bilete ale tale pentru Coldplay");
        assertThat(message.getText())
                .contains("Loc 11")
                .contains("Loc 12")
                .contains("Total platit: 400.00 RON (2 bilete)");
    }

    @Test
    void doesNotSendTwiceForTheSameOrder() throws JsonProcessingException {
        claimSucceeds(false);

        service.collect(aTicket(11, 15000, 1));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void releasesTheMarkerWhenSendingFails() {
        claimSucceeds(true);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.collect(aTicket(11, 15000, 1)))
                .isInstanceOf(MailSendException.class);

        verify(redisTemplate).delete(SENT_KEY);
    }
}
