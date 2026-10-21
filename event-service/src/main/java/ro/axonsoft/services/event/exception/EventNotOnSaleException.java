package ro.axonsoft.services.event.exception;

import ro.axonsoft.services.event.model.EventStatus;

public class EventNotOnSaleException extends RuntimeException {
    public EventNotOnSaleException(Long eventId, EventStatus status) {
        super("Evenimentul " + eventId + " nu are vânzarea activată (status " + status + ")");
    }
}
