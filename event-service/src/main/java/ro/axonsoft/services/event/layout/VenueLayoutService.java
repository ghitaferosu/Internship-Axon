package ro.axonsoft.services.event.layout;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ro.axonsoft.services.event.exception.SectionNotFoundException;
import ro.axonsoft.services.event.exception.VenueNotFoundException;
import ro.axonsoft.services.event.feedingJob.DTOs.PageDTO;
import ro.axonsoft.services.event.layout.DTOs.SeatResponseDTO;
import ro.axonsoft.services.event.layout.DTOs.SectionLayoutDTO;
import ro.axonsoft.services.event.layout.DTOs.VenueLayoutResponseDTO;
import ro.axonsoft.services.event.layout.DTOs.VenueSummaryDTO;
import ro.axonsoft.services.event.layout.SectionGeometryCalculator.Point;
import ro.axonsoft.services.event.model.Section;
import ro.axonsoft.services.event.model.Seat;
import ro.axonsoft.services.event.model.Venue;
import ro.axonsoft.services.event.repository.SeatRepository;
import ro.axonsoft.services.event.repository.SectionRepository;
import ro.axonsoft.services.event.repository.SectionVertexRepository;
import ro.axonsoft.services.event.repository.VenueRepository;

import java.util.List;

// Read-only now: SectionGeometryCalculator is write-time-only (runs once
// inside VenueSeedingService.seedVenue()), so this service just reads back
// whatever got persisted onto Section's columns - no recomputation on every
// cached read (see venue-authoring-rework in project memory).
@Service
public class VenueLayoutService {

    private final VenueRepository venueRepository;
    private final SectionRepository sectionRepository;
    private final SeatRepository seatRepository;
    private final SectionVertexRepository sectionVertexRepository;

    public VenueLayoutService(VenueRepository venueRepository,
                               SectionRepository sectionRepository,
                               SeatRepository seatRepository,
                               SectionVertexRepository sectionVertexRepository) {
        this.venueRepository = venueRepository;
        this.sectionRepository = sectionRepository;
        this.seatRepository = seatRepository;
        this.sectionVertexRepository = sectionVertexRepository;
    }

    // Deliberately not @Cacheable, unlike getLayout below: VenueSeedingService
    // never evicts a cache on save, so a just-seeded venue needs to show up in
    // the admin picker immediately, not after whatever TTL a cached list
    // would carry.
    public List<VenueSummaryDTO> listVenues() {
        return venueRepository.findAllByOrderByIdDesc().stream()
                .map(v -> new VenueSummaryDTO(v.getId(), v.getName(), v.getAddress(), v.getNoSeats()))
                .toList();
    }

    @Cacheable(value = "venueLayout", key = "#venueId", sync = true)
    public VenueLayoutResponseDTO getLayout(Long venueId) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueNotFoundException(venueId));

        List<SectionLayoutDTO> sections = sectionRepository.findByVenueId(venueId).stream()
                .map(this::toSectionLayoutDTO)
                .toList();

        return new VenueLayoutResponseDTO(
                venue.getId(), venue.getName(), venue.getAddress(), venue.getNoSeats(), sections);
    }

    @Cacheable(value = "sectionSeats", key = "#sectionId + '-' + #page + '-' + #size", sync = true)
    public PageDTO<SeatResponseDTO> getSeats(Long sectionId, int page, int size) {
        sectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));

        Page<Seat> seatPage = seatRepository.findBySectionIdOrderByRowAscSeatNumberAsc(
                sectionId, PageRequest.of(page, size));

        List<SeatResponseDTO> content = seatPage.getContent().stream()
                .map(seat -> new SeatResponseDTO(seat.getId(),seat.getRow(), seat.getSeatNumber()))
                .toList();

        return new PageDTO<>(content, page, size, seatPage.getTotalElements(), seatPage.getTotalPages());
    }

    private SectionLayoutDTO toSectionLayoutDTO(Section section) {
        int seatCount = (section.getRowEnd() - section.getRowStart() + 1)
                * (section.getSeatEnd() - section.getSeatStart() + 1);

        List<Point> vertices = sectionVertexRepository
                .findBySectionIdOrderByVertexOrderAsc(section.getId()).stream()
                .map(v -> new Point(v.getX(), v.getY()))
                .toList();

        return new SectionLayoutDTO(
                section.getId(),
                section.getSectionName(),
                section.getAngle(),
                section.getRowStart(),
                section.getRowEnd(),
                section.getSeatStart(),
                section.getSeatEnd(),
                seatCount,
                section.getOriginX(),
                section.getOriginY(),
                section.getRowSpacing(),
                section.getColSpacing(),
                vertices
        );
    }
}
