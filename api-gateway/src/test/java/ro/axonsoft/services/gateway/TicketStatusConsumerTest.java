package ro.axonsoft.services.gateway;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ro.axonsoft.services.gateway.dto.event_dto.TicketStatusMessage;
import ro.axonsoft.services.gateway.websocket.EventWebSocketHandler;
import ro.axonsoft.services.gateway.websocket.TicketStatusConsumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketStatusConsumerTest {

    @Mock
    private EventWebSocketHandler eventWebSocketHandler;

    private TicketStatusConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        consumer = new TicketStatusConsumer(objectMapper, eventWebSocketHandler);
    }

    @Test
    void forwardsMessageToTheRightEvent() {
        consumer.onTicketStatusChanged("""
                {"eventId":5,"seatId":123,"status":"HELD","occurredAt":"2026-08-12T10:00:00Z"}""");
        verify(eventWebSocketHandler).broadcast(eq(5L), any(TicketStatusMessage.class));
    }

    @Test
    void ignoresBrokenMessageWithoutFailing() {
        consumer.onTicketStatusChanged("something");
        verifyNoInteractions(eventWebSocketHandler);
    }

    @Test
    void acceptsMessageWithFieldsItDoesNotKnow() {
        consumer.onTicketStatusChanged("""
                {"type":"seat.held","eventId":5,"seatId":123,"status":"HELD","occurredAt":"2026-08-12T10:00:00Z"}""");

        verify(eventWebSocketHandler).broadcast(eq(5L), any(TicketStatusMessage.class));
    }
}
