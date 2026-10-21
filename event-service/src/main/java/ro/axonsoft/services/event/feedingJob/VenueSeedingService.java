package ro.axonsoft.services.event.feedingJob;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.axonsoft.services.event.feedingJob.DTOs.SectionDrawRequestDTO;
import ro.axonsoft.services.event.feedingJob.DTOs.SectionSeedingRequestDTO;
import ro.axonsoft.services.event.feedingJob.DTOs.SectionSeedingResponseDTO;
import ro.axonsoft.services.event.feedingJob.DTOs.VenueDrawRequestDTO;
import ro.axonsoft.services.event.feedingJob.DTOs.VenueSeedingRequestDTO;
import ro.axonsoft.services.event.feedingJob.DTOs.VenueSeedingResponseDTO;
import ro.axonsoft.services.event.language.Direction;
import ro.axonsoft.services.event.language.GeneratedSeat;
import ro.axonsoft.services.event.language.SeatedSectionAst;
import ro.axonsoft.services.event.layout.SectionGeometryCalculator;
import ro.axonsoft.services.event.layout.SectionGeometryCalculator.Point;
import ro.axonsoft.services.event.layout.SectionGeometryCalculator.SectionGeometry;
import ro.axonsoft.services.event.model.Seat;
import ro.axonsoft.services.event.model.Section;
import ro.axonsoft.services.event.model.SectionVertex;
import ro.axonsoft.services.event.model.Venue;
import ro.axonsoft.services.event.repository.SeatRepository;
import ro.axonsoft.services.event.repository.SectionRepository;
import ro.axonsoft.services.event.repository.SectionVertexRepository;
import ro.axonsoft.services.event.repository.VenueRepository;

import java.util.ArrayList;
import java.util.List;

// Rebuilt against the drawn-polygon request contract (see
// venue-authoring-rework in project memory) - the DSL entry point
// (Lexer/Parser/Validator) is gone, replaced by SectionGeometryCalculator
// turning each section's drawn points into placement geometry.
// VenueAst/SeatedSectionAst/SeatGenerator/GeneratedSeat survive unchanged -
// only the text-parsing half was dropped - so seedVenue() still ends with
// SeatGenerator expanding rows/seats, it just gets there via computed
// geometry instead of Lexer/Parser/Validator.
@Service
public class VenueSeedingService {

    private final VenueRepository venueRepository;
    private final SectionRepository sectionRepository;
    private final SeatRepository seatRepository;
    private final SectionVertexRepository sectionVertexRepository;
    private final SeatGenerator seatGenerator;
    private final SectionGeometryCalculator geometryCalculator;

    public VenueSeedingService(VenueRepository venueRepository,
                                SectionRepository sectionRepository,
                                SeatRepository seatRepository,
                                SectionVertexRepository sectionVertexRepository,
                                SeatGenerator seatGenerator,
                                SectionGeometryCalculator geometryCalculator) {
        this.venueRepository = venueRepository;
        this.sectionRepository = sectionRepository;
        this.seatRepository = seatRepository;
        this.sectionVertexRepository = sectionVertexRepository;
        this.seatGenerator = seatGenerator;
        this.geometryCalculator = geometryCalculator;
    }

    // Wire-facing entry point: runs SectionGeometryCalculator over each
    // section's raw drawn points, then delegates to the pure-persistence
    // overload below. Kept separate so persistence can be tested against
    // hand-crafted geometry, without needing real polygon fixtures.
    @Transactional
    public VenueSeedingResponseDTO seedVenue(VenueDrawRequestDTO request) {
        return seedVenue(toSeedingRequest(request));
    }

    @Transactional
    public VenueSeedingResponseDTO seedVenue(VenueSeedingRequestDTO request) {
        Venue venue = venueRepository.save(new Venue(request.name(), request.address(), 0));

        List<SectionSeedingResponseDTO> sectionResponses = new ArrayList<>();
        int totalSeats = 0;

        for (SectionSeedingRequestDTO sectionRequest : request.sections()) {
            Section section = new Section(venue, sectionRequest.name());
            section.setAngle(sectionRequest.angle());
            section.setOriginX((int) Math.round(sectionRequest.originX()));
            section.setOriginY((int) Math.round(sectionRequest.originY()));
            section.setRowSpacing(sectionRequest.rowSpacing());
            section.setColSpacing(sectionRequest.colSpacing());
            section.setRowStart(sectionRequest.rowStart());
            section.setRowEnd(sectionRequest.rowEnd());
            section.setSeatStart(sectionRequest.seatStart());
            section.setSeatEnd(sectionRequest.seatEnd());
            section = sectionRepository.save(section);

            sectionVertexRepository.saveAll(toVertices(section.getId(), sectionRequest));

            List<Seat> seats = generateSeats(section, sectionRequest);
            seatRepository.saveAll(seats);
            totalSeats += seats.size();

            sectionResponses.add(new SectionSeedingResponseDTO(
                    section.getId(),
                    sectionRequest.name(),
                    sectionRequest.angle(),
                    sectionRequest.originX(),
                    sectionRequest.originY(),
                    sectionRequest.rowSpacing(),
                    sectionRequest.colSpacing(),
                    sectionRequest.rowStart(),
                    sectionRequest.rowEnd(),
                    sectionRequest.seatStart(),
                    sectionRequest.seatEnd(),
                    sectionRequest.vertices()
            ));
        }

        venue.setNoSeats(totalSeats);
        venue = venueRepository.save(venue);

        return new VenueSeedingResponseDTO(
                venue.getId(), venue.getName(), venue.getAddress(), venue.getNoSeats(), sectionResponses);
    }

    private VenueSeedingRequestDTO toSeedingRequest(VenueDrawRequestDTO request) {
        List<SectionSeedingRequestDTO> sections = request.sections().stream()
                .map(this::toSeedingRequest)
                .toList();
        return new VenueSeedingRequestDTO(request.name(), request.address(), sections);
    }

    private SectionSeedingRequestDTO toSeedingRequest(SectionDrawRequestDTO sectionDraw) {
        SectionGeometry geometry = geometryCalculator.compute(
                sectionDraw.points(), sectionDraw.rows(), sectionDraw.seatsPerRow(), sectionDraw.angle());
        return new SectionSeedingRequestDTO(
                sectionDraw.name(),
                geometry.angle(),
                geometry.originX(),
                geometry.originY(),
                geometry.rowSpacing(),
                geometry.colSpacing(),
                geometry.rowStart(),
                geometry.rowEnd(),
                geometry.seatStart(),
                geometry.seatEnd(),
                sectionDraw.points()
        );
    }

    private List<SectionVertex> toVertices(Long sectionId, SectionSeedingRequestDTO sectionRequest) {
        List<Point> points = sectionRequest.vertices();
        List<SectionVertex> vertices = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            vertices.add(new SectionVertex(sectionId, i, points.get(i).x(), points.get(i).y()));
        }
        return vertices;
    }

    private List<Seat> generateSeats(Section section, SectionSeedingRequestDTO sectionRequest) {
        // Direction/line/col are DSL leftovers SeatGenerator never actually
        // reads - only the row/seat ranges matter (see SeatedSectionAst).
        SeatedSectionAst ast = new SeatedSectionAst(
                sectionRequest.name(), Direction.NORTH,
                sectionRequest.rowStart(), sectionRequest.rowEnd(),
                sectionRequest.seatStart(), sectionRequest.seatEnd(),
                0, 0);
        List<GeneratedSeat> generatedSeats = seatGenerator.generate(ast);
        return generatedSeats.stream()
                .map(gs -> new Seat(section, gs.row(), gs.seatNumber()))
                .toList();
    }
}
