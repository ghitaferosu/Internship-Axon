import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { searchEvents } from '../api/events'
import { useAuth } from '../auth/useAuth'
import type { EventSearchCriteria, EventSearchPage } from '../types/booking'
import concertBackground from '../assets/concert-crowd.jpg'
import { isOnSale, statusLabel } from '../lib/eventStatus'

const PAGE_SIZE = 9
const MAX_PAGE_DEPTH = 100
const PAGE_WINDOW = 2

function pageItems(current: number, total: number): (number | 'gap')[] {
    const items: (number | 'gap')[] = []
    const from = Math.max(0, current - PAGE_WINDOW)
    const to = Math.min(total - 1, current + PAGE_WINDOW)

    if (from > 0) {
        items.push(0)
        if (from > 1) items.push('gap')
    }
    for (let p = from; p <= to; p += 1) items.push(p)
    if (to < total - 1) {
        if (to < total - 2) items.push('gap')
        items.push(total - 1)
    }
    return items
}

interface FilterValues {
    country: string
    city: string
    dateFrom: string
    dateTo: string
    keyword: string
}

const emptyFilters: FilterValues = { country: '', city: '', dateFrom: '', dateTo: '', keyword: '' }

// <input type="date"> gives yyyy-MM-dd; the backend wants a full
// OffsetDateTime (EventSearchController's @DateTimeFormat(iso = DATE_TIME)).
function toCriteria(values: FilterValues): EventSearchCriteria {
    return {
        country: values.country.trim() || undefined,
        city: values.city.trim() || undefined,
        keyword: values.keyword.trim() || undefined,
        dateFrom: values.dateFrom ? `${values.dateFrom}T00:00:00Z` : undefined,
        dateTo: values.dateTo ? `${values.dateTo}T23:59:59Z` : undefined,
    }
}

