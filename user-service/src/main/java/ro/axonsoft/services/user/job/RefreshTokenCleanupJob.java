package ro.axonsoft.services.user.job;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.axonsoft.services.user.repository.RefreshTokenRepo;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepo refreshTokenRepo;

    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Bucharest")
    @Transactional
    public void deleteExpired() {
        int deleted = refreshTokenRepo.deleteExpiredBefore(Instant.now());
        if(deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }
}
