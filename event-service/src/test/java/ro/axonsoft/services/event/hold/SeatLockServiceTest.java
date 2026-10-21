package ro.axonsoft.services.event.hold;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatLockServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private SeatLockService newService() {
        return new SeatLockService(redisTemplate);
    }

    @Test
    void tryAcquireReturnsTrueWhenKeyWasAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("seat-hold:1:10", "holder-A", Duration.ofMinutes(5)))
                .thenReturn(true);

        boolean acquired = newService().tryAcquire(1L, 10L, "holder-A", Duration.ofMinutes(5));

        assertThat(acquired).isTrue();
    }

    @Test
    void tryAcquireReturnsFalseWhenAlreadyHeld() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(false);

        boolean acquired = newService().tryAcquire(1L, 10L, "holder-B", Duration.ofMinutes(5));

        assertThat(acquired).isFalse();
    }

    @Test
    void releaseReturnsTrueWhenScriptReportsOwnerMatch() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("holder-A"))).thenReturn(1L);

        boolean released = newService().release(1L, 10L, "holder-A");

        assertThat(released).isTrue();
    }

    @Test
    void releaseReturnsFalseWhenCallerIsNotTheOwner() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("holder-B"))).thenReturn(0L);

        boolean released = newService().release(1L, 10L, "holder-B");

        assertThat(released).isFalse();
    }

    @Test
    void renewReturnsTrueWhenScriptReportsOwnerMatch() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("holder-A"), any()))
                .thenReturn(1L);

        boolean renewed = newService().renew(1L, 10L, "holder-A", Duration.ofMinutes(5));

        assertThat(renewed).isTrue();
    }

    @Test
    void renewReturnsFalseWhenCallerIsNotTheOwner() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("holder-B"), any()))
                .thenReturn(0L);

        boolean renewed = newService().renew(1L, 10L, "holder-B", Duration.ofMinutes(5));

        assertThat(renewed).isFalse();
    }
}
