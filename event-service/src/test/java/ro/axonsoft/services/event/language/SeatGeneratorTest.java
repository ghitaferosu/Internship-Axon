package ro.axonsoft.services.event.language;

import org.junit.jupiter.api.Test;
import ro.axonsoft.services.event.feedingJob.SeatGenerator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeatGeneratorTest {

    private final SeatGenerator generator = new SeatGenerator();

    // Built directly instead of via Lexer/Parser/Validator (deleted along
    // with the DSL) - SeatGenerator only ever depended on VenueAst/
    // SeatedSectionAst, never on the text-parsing pipeline that used to
    // build them, so its tests don't need a script fixture at all.
    private static SeatedSectionAst section(String name, Direction direction,
                                             int rowStart, int rowEnd, int seatStart, int seatEnd) {
        return new SeatedSectionAst(name, direction, rowStart, rowEnd, seatStart, seatEnd, 1, 1);
    }

    @Test
    void generatesRectangularSection() {
        SeatedSectionAst section = section("A", Direction.NORTH, 1, 3, 1, 2);

        List<GeneratedSeat> seats = generator.generate(section);

        assertThat(seats).hasSize(6);
        assertThat(seats).containsExactly(
                new GeneratedSeat(1, 1), new GeneratedSeat(1, 2),
                new GeneratedSeat(2, 1), new GeneratedSeat(2, 2),
                new GeneratedSeat(3, 1), new GeneratedSeat(3, 2)
        );
    }

    @Test
    void generatesTheFullExampleVenue() {
        VenueAst venue = new VenueAst("National Arena", List.of(
                section("A", Direction.NORTH, 1, 30, 1, 40),
                section("B", Direction.SOUTH, 1, 20, 1, 25),
                section("C", Direction.EAST, 1, 15, 1, 20),
                section("D", Direction.WEST, 1, 10, 1, 10)
        ));

        Map<String, List<GeneratedSeat>> seatsBySection = generator.generate(venue);

        assertThat(seatsBySection.keySet()).containsExactly("A", "B", "C", "D");
        assertThat(seatsBySection.get("A")).hasSize(30 * 40);
        assertThat(seatsBySection.get("B")).hasSize(20 * 25);
        assertThat(seatsBySection.get("C")).hasSize(15 * 20);
        assertThat(seatsBySection.get("D")).hasSize(10 * 10);
    }
}
