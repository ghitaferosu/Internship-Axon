package ro.axonsoft.services.event.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tickets", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_id"}), indexes = {
        @Index(name = "idx_tickets_event_status", columnList = "event_id,status"),
        @Index(name = "idx_tickets_owner_id", columnList = "owner_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TicketStatus status;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "held_by")
    private Long heldBy;

    @Column(name = "held_until")
    private OffsetDateTime heldUntil;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Ticket(Event event, Seat seat, int priceCents) {
        this.event = event;
        this.seat = seat;
        this.priceCents = priceCents;
        this.status = TicketStatus.AVAILABLE;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
