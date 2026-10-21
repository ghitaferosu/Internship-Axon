import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { getMyTicketHistory, type TicketHistoryEntry } from '../api/tickets'
import concertBackground from '../assets/concert-crowd.jpg'

const BACKGROUND_STYLE = {
    backgroundImage: `linear-gradient(rgba(10,14,30,0.6), rgba(10,14,30,0.8)), url(${concertBackground})`,
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

interface EventGroup {
    eventId: number
    eventName: string
    eventStartHour: string
    venueName: string
    city: string
    tickets: TicketHistoryEntry[]
    totalCents: number
}

const MyTicketsPage = () => {
    const [entries, setEntries] = useState<TicketHistoryEntry[] | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [now, setNow] = useState(() => Date.now())

    useEffect(() => {
        getMyTicketHistory().then(setEntries).catch((err: unknown) => setError(err instanceof Error ? err.message : 'Nu s-au putut încărca biletele'))
    }, [])

    useEffect(() => {
        const id = setInterval(() => setNow(Date.now()), 1000)
        return () => clearInterval(id)
    }, [])

    const held = useMemo(() => (entries ?? []).filter(
            (t) => t.status === 'HELD' && t.heldUntil && new Date(t.heldUntil).getTime() > now),
        [entries, now],
    )

    const groups = useMemo(() => {
        const map = new Map<number, EventGroup>()
        const sold = (entries ?? []).filter((t) => t.status === 'SOLD')
        sold.forEach((t) => {
            let group = map.get(t.eventId)
            if (!group) {
                group = {eventId: t.eventId, eventName: t.eventName, eventStartHour: t.eventStartHour, venueName: t.venueName,
                    city: t.city, tickets: [], totalCents: 0,
                }
                map.set(t.eventId, group)
            }
            group.tickets.push(t)
            group.totalCents += t.priceCents
        })
        return Array.from(map.values())
    }, [entries])

    if (error) {
        return (
            <div className="min-h-screen bg-cover bg-center" style={BACKGROUND_STYLE}>
                <div className="mx-auto max-w-2xl px-4 py-10">
                    <p className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 ring-1 ring-red-100">{error}</p>
                </div>
            </div>
        )
    }

    if (entries === null) {
        return (
            <div className="min-h-screen bg-cover bg-center" style={BACKGROUND_STYLE}>
                <div className="mx-auto max-w-2xl px-4 py-10 text-sm text-slate-300">Se încarcă…</div>
            </div>
        )
    }

    return (
        <div className="min-h-screen bg-cover bg-center" style={BACKGROUND_STYLE}>
            <div className="mx-auto max-w-4xl px-4 py-8">
                <div className="flex items-center justify-between">
                    <h1 className="text-2xl font-bold tracking-tight text-white">Biletele mele</h1>
                    <Link to="/events" className="text-sm font-semibold text-indigo-300 hover:text-indigo-200">
                        ← Evenimente
                    </Link>
                </div>

                {held.length === 0 && groups.length === 0 && (
                    <p className="mt-6 text-sm text-slate-300">
                        Nu ai niciun bilet încă.{' '}
                        <Link to="/events" className="font-semibold text-indigo-300 hover:text-indigo-200">
                            Vezi evenimentele
                        </Link>
                    </p>
                )}

                {held.length > 0 && (
                    <section className="mt-6">
                        <h2 className="text-sm font-semibold text-slate-200">Rezervări neplătite</h2>
                        <ul className="mt-3 space-y-3">
                            {held.map((t) => (
                                <li key={t.ticketId} className="rounded-xl border border-amber-200 bg-amber-50 p-4">
                                    <div className="flex items-start justify-between gap-4">
                                        <div>
                                            <p className="font-semibold text-slate-900">{t.eventName}</p>
                                            <p className="text-sm text-slate-600">
                                                {new Date(t.eventStartHour).toLocaleString('ro-RO')} · {t.venueName}, {t.city}
                                            </p>
                                            <p className="mt-1 text-sm text-slate-700">
                                                {t.sectionName} · rând {t.row}, loc {t.seatNumber} · {formatPrice(t.priceCents)}
                                            </p>
                                        </div>
                                        <div className="shrink-0 text-right">
                                            <p className="text-sm font-semibold text-amber-700">
                                                {formatCountdown(new Date(t.heldUntil!).getTime() - now)}
                                            </p>
                                            <Link
                                                to={`/events/${t.eventId}`}
                                                className="mt-2 inline-block rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-700"
                                            >
                                                Finalizează plata
                                            </Link>
                                        </div>
                                    </div>
                                </li>
                            ))}
                        </ul>
                    </section>
                )}

                {groups.length > 0 && (
                    <section className="mt-8">
                        <h2 className="text-sm font-semibold text-slate-200">Bilete cumpărate</h2>
                        <div className="mt-3 space-y-4">
                            {groups.map((g) => (
                                <article
                                    key={g.eventId}
                                    className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
                                >
                                    <div className="flex items-start justify-between gap-4">
                                        <div>
                                            <Link
                                                to={`/events/${g.eventId}`}
                                                className="font-semibold text-slate-900 hover:text-indigo-600"
                                            >
                                                {g.eventName}
                                            </Link>
                                            <p className="text-sm text-slate-500">
                                                {new Date(g.eventStartHour).toLocaleString('ro-RO')} · {g.venueName}, {g.city}
                                            </p>
                                        </div>
                                        <span className="shrink-0 text-sm font-semibold text-slate-900">
                                            {formatPrice(g.totalCents)}
                                        </span>
                                    </div>
                                    <ul className="mt-3 divide-y divide-slate-100">
                                        {g.tickets.map((t) => (
                                            <li
                                                key={t.ticketId}
                                                className="flex items-center justify-between py-2 text-sm"
                                            >
                                                <span className="text-slate-700">
                                                    {t.sectionName} · rând {t.row}, loc {t.seatNumber}
                                                </span>
                                                <span className="text-slate-500">{formatPrice(t.priceCents)}</span>
                                            </li>
                                        ))}
                                    </ul>
                                </article>
                            ))}
                        </div>
                    </section>
                )}
            </div>
        </div>
    )
}



export default MyTicketsPage


