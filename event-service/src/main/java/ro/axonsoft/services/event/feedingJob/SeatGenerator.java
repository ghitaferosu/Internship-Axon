package ro.axonsoft.services.event.feedingJob;

import org.springframework.stereotype.Component;
import ro.axonsoft.services.event.language.GeneratedSeat;
import ro.axonsoft.services.event.language.SeatedSectionAst;
import ro.axonsoft.services.event.language.VenueAst;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SeatGenerator {

    public Map<String, List<GeneratedSeat>> generate(VenueAst venue) {
        Map<String, List<GeneratedSeat>> seatsBySection = new LinkedHashMap<>();
        for (SeatedSectionAst section : venue.sections()) {
            seatsBySection.put(section.name(), generate(section));
        }
        return seatsBySection;
    }

    public List<GeneratedSeat> generate(SeatedSectionAst section) {
        List<GeneratedSeat> seats = new ArrayList<>();
        for (int row = section.rowStart(); row <= section.rowEnd(); row++) {
            for (int seatNumber = section.seatStart(); seatNumber <= section.seatEnd(); seatNumber++) {
                seats.add(new GeneratedSeat(row, seatNumber));
            }
        }
        return seats;
    }
}
