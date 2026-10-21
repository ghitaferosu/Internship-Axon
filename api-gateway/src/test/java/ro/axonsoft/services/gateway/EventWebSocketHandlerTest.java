package ro.axonsoft.services.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import ro.axonsoft.services.gateway.dto.event_dto.TicketStatusMessage;
import ro.axonsoft.services.gateway.websocket.EventWebSocketHandler;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;

import static org.mockito.Mockito.*;

public class EventWebSocketHandlerTest {

    private final EventWebSocketHandler handler = new EventWebSocketHandler(new ObjectMapper().registerModule(new JavaTimeModule()));

    private WebSocketSession sessionFor(String path) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create(path));
        when(session.getAttributes()).thenReturn(new HashMap<>());
        lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }

    private TicketStatusMessage messageFor(long eventId) {
        return new TicketStatusMessage(eventId, 123L, "HELD", Instant.parse("2026-08-12T10:00:00Z"), OffsetDateTime.parse("2026-08-12T10:10:00Z"));
    }

    @Test
    void sendsOnlyToSessionsWatchingThatEvent() throws Exception {
        WebSocketSession watcherOfFive = sessionFor("ws://localhost:8080/ws/events/5");
        WebSocketSession watcherOfTwelve = sessionFor("ws://localhost:8080/ws/events/12");

        handler.afterConnectionEstablished(watcherOfFive);
        handler.afterConnectionEstablished(watcherOfTwelve);
        handler.broadcast(5L, messageFor(5L));

        verify(watcherOfFive).sendMessage(any(TextMessage.class));
        verify(watcherOfTwelve, never()).sendMessage(any());
    }


    @Test
    void sendsToEverySessionWatchingTheSameEvent() throws Exception {
        WebSocketSession first = sessionFor("ws://localhost:8080/ws/events/5");
        WebSocketSession second = sessionFor("ws://localhost:8080/ws/events/5");

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        handler.broadcast(5L, messageFor(5L));

        verify(first).sendMessage(any(TextMessage.class));
        verify(second).sendMessage(any(TextMessage.class));
    }

    @Test
    void doesNothingWhenNobodyIsWatching() throws Exception {
        handler.broadcast(20L, messageFor(20L));
    }

    @Test
    void stopsSendingAfterSessionCloses() throws Exception {
        WebSocketSession first = sessionFor("ws://localhost:8080/ws/events/5");
        WebSocketSession second = sessionFor("ws://localhost:8080/ws/events/5");

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        handler.broadcast(5L, messageFor(5L));

        verify(first, never()).sendMessage(any());
        verify(second).sendMessage(any(TextMessage.class));
    }

    @Test
    void rejectsConnectionWithoutValidEventId() throws Exception {
        WebSocketSession first = sessionFor("ws://localhost:8080/ws/events/xyz");

        handler.afterConnectionEstablished(first);
        verify(first).close(CloseStatus.BAD_DATA);
    }
}
