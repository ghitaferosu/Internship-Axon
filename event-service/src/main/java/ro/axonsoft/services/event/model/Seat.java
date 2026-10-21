package ro.axonsoft.services.event.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(columnNames = {"section_id", "row_number", "seat_number"}))
@Getter
@Setter
@NoArgsConstructor
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(name = "row_number", nullable = false)
    private int row;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    public Seat(Section section, int row, int seatNumber) {
        this.section = section;
        this.row = row;
        this.seatNumber = seatNumber;
    }
}
