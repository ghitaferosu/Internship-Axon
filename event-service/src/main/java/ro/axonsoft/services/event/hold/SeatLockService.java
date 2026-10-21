package ro.axonsoft.services.event.hold;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * A per-seat distributed lock backed by a single Redis instance (SET NX PX).
 * This is deliberately not a full Redlock (no multi-node quorum) - the
 * project runs against one Redis (see application.yaml), so a plain atomic
 * "set if absent, with TTL" is sufficient and avoids pulling in a client
 * library (e.g. Redisson) for a guarantee this deployment doesn't need.
 *
 * The lock's value is a caller-supplied holder token (not just used as a
 * flag), so release/renew can verify the caller is still the one who holds
 * it before acting - see the compare-and-act Lua scripts below.
 */
@Component
public class SeatLockService {

    // Compare-and-delete: only release if the caller still holds the lock,
    // so a stale caller (e.g. one whose hold already expired and was
    // re-acquired by someone else) can't evict a lock it no longer owns.
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call("GET", KEYS[1]) == ARGV[1] then
                return redis.call("DEL", KEYS[1])
            else
                return 0
            end
            """, Long.class);

    // Compare-and-extend: same ownership check, but renews the TTL instead
    // of deleting - used to keep an in-progress checkout's hold alive.
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>("""
            if redis.call("GET", KEYS[1]) == ARGV[1] then
                return redis.call("PEXPIRE", KEYS[1], ARGV[2])
            else
                return 0
            end
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public SeatLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String lockKey(Long eventId, Long seatId) {
        return "seat-hold:" + eventId + ":" + seatId;
    }

    /**
     * Atomically claims the seat for {@code holderToken} if nobody else
     * currently holds it. The key expires on its own after {@code ttl} -
     * that's the entire "auto return seats to the pool" mechanism, no
     * scheduled sweep required.
     */
    public boolean tryAcquire(Long eventId, Long seatId, String holderToken, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(eventId, seatId), holderToken, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    /** Releases the hold only if {@code holderToken} is the current owner. */
    public boolean release(Long eventId, Long seatId, String holderToken) {
        Long result = redisTemplate.execute(RELEASE_SCRIPT, List.of(lockKey(eventId, seatId)), holderToken);
        return result != null && result == 1L;
    }

    /** Extends the TTL only if {@code holderToken} is the current owner. */
    public boolean renew(Long eventId, Long seatId, String holderToken, Duration ttl) {
        Long result = redisTemplate.execute(
                RENEW_SCRIPT, List.of(lockKey(eventId, seatId)), holderToken, String.valueOf(ttl.toMillis()));
        return result != null && result == 1L;
    }
}
