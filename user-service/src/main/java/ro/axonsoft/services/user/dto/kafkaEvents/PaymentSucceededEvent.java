package ro.axonsoft.services.user.dto.kafkaEvents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentSucceededEvent(
        UUID messageId,
        String type,
        Long paymentId,
        Long eventId,
        Long ticketId,
        Long payerId,
        int amountCents,
        UUID orderId,
        Integer orderSize,
        String eventName,
        String venueName,
        String city,
        OffsetDateTime eventStartHour,
        String sectionName,
        int rowNumber,
        int seatNumber
) {

    public String orderKey() {
        return orderId != null ? orderId.toString() : "single:" + messageId;
    }

    public int expectedSize() {
        return orderId != null && orderSize != null && orderSize > 0 ? orderSize : 1;
    }
}
