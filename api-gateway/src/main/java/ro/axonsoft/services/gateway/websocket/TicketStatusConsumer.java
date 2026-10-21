package ro.axonsoft.services.gateway.websocket;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ro.axonsoft.services.gateway.dto.event_dto.TicketStatusMessage;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketStatusConsumer {

    private final ObjectMapper objectMapper;
    private final EventWebSocketHandler eventWebSocketHandler;

    @KafkaListener(topics = "ticket-events")
    public void onTicketStatusChanged(String payload) {
        try {
            TicketStatusMessage message = objectMapper.readValue(payload, TicketStatusMessage.class);
            eventWebSocketHandler.broadcast(message.eventId(), message);
        } catch (Exception e) {
            log.error("Invalid message on ticket-events: {}", payload, e);
        }
    }
}
