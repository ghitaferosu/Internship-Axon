package ro.axonsoft.services.event.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TicketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TicketEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TicketEventPublisher(KafkaTemplate<String, Object> ticketEventKafkaTemplate) {
        this.kafkaTemplate = ticketEventKafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketEvent(TicketEventMessage message) {
        kafkaTemplate.send(KafkaTopics.TICKET_EVENTS, message.partitionKey(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Publicare esuata: type={} event={} seat={} messageId={}",
                                message.type(), message.eventId(), message.seatId(), message.messageId(), ex);
                    } else {
                        log.debug("Publicat {} -> {}-{}@{}",
                                message.type(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
