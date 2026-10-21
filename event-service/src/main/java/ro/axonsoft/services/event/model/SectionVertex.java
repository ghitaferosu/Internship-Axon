package ro.axonsoft.services.event.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//One point of a section's drawn outline polygon. Deliberately not a JPA
//relationship on Section (no @ManyToOne back-reference here, no @OneToMany
//on Section) - this is a plain FK by section_id, kept fully decoupled so
//loading a Section for seat/ticket purposes never has to touch this table.
//Order matters (it's a polygon, not a set of points), hence vertexOrder.
@Entity
@Table(name = "section_vertices")
@Getter
@Setter
@NoArgsConstructor
public class SectionVertex {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "vertex_order", nullable = false)
    private int vertexOrder;

    @Column(name = "x", nullable = false)
    private double x;

    @Column(name = "y", nullable = false)
    private double y;

    public SectionVertex(Long sectionId, int vertexOrder, double x, double y) {
        this.sectionId = sectionId;
        this.vertexOrder = vertexOrder;
        this.x = x;
        this.y = y;
    }
}
