import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'
import { holdSeat, paySeat, releaseSeat } from '../api/booking'
import { getEventWithSeatMap } from '../api/events'
import { useAuth } from '../auth/useAuth'
import BookingSeatMap, { type LiveSeatStatus, type MineStatus } from '../components/BookingSeatMap'
import { useEventSocket } from '../hooks/useEventSocket'
import type { EventWithSeatMap, SeatWithStatus } from '../types/booking'
import type { SectionLayout } from '../types/venueLayout'
import {getMyTickets} from "../api/tickets.ts";
import { isOnSale, notOnSaleMessage, statusLabel } from '../lib/eventStatus'
import concertBackground from '../assets/concert-crowd.jpg'

const BACKGROUND_STYLE = {
    backgroundImage: `linear-gradient(rgba(10,14,30,0.6), rgba(10,14,30,0.8)), url(${concertBackground})`,
}

// HOLD_TTL on the backend (SeatHoldService) - only used for the countdown's
// starting point if a hold response somehow arrives without heldUntil.
const FALLBACK_HOLD_MS = 5 * 60 * 1000

type CartStatus = 'held' | 'paying' | 'paid' | 'failed'

interface CartEntry {
    seat: SeatWithStatus
    section: SectionLayout
    ticketId: number
    priceCents: number
    heldUntil: string
    idempotencyKey: string
    status: CartStatus
    error?: string
}

function formatPrice(cents: number): string {
    return `${(cents / 100).toFixed(2)} RON`
}

function formatCountdown(msRemaining: number): string {
    const totalSeconds = Math.max(0, Math.ceil(msRemaining / 1000))
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

function formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString('ro-RO', { hour: '2-digit', minute: '2-digit' })
}

