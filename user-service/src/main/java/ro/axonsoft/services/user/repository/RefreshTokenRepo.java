package ro.axonsoft.services.user.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ro.axonsoft.services.user.model.RefreshToken;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepo extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash=:tokenHash")
    Optional<RefreshToken> findByTokenHashWithLock(@Param("tokenHash")String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt=:now WHERE r.user.id=:userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt<:now")
    int deleteExpiredBefore(@Param("now") Instant now);
}