const EventsPage = () => {
    const { isAuthenticated, logout, roles } = useAuth()
    const navigate = useNavigate()
    const [filters, setFilters] = useState<FilterValues>(emptyFilters)
    const [page, setPage] = useState(0)
    const [result, setResult] = useState<EventSearchPage | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [loading, setLoading] = useState(false)

    function runSearch(nextPage: number, filterValues: FilterValues = filters) {
        const target = Math.max(0, Math.min(nextPage, MAX_PAGE_DEPTH - 1))
        setLoading(true)
        setError(null)
        searchEvents(toCriteria(filterValues), target, PAGE_SIZE)
            .then((res) => {
                setResult(res)
                setPage(target)
            })
            .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Căutarea a eșuat'))
            .finally(() => setLoading(false))
    }

    useEffect(() => {
        // Initial fetch-on-mount, same shape/rationale as EventBookingPage's
        // load effect - see the disable comment there.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        runSearch(0)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    function handleSubmit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault()
        runSearch(0)
    }

    function handleLogout() {
        logout()
        navigate('/login', { replace: true })
    }

    const reachablePages = result ? Math.min(result.totalPages, MAX_PAGE_DEPTH) : 0

    return (
        <div
            className="min-h-screen bg-cover bg-center"
            style={{ backgroundImage: `linear-gradient(rgba(10,14,30,0.6), rgba(10,14,30,0.8)), url(${concertBackground})` }}
        >
        <div className="mx-auto max-w-5xl px-4 py-8">
            <div className="flex items-center justify-between">
                <h1 className="text-2xl font-bold tracking-tight text-white">Evenimente</h1>
                <div className="flex items-center gap-4">
                    {isAuthenticated && (
                        <Link to="/my-tickets" className="text-sm font-medium text-slate-300 hover:text-white">
                            Biletele mele
                        </Link>
                    )}
                    {roles.includes('ROLE_ORGANIZER') && (<Link to="/admin" className="text-sm font-medium text-slate-300 hover:text-white">
                        Administrare date
                    </Link> )}
                    {isAuthenticated ? (
                        <button type="button" onClick={handleLogout} className="text-sm font-medium text-slate-300 hover:text-white">
                            Deconectare
                        </button>
                    ) : (
                        <Link to="/login" className="text-sm font-medium text-indigo-400 hover:text-indigo-300">
                            Conectează-te
                        </Link>
                    )}
                </div>
            </div>

            <form onSubmit={handleSubmit} className="mt-6 grid grid-cols-2 gap-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm ring-1 ring-slate-100 sm:grid-cols-3 lg:grid-cols-5">
                <Field label="Țară">
                    <input
                        value={filters.country}
                        onChange={(e) => setFilters((prev) => ({ ...prev, country: e.target.value }))}
                        className="mt-1.5 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10"
                        placeholder="Romania"
                    />
                </Field>
                <Field label="Oraș">
                    <input
                        value={filters.city}
                        onChange={(e) => setFilters((prev) => ({ ...prev, city: e.target.value }))}
                        className="mt-1.5 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10"
                        placeholder="Cluj-Napoca"
                    />
                </Field>
                <Field label="De la">
                    <input
                        type="date"
                        value={filters.dateFrom}
                        onChange={(e) => setFilters((prev) => ({ ...prev, dateFrom: e.target.value }))}
                        className="mt-1.5 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10"
                    />
                </Field>
                <Field label="Până la">
                    <input
                        type="date"
                        value={filters.dateTo}
                        onChange={(e) => setFilters((prev) => ({ ...prev, dateTo: e.target.value }))}
                        className="mt-1.5 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10"
                    />
                </Field>
                <Field label="Cuvânt cheie">
                    <input
                        value={filters.keyword}
                        onChange={(e) => setFilters((prev) => ({ ...prev, keyword: e.target.value }))}
                        className="mt-1.5 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10"
                        placeholder="nume artist sau eveniment"
                    />
                </Field>

                <div className="col-span-2 flex items-end gap-2 sm:col-span-3 lg:col-span-5">
                    <button
                        type="submit"
                        disabled={loading}
                        className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-lg shadow-indigo-600/25 transition hover:bg-indigo-700 disabled:opacity-60"
                    >
                        {loading ? 'Se caută…' : 'Caută'}
                    </button>
                    <button
                        type="button"
                        onClick={() => {
                            setFilters(emptyFilters)
                            runSearch(0, emptyFilters)
                        }}
                        className="rounded-lg px-4 py-2 text-sm font-medium text-slate-500 hover:text-slate-700"
                    >
                        Resetează
                    </button>
                </div>
            </form>

            {error && <p className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 ring-1 ring-red-100">{error}</p>}

            {result && result.content.length === 0 && !loading && (
                <p className="mt-8 text-center text-sm text-slate-300">Niciun eveniment găsit.</p>
            )}

            <ul className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {result?.content.map((event) => (
                    <li key={event.id} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm ring-1 ring-slate-100">
                        <p
                            className={`text-xs font-medium uppercase tracking-wide ${
                                isOnSale(event.status) ? 'text-indigo-600' : 'text-slate-400'
                            }`}
                        >
                            {statusLabel(event.status)}
                        </p>
                        <h2 className="mt-1 text-lg font-semibold text-slate-900">{event.name}</h2>
                        {event.performers.length > 0 && (
                            <div className="mt-2 -ml-2 flex flex-wrap gap-1.5">
                                {event.performers.map((name) => (
                                    <span
                                        key={name}
                                        className="rounded-full bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700"
                                    >
                                      {name}
                                    </span>
                                ))}
                            </div>
                        )}
                        <p className="mt-1 text-sm text-slate-500">{event.venueName}</p>
                        <p className="text-sm text-slate-500">
                            {event.city}, {event.country}
                        </p>
                        <p className="mt-1 text-sm text-slate-500">{new Date(event.eventStartHour).toLocaleString('ro-RO')}</p>
                        <Link
                            to={`/events/${event.id}`}
                            className={`mt-4 inline-block text-sm font-semibold ${
                                isOnSale(event.status)
                                    ? 'text-indigo-600 hover:text-indigo-700'
                                    : 'text-slate-500 hover:text-slate-700'
                            }`}
                        >
                            {isOnSale(event.status) ? 'Vezi locurile →' : 'Vezi detaliile sălii →'}
                        </Link>
                    </li>
                ))}
            </ul>

            {result && result.totalElements > 0 && (
                <p className="mt-6 text-center text-xs text-slate-300">
                    {result.totalElements} {result.totalElements === 1 ? 'eveniment găsit' : 'evenimente găsite'} · pagina{' '}
                    {page + 1} din {reachablePages}
                </p>
            )}

            {result && reachablePages > 1 && (
                <nav aria-label="Paginare" className="mt-3 flex flex-wrap items-center justify-center gap-2 text-sm">
                    <button
                        type="button"
                        aria-label="Prima pagină"
                        disabled={page === 0 || loading}
                        onClick={() => runSearch(0)}
                        className="rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 disabled:opacity-40"
                    >
                        «
                    </button>
                    <button
                        type="button"
                        disabled={page === 0 || loading}
                        onClick={() => runSearch(page - 1)}
                        className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 disabled:opacity-40"
                    >
                        ← Anterior
                    </button>

                    {pageItems(page, reachablePages).map((item, index) =>
                        item === 'gap' ? (
                            <span key={`gap-${index}`} className="px-1 text-slate-300">
                                …
                            </span>
                        ) : (
                            <button
                                key={item}
                                type="button"
                                disabled={loading}
                                aria-current={item === page ? 'page' : undefined}
                                onClick={() => runSearch(item)}
                                className={`min-w-9 rounded-lg border px-2.5 py-1.5 font-medium disabled:opacity-40 ${
                                    item === page
                                        ? 'border-indigo-600 bg-indigo-600 text-white'
                                        : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                                }`}
                            >
                                {item + 1}
                            </button>
                        ),
                    )}

                    <button
                        type="button"
                        disabled={page + 1 >= reachablePages || loading}
                        onClick={() => runSearch(page + 1)}
                        className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 disabled:opacity-40"
                    >
                        Următor →
                    </button>
                    <button
                        type="button"
                        aria-label="Ultima pagină"
                        disabled={page + 1 >= reachablePages || loading}
                        onClick={() => runSearch(reachablePages - 1)}
                        className="rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 disabled:opacity-40"
                    >
                        »
                    </button>
                </nav>
            )}

            {result && result.totalPages > MAX_PAGE_DEPTH && (
                <p className="mt-3 text-center text-xs text-slate-300">
                    Se pot răsfoi primele {MAX_PAGE_DEPTH} pagini. Restrânge căutarea cu filtrele de mai sus.
                </p>
            )}
        </div>
        </div>
    )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
    return (
        <label className="block text-sm font-medium text-slate-700">
            {label}
            {children}
        </label>
    )
}

export default EventsPage
