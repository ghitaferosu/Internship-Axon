package ro.axonsoft.services.event.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentEvent(PaymentEventMessage message) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, message.partitionKey(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Publicare esuata: payment={} messageId={}",
                                message.paymentId(), message.messageId(), ex);
                    }
                });
    }
}