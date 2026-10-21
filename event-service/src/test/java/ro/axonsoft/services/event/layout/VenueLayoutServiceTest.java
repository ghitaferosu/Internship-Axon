package ro.axonsoft.services.event.layout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ro.axonsoft.services.event.exception.SectionNotFoundException;
import ro.axonsoft.services.event.exception.VenueNotFoundException;
import ro.axonsoft.services.event.feedingJob.DTOs.PageDTO;
import ro.axonsoft.services.event.layout.DTOs.SeatResponseDTO;
import ro.axonsoft.services.event.layout.DTOs.SectionLayoutDTO;
import ro.axonsoft.services.event.layout.DTOs.VenueLayoutResponseDTO;
import ro.axonsoft.services.event.layout.DTOs.VenueSummaryDTO;
import ro.axonsoft.services.event.model.Seat;
import ro.axonsoft.services.event.model.Section;
import ro.axonsoft.services.event.model.SectionVertex;
import ro.axonsoft.services.event.model.Venue;
import ro.axonsoft.services.event.repository.SeatRepository;
import ro.axonsoft.services.event.repository.SectionRepository;
import ro.axonsoft.services.event.repository.SectionVertexRepository;
import ro.axonsoft.services.event.repository.VenueRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VenueLayoutServiceTest {

    @Mock
    private VenueRepository venueRepository;
    @Mock
    private SectionRepository sectionRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private SectionVertexRepository sectionVertexRepository;

    private VenueLayoutService newService() {
        return new VenueLayoutService(venueRepository, sectionRepository, seatRepository, sectionVertexRepository);
    }

    private Section section(Long id, String name, double angle) {
        Venue venue = new Venue("National Arena", "123 Main St", 1200);
        venue.setId(1L);
        Section section = new Section(venue, name);
        section.setId(id);
        section.setAngle(angle);
        section.setRowStart(1);
        section.setRowEnd(30);
        section.setSeatStart(1);
        section.setSeatEnd(40);
        section.setOriginX(0);
        section.setOriginY(-150);
        section.setRowSpacing(24);
        return section;
    }

    @Test
    void getLayoutMapsVenueAndSectionsWithPersistedGeometry() {
        Venue venue = new Venue("National Arena", "123 Main St", 1200);
        venue.setId(1L);
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(sectionRepository.findByVenueId(1L)).thenReturn(List.of(section(10L, "A", -Math.PI / 2)));
        when(sectionVertexRepository.findBySectionIdOrderByVertexOrderAsc(10L)).thenReturn(List.of(
                new SectionVertex(10L, 0, -50, -250),
                new SectionVertex(10L, 1, 50, -250),
                new SectionVertex(10L, 2, 0, -150)
        ));

        VenueLayoutResponseDTO response = newService().getLayout(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("National Arena");
        assertThat(response.noSeats()).isEqualTo(1200);
        assertThat(response.sections()).hasSize(1);

        SectionLayoutDTO sectionDTO = response.sections().get(0);
        assertThat(sectionDTO.id()).isEqualTo(10L);
        assertThat(sectionDTO.name()).isEqualTo("A");
        assertThat(sectionDTO.angle()).isEqualTo(-Math.PI / 2);
        assertThat(sectionDTO.seatCount()).isEqualTo(30 * 40);
        assertThat(sectionDTO.originY()).isNegative();
        assertThat(sectionDTO.rowSpacing()).isPositive();
        assertThat(sectionDTO.vertices()).hasSize(3);
        assertThat(sectionDTO.vertices().get(0).x()).isEqualTo(-50);
    }

    @Test
    void listVenuesReturnsEveryVenueNewestFirstAsSummaries() {
        Venue first = new Venue("National Arena", "123 Main St", 1200);
        first.setId(1L);
        Venue second = new Venue("Sala Radio", "Studio 4", 800);
        second.setId(2L);
        // Repository method already orders newest-first (findAllByOrderByIdDesc) -
        // the service just has to preserve that order, not re-sort.
        when(venueRepository.findAllByOrderByIdDesc()).thenReturn(List.of(second, first));

        List<VenueSummaryDTO> result = newService().listVenues();

        assertThat(result).containsExactly(
                new VenueSummaryDTO(2L, "Sala Radio", "Studio 4", 800),
                new VenueSummaryDTO(1L, "National Arena", "123 Main St", 1200));
    }

    @Test
    void getLayoutThrowsWhenVenueMissing() {
        when(venueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().getLayout(99L))
                .isInstanceOf(VenueNotFoundException.class);
    }

    @Test
    void getSeatsReturnsSeatsForTheSection() {
        Section section = section(10L, "A", 0);
        when(sectionRepository.findById(10L)).thenReturn(Optional.of(section));

        Seat firstSeat = new Seat(section, 1, 1);
        firstSeat.setId(101L);
        Seat secondSeat = new Seat(section, 1, 2);
        secondSeat.setId(102L);

        List<Seat> seats = List.of(firstSeat, secondSeat);
        Pageable pageable = PageRequest.of(0, 200);
        when(seatRepository.findBySectionIdOrderByRowAscSeatNumberAsc(10L, pageable))
                .thenReturn(new PageImpl<>(seats, pageable, seats.size()));

        PageDTO<SeatResponseDTO> result = newService().getSeats(10L, 0, 200);

        assertThat(result.content()).containsExactly(new SeatResponseDTO(101L, 1, 1), new SeatResponseDTO(102L, 1, 2));
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void getSeatsThrowsWhenSectionMissing() {
        when(sectionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().getSeats(99L, 0, 200))
                .isInstanceOf(SectionNotFoundException.class);
    }
}

