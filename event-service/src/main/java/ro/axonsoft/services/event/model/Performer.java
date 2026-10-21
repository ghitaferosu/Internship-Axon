package ro.axonsoft.services.event.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "performers")
@Getter
@Setter
@NoArgsConstructor
public class Performer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name",nullable = false)
    private String name;

    @Column(name = "genre",nullable = false)
    private String genre;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public Performer(String name, String genre, String description) {
        this.name = name;
        this.genre = genre;
        this.description = description;
    }
}
