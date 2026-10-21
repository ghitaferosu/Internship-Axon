package ro.axonsoft.services.event.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "venues", uniqueConstraints = @UniqueConstraint(columnNames = {"name","address"}))
@Getter
@Setter
@NoArgsConstructor
public class Venue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "no_seats",nullable = false)
    private int noSeats;

    public Venue(String name, String address, int noSeats) {
        this.name = name;
        this.address = address;
        this.noSeats = noSeats;
    }


}