const EventBookingPage = () => {
    const { eventId } = useParams<{ eventId: string }>()
    const { userId } = useAuth()
    const [ownedSeatIds, setOwnedSeatIds] = useState<number[]>([])

    const [event, setEvent] = useState<EventWithSeatMap | null>(null)
    const [loadError, setLoadError] = useState<string | null>(null)
    const [cart, setCart] = useState<Map<number, CartEntry>>(new Map())
    const [notice, setNotice] = useState<string | null>(null)
    const [checkingOut, setCheckingOut] = useState(false)
    const [now, setNow] = useState(() => Date.now())
    const [cartOpen, setCartOpen] = useState(true)

    const numericEventId = eventId ? Number(eventId) : NaN
    const { seatStatus } = useEventSocket(Number.isFinite(numericEventId) ? numericEventId : null)

    useEffect(() => {
        if (!eventId) return
        // Fetch-on-mount/param-change: the standard data-loading pattern.
        // react-hooks/set-state-in-effect wants every setState routed through
        // an external callback (it endorses exactly this .then/.catch shape);
        // it still flags the synchronous reset that starts it.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setEvent(null)
        setLoadError(null)
        getEventWithSeatMap(eventId)
            .then(setEvent)
            .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : 'Nu s-a putut încărca evenimentul'))
    }, [eventId])

    useEffect(() => {
        if (userId == null || !event)
            return
        getMyTickets(event.id).then((tickets) => {
            setOwnedSeatIds(tickets.filter((t) => t.status === 'SOLD').map((t) => t.seatId))
            const held = tickets.filter((t) => t.status === 'HELD' && t.heldUntil)
            if (held.length === 0)
                return

            setCart((prev) => {
                const next = new Map(prev)
                held.forEach((t) => {
                    if (next.has(t.seatId))
                        return
                    const section = event.venue.sections.find((s) => s.id === t.sectionId)
                    if(!section)
                        return
                    next.set(t.seatId, { seat: {id: t.seatId, row: t.row, seatNumber: t.seatNumber, status: 'HELD'}, section,
                                       ticketId: t.ticketId,
                                       priceCents: t.priceCents,
                                       heldUntil: t.heldUntil!,
                                       idempotencyKey: crypto.randomUUID(),
                                       status: 'held',})
                })
                return next
            })
        })
            .catch(() => setOwnedSeatIds([]))
    }, [userId, event])


    // Countdown tick, also drives the auto-expiry sweep below.
    useEffect(() => {
        const id = setInterval(() => setNow(Date.now()), 1000)
        return () => clearInterval(id)
    }, [])

    useEffect(() => {
        if (!event) return
        for (const [seatId, entry] of cart) {
            if (entry.status === 'held' && new Date(entry.heldUntil).getTime() <= now) {
                // Reacting to the `now` tick crossing a per-entry deadline -
                // an external-clock-driven update, just expressed via effect
                // + interval state rather than the interval callback itself.
                // eslint-disable-next-line react-hooks/set-state-in-effect
                setCart((prev) => {
                    const next = new Map(prev)
                    next.delete(seatId)
                    return next
                })
                setNotice(`Locul din rândul ${entry.seat.row} a expirat și a revenit în vânzare.`)
                if (userId != null) {
                    // Redis TTL already released the lock server-side; this is
                    // just tidying up the ticket row's HELD status early.
                    releaseSeat(event.id, seatId).catch(() => {})
                }
            }
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [now])

    useEffect(() => {
        if (!notice) return
        const id = setTimeout(() => setNotice(null), 4000)
        return () => clearTimeout(id)
    }, [notice])

    useEffect(() => {
        if (!cartOpen) return
        function onKeyDown(domEvent: KeyboardEvent) {
            if (domEvent.key === 'Escape') setCartOpen(false)
        }
        window.addEventListener('keydown', onKeyDown)
        return () => window.removeEventListener('keydown', onKeyDown)
    }, [cartOpen])

    const liveStatus = useMemo(() => {
        const map = new Map<number, LiveSeatStatus>()
        seatStatus.forEach((message, seatId) =>
            map.set(seatId, { status: message.status, heldUntil: message.heldUntil }),
        )
        return map
    }, [seatStatus])

    const mineStatus = useMemo(() => {
        const map = new Map<number, MineStatus>()
        ownedSeatIds.forEach((seatId) => map.set(seatId, 'paid'))

        cart.forEach((entry, seatId) => {
            map.set(seatId, entry.status === 'paid' ? 'paid' : entry.status === 'paying' ? 'paying' : 'held')
        })
        return map
    }, [ownedSeatIds, cart])

    const cartEntries = useMemo(() => Array.from(cart.values()), [cart])
    const [prevCartSize, setPrevCartSize] = useState(cart.size)
    if (prevCartSize !== cart.size) {
        setPrevCartSize(cart.size)
        if (cart.size > prevCartSize) setCartOpen(true)
    }

    const unpaidCents = cartEntries.filter((entry) => entry.status !=='paid').reduce((sum, entry) => sum + entry.priceCents, 0)
    const allPaid = cartEntries.length > 0 && cartEntries.every((entry) => entry.status === 'paid')
    const canCheckout = !checkingOut && !allPaid && cartEntries.some((entry) => entry.status !== 'paid')

    async function handleSeatClick(seat: SeatWithStatus, section: SectionLayout) {
        if (!event || userId == null || checkingOut) return

        if (!isOnSale(event.status)) {
            setNotice(notOnSaleMessage(event.status))
            return
        }

        const existing = cart.get(seat.id)
        if (existing) {
            if (existing.status !== 'held') return // don't let a paying/paid pick be un-held from the map
            setCart((prev) => {
                const next = new Map(prev)
                next.delete(seat.id)
                return next
            })
            try {
                await releaseSeat(event.id, seat.id)
            } catch (err) {
                setNotice(err instanceof Error ? err.message : 'Nu s-a putut elibera locul')
            }
            return
        }

        try {
            const ticket = await holdSeat(event.id, seat.id)
            setCart((prev) =>
                new Map(prev).set(seat.id, {
                    seat,
                    section,
                    ticketId: ticket.ticketId,
                    priceCents: ticket.priceCents,
                    heldUntil: ticket.heldUntil ?? new Date(Date.now() + FALLBACK_HOLD_MS).toISOString(),
                    idempotencyKey: crypto.randomUUID(),
                    status: 'held',
                }),
            )
        } catch (err) {
            setNotice(err instanceof Error ? err.message : 'Locul tocmai a fost preluat de altcineva')
        }
    }

    async function handleCheckout() {
        if (!event || userId == null) return
        setCheckingOut(true)

        const orderId = crypto.randomUUID()
        const pending = [...cart].filter(([, entry]) => entry.status !== 'paid')
        const orderSize = pending.length

        for (const [seatId, entry] of pending) {
            setCart((prev) => {
                const current = prev.get(seatId)
                if (!current) return prev
                return new Map(prev).set(seatId, { ...current, status: 'paying', error: undefined })
            })
            try {
                await paySeat(event.id, seatId, {
                    amountCents: entry.priceCents,
                    idempotencyKey: entry.idempotencyKey,
                    orderId,
                    orderSize,
                })
                setCart((prev) => {
                    const current = prev.get(seatId)
                    if (!current) return prev
                    return new Map(prev).set(seatId, { ...current, status: 'paid' })
                })
            } catch (err) {
                const message = err instanceof Error ? err.message : 'Plata a eșuat'
                setCart((prev) => {
                    const current = prev.get(seatId)
                    if (!current) return prev
                    return new Map(prev).set(seatId, { ...current, status: 'failed', error: message })
                })
            }
        }

        setCheckingOut(false)
    }

    if (loadError) {
        return (
            <div className="min-h-screen bg-cover bg-center" style={BACKGROUND_STYLE}>
                <div className="mx-auto max-w-2xl px-4 py-10">
                    <p className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 ring-1 ring-red-100">{loadError}</p>
                    <Link to="/events" className="mt-4 inline-block text-sm font-semibold text-indigo-400 hover:text-indigo-300">
                        ← Înapoi la evenimente
                    </Link>
                </div>
            </div>
        )
    }

    if (!event) {
        return (
            <div className="min-h-screen bg-cover bg-center" style={BACKGROUND_STYLE}>
                <div className="mx-auto max-w-2xl px-4 py-10 text-sm text-slate-300">Se încarcă evenimentul…</div>
            </div>
        )
    }

    return (
        <div className="min-h-screen bg-cover bg-center" style={BACKGROUND_STYLE}>
        <div className="mx-auto max-w-6xl px-4 py-8">
            <Link to="/events" className="text-sm font-semibold text-indigo-400 hover:text-indigo-300">
                ← Înapoi la evenimente
            </Link>

            <h1 className="mt-2 text-2xl font-bold tracking-tight text-white">{event.name}</h1>
            <p className="mt-1 text-sm text-slate-300">
                {event.venue.name} — {event.city}, {event.country} ·{' '}
                {new Date(event.eventStartHour).toLocaleString('ro-RO')}
            </p>

            {event.performers.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1.5">
                    {event.performers.map((performer, index) => (
                        <span
                            key={`${performer.performerName}-${index}`}
                            className="rounded-full bg-white/10 px-2.5 py-1 text-xs font-medium text-slate-200 ring-1 ring-white/15"
                        >
                            {performer.performerName} · {formatTime(performer.startHour)}–{formatTime(performer.endHour)}
                        </span>
                    ))}
                </div>
            )}

            {!isOnSale(event.status) && (
                <div className="mt-4 flex items-start gap-3 rounded-xl bg-sky-50 px-4 py-3.5 ring-1 ring-sky-200">
                    <span aria-hidden="true" className="text-lg leading-none">ℹ️</span>
                    <div>
                        <p className="text-sm font-semibold text-sky-900">{statusLabel(event.status)}</p>
                        <p className="mt-0.5 text-sm text-sky-800">{notOnSaleMessage(event.status)}</p>
                        <p className="mt-1.5 text-xs text-sky-700">
                            Poți vedea harta sălii, dar locurile nu pot fi rezervate încă.
                        </p>
                    </div>
                </div>
            )}

            {notice && (
                <div className="mt-4 rounded-lg bg-amber-50 px-4 py-2.5 text-sm text-amber-800 ring-1 ring-amber-100">
                    {notice}
                </div>
            )}

            <div className="mt-6">
                <BookingSeatMap
                    event={event}
                    liveStatus={liveStatus}
                    mineStatus={mineStatus}
                    onSeatClick={handleSeatClick}
                    selectable={isOnSale(event.status)}
                />
            </div>
        </div>

        <aside
            aria-label="Locurile tale"
            aria-hidden={!cartOpen}
            className={`fixed inset-y-0 right-0 z-40 flex w-full max-w-sm flex-col bg-white shadow-2xl transition-transform duration-300 ease-out ${cartOpen ? 'translate-x-0' : 'pointer-events-none translate-x-full'}`}
        >
            <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
                <h2 className="text-sm font-semibold text-slate-900">Locurile tale</h2>
                <button
                    type="button"
                    onClick={() => setCartOpen(false)}
                    aria-label="Închide"
                    className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
                >
                    ✕
                </button>
            </div>

            <div className="flex-1 overflow-y-auto px-5 py-4">
                {cartEntries.length === 0 && (
                    <p className="text-sm text-slate-500">
                        {isOnSale(event.status)
                            ? 'Alege unul sau mai multe locuri disponibile pe hartă.'
                            : 'Vânzarea nu a început încă pentru acest eveniment.'}
                    </p>
                )}

                <ul className="space-y-3">
                    {cartEntries.map((entry) => {
                        const remainingMs = new Date(entry.heldUntil).getTime() - now
                        return (
                            <li key={entry.seat.id} className="rounded-lg bg-slate-50 p-3 text-sm ring-1 ring-slate-100">
                                <div className="flex items-center justify-between">
                                    <span className="font-medium text-slate-800">
                                        {entry.section.name} · rând {entry.seat.row}, loc {entry.seat.seatNumber}
                                    </span>
                                    <span className="text-slate-600">{formatPrice(entry.priceCents)}</span>
                                </div>
                                <div className="mt-1 flex items-center justify-between text-xs">
                                    {entry.status === 'held' && (
                                        <span className="text-amber-600">Rezervat încă {formatCountdown(remainingMs)}</span>
                                    )}
                                    {entry.status === 'paying' && <span className="text-slate-500">Se procesează plata…</span>}
                                    {entry.status === 'paid' && <span className="font-semibold text-emerald-600">Bilet confirmat ✓</span>}
                                    {entry.status === 'failed' && <span className="text-red-600">{entry.error}</span>}
                                </div>
                            </li>
                        )
                    })}
                </ul>
            </div>

            {cartEntries.length > 0 && (
                <div className="border-t border-slate-100 px-5 py-4">
                    {!allPaid && (
                        <div className="flex items-center justify-between text-sm font-semibold text-slate-900">
                            <span>De plată</span>
                            <span>{formatPrice(unpaidCents)}</span>
                        </div>
                    )}

                    {allPaid ? (
                        <p className="mt-4 rounded-lg bg-emerald-50 px-3.5 py-2.5 text-sm text-emerald-700 ring-1 ring-emerald-100">
                            Rezervare finalizată — biletele tale sunt confirmate.
                        </p>
                    ) : (
                        <button
                            type="button"
                            onClick={handleCheckout}
                            disabled={!canCheckout}
                            className="mt-4 w-full rounded-lg bg-indigo-600 py-2.5 text-sm font-semibold text-white shadow-lg shadow-indigo-600/25 transition hover:bg-indigo-700 disabled:opacity-60"
                        >
                            {checkingOut ? 'Se plătește…' : 'Plătește și confirmă'}
                        </button>
                    )}
                </div>
            )}
        </aside>
        </div>
    )
}

export default EventBookingPage
