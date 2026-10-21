package ro.axonsoft.services.event.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "events_performers",uniqueConstraints = @UniqueConstraint(
        columnNames = {"event_id","performer_id","start_hour"}
))
@Getter
@Setter
@NoArgsConstructor
public class EventPerformer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "event_id",nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "performer_id",nullable = false)
    private Performer performer;

    @Column(name = "start_hour",nullable = false)
    private OffsetDateTime startHour;

    @Column(name = "end_hour",nullable = false)
    private OffsetDateTime endHour;

    public EventPerformer(Event event, Performer performer, OffsetDateTime startHour, OffsetDateTime endHour){
        this.event = event;
        this.performer = performer;
        this.startHour = startHour;
        this.endHour = endHour;
    }
}

