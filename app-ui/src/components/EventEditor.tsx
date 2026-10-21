import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { getPerformers, seedEvents } from '../api/feedingJob'
import { getVenues } from '../api/venueLayout'
import DateTimePicker from './DateTimePicker'
import type { EventPerformersSeedingResponse, PerformerAppearanceSeedingRequest, PerformerSeedingResponse } from '../types/feedingJob'
import type { VenueSummary } from '../types/venueLayout'

interface AppearanceDraft {
    key: number
    performerId: string
    startHour: string
    endHour: string
}

let nextAppearanceKey = 1
function emptyAppearance(): AppearanceDraft {
    return { key: nextAppearanceKey++, performerId: '', startHour: '', endHour: '' }
}

const inputClass =
    'mt-1.5 w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10'

// <input type="datetime-local"> gives "YYYY-MM-DDTHH:mm", no timezone - the
// backend wants a full OffsetDateTime. The picked value is wall-clock time in
// the organizer's own zone, so it has to be resolved against that zone (which
// is what `new Date` does for an offset-less date-time) rather than stamped
// with a Z it never had.
function toOffsetDateTime(localValue: string): string {
    return new Date(localValue).toISOString()
}

export default function EventEditor() {
    const [venueId, setVenueId] = useState('')
    const [name, setName] = useState('')
    const [country, setCountry] = useState('')
    const [city, setCity] = useState('')
    const [eventStartHour, setEventStartHour] = useState('')
    const [priceRon, setPriceRon] = useState('')
    const [appearances, setAppearances] = useState<AppearanceDraft[]>([])
    const [created, setCreated] = useState<EventPerformersSeedingResponse[]>([])
    const [venues, setVenues] = useState<VenueSummary[]>([])
    const [performers, setPerformers] = useState<PerformerSeedingResponse[]>([])
    const [pickerError, setPickerError] = useState<string | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        Promise.all([getVenues(), getPerformers()])
            .then(([venueList, performerList]) => {
                setVenues(venueList)
                setPerformers(performerList)
            })
            .catch((err: unknown) => setPickerError(err instanceof Error ? err.message : 'Nu s-au putut încărca locațiile/artiștii'))
    }, [])

    function addAppearance() {
        setAppearances((prev) => [...prev, emptyAppearance()])
    }

    function updateAppearance(key: number, patch: Partial<AppearanceDraft>) {
        setAppearances((prev) => prev.map((a) => (a.key === key ? { ...a, ...patch } : a)))
    }

    function removeAppearance(key: number) {
        setAppearances((prev) => prev.filter((a) => a.key !== key))
    }

    async function handleSubmit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault()
        // DateTimePicker isn't a native form control, so HTML5 `required`
        // doesn't cover it - checked explicitly here instead of letting an
        // empty value reach the backend as an unparseable OffsetDateTime.
        if (!eventStartHour) {
            setError('Data și ora evenimentului sunt obligatorii')
            return
        }
        setSubmitting(true)
        setError(null)
        try {
            const performerAppearances: PerformerAppearanceSeedingRequest[] = appearances
                .filter((a) => a.performerId && a.startHour && a.endHour)
                .map((a) => ({
                    performerId: Number(a.performerId),
                    startHour: toOffsetDateTime(a.startHour),
                    endHour: toOffsetDateTime(a.endHour),
                }))

            // DataFeedingEventsController seeds in batches (and materializes one
            // ticket row per seat per event synchronously - see EventSeedingService);
            // this form submits one event at a time as a single-element batch.
            const [saved] = await seedEvents([
                {
                    venueId: Number(venueId),
                    name: name.trim(),
                    country: country.trim(),
                    city: city.trim(),
                    eventStartHour: toOffsetDateTime(eventStartHour),
                    basePriceCents: Math.round(Number(priceRon) * 100),
                    performers: performerAppearances,
                },
            ])
            setCreated((prev) => [saved, ...prev])
            setName('')
            setCountry('')
            setCity('')
            setEventStartHour('')
            setPriceRon('')
            setAppearances([])
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Nu s-a putut crea evenimentul')
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <form onSubmit={handleSubmit} className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm ring-1 ring-slate-100">
                <h2 className="text-sm font-semibold text-slate-900">Eveniment nou</h2>
                <p className="mt-1 text-xs text-slate-500">
                    Materializează un bilet pentru fiecare loc din venue imediat ce se salvează (poate dura pentru locații mari).
                </p>

                {pickerError && (
                    <div className="mt-4 rounded-lg bg-red-50 px-3.5 py-2.5 text-sm text-red-700 ring-1 ring-red-100">{pickerError}</div>
                )}

                <label className="mt-4 block text-sm font-medium text-slate-700">
                    Venue
                    <select required value={venueId} onChange={(e) => setVenueId(e.target.value)} className={inputClass}>
                        <option value="" disabled>
                            {venues.length === 0 ? 'Niciun venue disponibil - creează unul în tab-ul Locații' : 'Alege un venue'}
                        </option>
                        {venues.map((venue) => (
                            <option key={venue.id} value={venue.id}>
                                #{venue.id} - {venue.name} ({venue.noSeats} locuri)
                            </option>
                        ))}
                    </select>
                </label>

                <label className="mt-4 block text-sm font-medium text-slate-700">
                    Nume eveniment
                    <input required value={name} onChange={(e) => setName(e.target.value)} className={inputClass} placeholder="Rock Fest" />
                </label>

                <div className="mt-4 grid grid-cols-2 gap-3">
                    <label className="block text-sm font-medium text-slate-700">
                        Țară
                        <input required value={country} onChange={(e) => setCountry(e.target.value)} className={inputClass} />
                    </label>
                    <label className="block text-sm font-medium text-slate-700">
                        Oraș
                        <input required value={city} onChange={(e) => setCity(e.target.value)} className={inputClass} />
                    </label>
                </div>

                <div className="mt-4 grid grid-cols-2 gap-3">
                    <label className="block text-sm font-medium text-slate-700">
                        Data și ora
                        <DateTimePicker value={eventStartHour} onChange={setEventStartHour} />
                    </label>
                    <label className="block text-sm font-medium text-slate-700">
                        Preț de bază (RON)
                        <input
                            required
                            type="number"
                            min="0"
                            step="0.01"
                            value={priceRon}
                            onChange={(e) => setPriceRon(e.target.value)}
                            className={inputClass}
                        />
                    </label>
                </div>

                <div className="mt-5 border-t border-slate-100 pt-4">
                    <div className="flex items-center justify-between">
                        <h3 className="text-sm font-medium text-slate-700">Apariții artiști (opțional)</h3>
                        <button type="button" onClick={addAppearance} className="text-sm font-semibold text-indigo-600 hover:text-indigo-700">
                            + Adaugă
                        </button>
                    </div>

                    {appearances.map((a) => (
                        <div key={a.key} className="mt-3 grid grid-cols-[1fr_1fr_1fr_auto] items-end gap-2">
                            <label className="block text-xs font-medium text-slate-700">
                                Artist
                                <select
                                    value={a.performerId}
                                    onChange={(e) => updateAppearance(a.key, { performerId: e.target.value })}
                                    className={inputClass}
                                >
                                    <option value="" disabled>
                                        {performers.length === 0 ? 'Niciunul disponibil' : 'Alege'}
                                    </option>
                                    {performers.map((performer) => (
                                        <option key={performer.id} value={performer.id}>
                                            #{performer.id} - {performer.name}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label className="block text-xs font-medium text-slate-700">
                                Start
                                <DateTimePicker value={a.startHour} onChange={(value) => updateAppearance(a.key, { startHour: value })} />
                            </label>
                            <label className="block text-xs font-medium text-slate-700">
                                Sfârșit
                                <DateTimePicker value={a.endHour} onChange={(value) => updateAppearance(a.key, { endHour: value })} />
                            </label>
                            <button
                                type="button"
                                onClick={() => removeAppearance(a.key)}
                                className="mb-0.5 rounded-lg px-2 py-2.5 text-sm text-red-600 hover:bg-red-50"
                            >
                                ✕
                            </button>
                        </div>
                    ))}
                </div>

                {error && (
                    <div className="mt-4 rounded-lg bg-red-50 px-3.5 py-2.5 text-sm text-red-700 ring-1 ring-red-100">{error}</div>
                )}

                <button
                    type="submit"
                    disabled={submitting}
                    className="mt-5 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-lg shadow-indigo-600/25 transition hover:bg-indigo-700 disabled:opacity-60"
                >
                    {submitting ? 'Se salvează…' : 'Creează eveniment'}
                </button>
            </form>

            <div>
                <h2 className="text-sm font-semibold text-slate-900">Evenimente create în această sesiune</h2>
                {created.length === 0 && <p className="mt-2 text-sm text-slate-500">Niciunul încă.</p>}
                <ul className="mt-3 space-y-2">
                    {created.map((event) => (
                        <li key={event.id} className="rounded-lg bg-slate-50 p-3 text-sm ring-1 ring-slate-100">
                            <div className="flex items-center justify-between">
                                <span>
                                    <span className="font-mono text-xs text-indigo-600">#{event.id}</span>{' '}
                                    <span className="font-medium text-slate-800">{event.name}</span>{' '}
                                    <span className="text-slate-500">({event.status})</span>
                                </span>
                                <Link to={`/events/${event.id}`} className="text-sm font-semibold text-indigo-600 hover:text-indigo-700">
                                    Rezervă →
                                </Link>
                            </div>
                        </li>
                    ))}
                </ul>
            </div>
        </div>
    )
}
