package ro.axonsoft.services.event.hold;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ExpiredHoldKeyListener extends KeyExpirationEventMessageListener {

    private static final Logger log = LoggerFactory.getLogger(ExpiredHoldKeyListener.class);
    private static final Pattern SEAT_HOLD_KEY = Pattern.compile("^seat-hold:(\\d+):(\\d+)$");

    private final ExpiredHoldReleaser expiredHoldReleaser;

    public ExpiredHoldKeyListener(RedisMessageListenerContainer listenerContainer,
                                  ExpiredHoldReleaser expiredHoldReleaser) {
        super(listenerContainer);
        this.expiredHoldReleaser = expiredHoldReleaser;
    }

    @Override
    protected void doHandleMessage(Message message) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        Matcher matcher = SEAT_HOLD_KEY.matcher(key);
        if (!matcher.matches()) {
            return;
        }

        Long eventId = Long.valueOf(matcher.group(1));
        Long seatId = Long.valueOf(matcher.group(2));
        try {
            if (expiredHoldReleaser.release(eventId, seatId)) {
                log.debug("Hold expirat eliberat imediat: event={} seat={}", eventId, seatId);
            }
        } catch (RuntimeException e) {
            log.error("Eliberare esuata pentru event={} seat={}, ramane pe seama sweeper-ului",
                    eventId, seatId, e);
        }
    }
}
