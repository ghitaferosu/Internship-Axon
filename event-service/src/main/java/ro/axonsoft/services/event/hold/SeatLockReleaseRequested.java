package ro.axonsoft.services.event.hold;


public record SeatLockReleaseRequested(Long eventId, Long seatId, String holderToken) {}