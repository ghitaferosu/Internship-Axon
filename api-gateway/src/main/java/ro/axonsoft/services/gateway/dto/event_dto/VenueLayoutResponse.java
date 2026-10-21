package ro.axonsoft.services.gateway.dto.event_dto;

import java.util.List;

public record VenueLayoutResponse(Long id, String name, String address, int noSeats, List<SectionLayoutResponse> sections) {
}
