import type { PaymentResponse, TicketResponse } from '../types/booking'
import { postJson } from './client'

// Routed through api-gateway's BookingController. event-service's
// SeatHoldController/PaymentController sit behind its InternalAuthFilter
// (requires X-Internal-Secret) and read the caller's identity off X-User-Id;
// the gateway's FeignConfig interceptors attach both, deriving X-User-Id from
// the caller's verified JWT. A direct browser call (bypassing the gateway)
// has neither and gets a 403 from InternalAuthFilter before it ever reaches
// the controller.

function base(eventId: number, seatId: number): string {
    return `/api/events/${eventId}/seats/${seatId}`
}

// No holderId/payerId anywhere below: the gateway derives the caller's
// identity from their JWT rather than trusting a client-supplied id
// (BookingController takes no body for hold/release, and its PaymentRequest
// is just amountCents + idempotencyKey).
export function holdSeat(eventId: number, seatId: number): Promise<TicketResponse> {
    return postJson<TicketResponse>(`${base(eventId, seatId)}/hold`)
}

export function releaseSeat(eventId: number, seatId: number): Promise<void> {
    return postJson<void>(`${base(eventId, seatId)}/release`)
}

export function paySeat(eventId: number, seatId: number, request: PaymentRequest): Promise<PaymentResponse> {
    return postJson<PaymentResponse>(`${base(eventId, seatId)}/pay`, request)
}

export interface PaymentRequest {
    amountCents: number
    idempotencyKey: string
    orderId: string
    orderSize: number
}
