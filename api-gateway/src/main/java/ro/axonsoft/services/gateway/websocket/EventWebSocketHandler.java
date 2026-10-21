package ro.axonsoft.services.gateway.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventWebSocketHandler extends TextWebSocketHandler {

    private static final String EVENT_ID = "eventId";
    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long eventId = extractEventId(session);
        if (eventId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        session.getAttributes().put(EVENT_ID, eventId);
        sessions.computeIfAbsent(eventId, id -> ConcurrentHashMap.newKeySet()).add(session);

        log.info("Client connected to event {}", eventId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        removeSession(session);
    }

    public void broadcast(Long eventId, Object payload) {
        Set<WebSocketSession> viewers = sessions.get(eventId);
        if (viewers == null || viewers.isEmpty()) {
            return;
        }

        TextMessage message = toJsonMessage(payload);
        if (message == null) {
            return;
        }

        viewers.forEach(session -> send(session, message));
    }

    private Long extractEventId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String lastPart = path.substring(path.lastIndexOf('/') + 1);

        try {
            return Long.parseLong(lastPart);
        } catch (NumberFormatException e) {
            log.warn("Invalid path: {}", path);
            return null;
        }
    }

    private void removeSession(WebSocketSession session) {
        Long eventId = (Long) session.getAttributes().get(EVENT_ID);
        if (eventId == null) {
            return;
        }

        Set<WebSocketSession> viewers = sessions.get(eventId);
        if (viewers != null) {
            viewers.remove(session);
        }
    }

    private TextMessage toJsonMessage(Object payload) {
        try {
            return new TextMessage(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Serialization error", e);
            return null;
        }
    }

    private void send(WebSocketSession session, TextMessage message) {
        try {
            if(session.isOpen()) {
                session.sendMessage(message);
            }
        } catch (IOException e) {
            log.error("Error sending message", e);
            removeSession(session);
        }
    }
}
