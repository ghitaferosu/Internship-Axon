import type { EventStatus } from '../types/booking'

export function isOnSale(status: EventStatus): boolean {
    return status === 'LIVE'
}

const STATUS_LABEL: Record<EventStatus, string> = {
    DRAFT: 'DRAFT',
    SETTING_UP: 'SETTING UP',
    LIVE: 'LIVE',
}

export function statusLabel(status: EventStatus): string {
    return STATUS_LABEL[status] ?? status
}

const NOT_ON_SALE_MESSAGE: Record<EventStatus, string> = {
    DRAFT: 'Acest eveniment este încă în pregătire, iar biletele nu au fost puse în vânzare. Revino mai târziu.',
    SETTING_UP: 'Se pregătesc locurile pentru acest eveniment. Biletele vor fi disponibile în scurt timp.',
    LIVE: '',
}

export function notOnSaleMessage(status: EventStatus): string {
    return NOT_ON_SALE_MESSAGE[status] ?? 'Biletele pentru acest eveniment nu sunt încă disponibile.'
}
