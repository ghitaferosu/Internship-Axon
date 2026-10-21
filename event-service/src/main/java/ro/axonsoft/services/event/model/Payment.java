package ro.axonsoft.services.event.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"payer_id", "idempotency_key"}),
        indexes = @Index(name = "idx_payments_ticket_id", columnList = "ticket_id"))
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "payer_id", nullable = false)
    private Long payerId;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Payment(Ticket ticket, Long payerId, int amountCents, String idempotencyKey) {
        this.ticket = ticket;
        this.payerId = payerId;
        this.amountCents = amountCents;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.SUCCEEDED;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
