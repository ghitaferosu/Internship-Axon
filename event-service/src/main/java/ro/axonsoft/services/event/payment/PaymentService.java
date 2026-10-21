package ro.axonsoft.services.event.payment;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.axonsoft.services.event.exception.IdempotencyKeyReusedException;
import ro.axonsoft.services.event.exception.PaymentAmountMismatchException;
import ro.axonsoft.services.event.exception.TicketNotFoundException;
import ro.axonsoft.services.event.hold.SeatHoldService;
import ro.axonsoft.services.event.messaging.PaymentEventMessage;
import ro.axonsoft.services.event.model.Payment;
import ro.axonsoft.services.event.model.Ticket;
import ro.axonsoft.services.event.payment.DTOs.PaymentRequestDTO;
import ro.axonsoft.services.event.payment.DTOs.PaymentResponseDTO;
import ro.axonsoft.services.event.repository.PaymentRepository;
import ro.axonsoft.services.event.repository.TicketRepository;

@Service
public class PaymentService {

    private final SeatHoldService seatHoldService;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher events;

    public PaymentService(SeatHoldService seatHoldService,
                          TicketRepository ticketRepository,
                          PaymentRepository paymentRepository,
                          ApplicationEventPublisher events) {
        this.seatHoldService = seatHoldService;
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.events = events;
    }

    @Transactional
    public PaymentResponseDTO pay(Long eventId, Long seatId, Long userId, PaymentRequestDTO request) {

        // Retrimiterea aceleiasi cereri (buton apasat de doua ori, retry de
        // retea) returneaza plata initiala in loc sa incaseze din nou.
        var existing = paymentRepository.findByPayerIdAndIdempotencyKey(
                userId, request.idempotencyKey());
        Ticket ticket = ticketRepository.findByEvent_IdAndSeat_Id(eventId, seatId)
                .orElseThrow(() -> new TicketNotFoundException(eventId, seatId));

        if(existing.isPresent()) {
            Payment payment = existing.get();
            boolean sameRequest=payment.getTicket().getId().equals(ticket.getId()) && payment.getAmountCents()== request.amountCents();
            if (!sameRequest) {
                throw new IdempotencyKeyReusedException(request.idempotencyKey());
            }
            return PaymentResponseDTO.from(payment);
        }

        if (ticket.getPriceCents() != request.amountCents()) {
            throw new PaymentAmountMismatchException(ticket.getPriceCents(), request.amountCents());
        }

        Ticket sold = seatHoldService.confirm(eventId, seatId, userId);

        Payment payment = paymentRepository.save(
                new Payment(sold, userId, request.amountCents(), request.idempotencyKey()));

        events.publishEvent(PaymentEventMessage.succeeded(eventId, seatId, payment, request));

        return PaymentResponseDTO.from(payment);
    }
}