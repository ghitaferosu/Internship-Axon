package ro.axonsoft.services.event.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sections")
@Getter
@Setter
@NoArgsConstructor
public class Section {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "section_name", nullable = false)
    private String sectionName;

    //Angle (radians) from the stage origin (0,0) to this section's centroid -
    //replaces the old fixed-direction enum, computed by
    //SectionGeometryCalculator from the section's drawn outline polygon.
    @Column(name = "angle", nullable = false)
    private double angle;

    @Column(name = "row_start")
    private int rowStart;

    @Column(name = "row_end")
    private int rowEnd;

    @Column(name = "seat_start")
    private int seatStart;

    @Column(name = "seat_end")
    private int seatEnd;

    //Row-to-row spacing (perpendicular to colSpacing below), populated by
    //SectionGeometryCalculator at seed time. originX/originY stay int
    //(rounded from the calculator's double output) - sub-pixel precision
    //doesn't matter for rendering.
    @Column(name = "row_spacing")
    private int rowSpacing;

    @Column(name = "origin_x")
    private int originX;

    @Column(name = "origin_y")
    private int originY;

    //Seat-to-seat spacing within a row (perpendicular to rowSpacing, which is
    //row-to-row) - independent values so a section's rendered seat grid fills
    //its actual drawn outline instead of one shared spacing getting pulled
    //down to whichever axis is tighter. Populated by SectionGeometryCalculator
    //at seed time, same as rowSpacing.
    @Column(name = "col_spacing")
    private int colSpacing;

    public Section(Venue venue, String sectionName) {
        this.venue = venue;
        this.sectionName = sectionName;
    }
}
