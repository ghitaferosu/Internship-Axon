package ro.axonsoft.services.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.axonsoft.services.event.model.Payment;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPayerIdAndIdempotencyKey(Long payerId, String idempotencyKey);
}
