package ro.axonsoft.services.event.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "events",indexes = {
        @Index(name = "idx_events_country",columnList = "country"),
        @Index(name = "idx_events_city",columnList = "city"),
        @Index(name = "idx_events_start_hour",columnList = "event_start_hour")
})
@Getter
@Setter
@NoArgsConstructor
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "venue_id",nullable = false)
    private Venue venue;

    @Column(name = "name",nullable = false)
    private String name;

    @Column(name = "country",nullable = false)
    private String country;

    @Column(name = "city",nullable = false)
    private String city;

    @Column(name = "event_start_hour",nullable = false)
    private OffsetDateTime eventStartHour;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",nullable = false)
    private EventStatus status;

    public Event(Venue venue, String name, String country, String city, OffsetDateTime eventStartHour) {
        this.venue = venue;
        this.name = name;
        this.country = country;
        this.city = city;
        this.eventStartHour = eventStartHour;
        this.status = EventStatus.DRAFT;
    }
}
