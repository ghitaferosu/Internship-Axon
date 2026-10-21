package ro.axonsoft.services.event.layout.DTOs;

// Lightweight venue picker row (id/name/address/noSeats only) - deliberately
// not VenueLayoutResponseDTO, which nests every section's geometry and would
// make listing every venue O(sections) instead of O(venues).
public record VenueSummaryDTO(Long id, String name, String address, int noSeats) {}
