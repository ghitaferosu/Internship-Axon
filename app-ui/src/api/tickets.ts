import {api} from "./client.ts";

export interface MyTicket {
    ticketId: number
    eventId: number
    seatId: number
    sectionId: number
    row: number
    seatNumber: number
    status: 'AVAILABLE' | 'HELD' | 'SOLD'
    priceCents: number
    heldUntil: string | null
}

export async function getMyTickets(eventId?: number): Promise<MyTicket[]> {
    const res = await api.get<MyTicket[]>('/api/me/tickets', {
        params: eventId != null ? {eventId} : undefined,
    })
    return res.data
}

export interface TicketHistoryEntry {
    ticketId: number
    eventId: number
    eventName: string
    eventStartHour: string
    city: string
    venueName: string
    sectionName: string
    row: number
    seatNumber: number
    status: 'AVAILABLE' | 'HELD' | 'SOLD'
    priceCents: number
    heldUntil: string | null
}
export async function getMyTicketHistory(): Promise<TicketHistoryEntry[]> {
    const res = await api.get<TicketHistoryEntry[]>('/api/me/tickets/history')
    return res.data
}