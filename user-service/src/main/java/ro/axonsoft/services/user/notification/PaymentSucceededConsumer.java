package ro.axonsoft.services.user.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import ro.axonsoft.services.user.dto.kafkaEvents.PaymentSucceededEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSucceededConsumer {

    private static final String PAYMENT_SUCCEEDED = "payment.succeeded";

    private final ObjectMapper objectMapper;
    private final TicketPurchaseEmailService ticketPurchaseEmailService;

    @RetryableTopic(
            attempts = "8",
            backoff = @Backoff(delay = 10000, multiplier = 3.0, maxDelay = 300000),
            autoCreateTopics = "true",
            exclude = {MailAuthenticationException.class, MailParseException.class, JsonProcessingException.class},
            traversingCauses = "true")
    @KafkaListener(topics = "payment-events")
    public void onPaymentEvent(String payload) throws JsonProcessingException {
        PaymentSucceededEvent event = objectMapper.readValue(payload, PaymentSucceededEvent.class);

        if (!PAYMENT_SUCCEEDED.equals(event.type())) {
            return;
        }
        ticketPurchaseEmailService.collect(event);
    }

    @DltHandler
    public void onFailedPermanently(String payload) {
        log.error("Email de confirmare achizitie abandonat dupa retries: {}", payload);
    }
}
