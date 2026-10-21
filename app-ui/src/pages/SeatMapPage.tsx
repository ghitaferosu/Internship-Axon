import { useEffect, useState } from 'react'
import { getVenueLayout, getVenues } from '../api/venueLayout'
import SeatMap from '../components/SeatMap'
import VenueEditor from '../components/VenueEditor'
import type { VenueLayout, VenueSummary } from '../types/venueLayout'

type Mode = 'view' | 'author'

const SeatMapPage = () => {
    const [mode, setMode] = useState<Mode>('view')
    const [venueId, setVenueId] = useState('')
    const [venues, setVenues] = useState<VenueSummary[]>([])
    const [layout, setLayout] = useState<VenueLayout | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        // Non-fatal: feeds a <datalist> of suggestions, the id field stays a
        // free-text input either way (this page is a raw dev/testing tool -
        // typing an id GET /venues hasn't caught up with yet should still work).
        getVenues()
            .then(setVenues)
            .catch(() => {})
    }, [])

    function loadVenue() {
        setLoading(true)
        setError(null)
        setLayout(null)
        getVenueLayout(venueId)
            .then(setLayout)
            .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load venue'))
            .finally(() => setLoading(false))
    }

    return (
        <div style={{ fontFamily: 'system-ui, sans-serif', maxWidth: 960, margin: '2rem auto', padding: '0 1rem' }}>
            <h1 style={{ fontSize: '1.3rem' }}>event-service — seat map</h1>

            <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
                <button onClick={() => setMode('view')} disabled={mode === 'view'}>
                    View venue
                </button>
                <button onClick={() => setMode('author')} disabled={mode === 'author'}>
                    Author venue
                </button>
            </div>

            {mode === 'view' && (
                <>
                    <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '1rem' }}>
                        <label htmlFor="venueId">Venue ID</label>
                        <input
                            id="venueId"
                            type="text"
                            list="venue-suggestions"
                            value={venueId}
                            onChange={(e) => setVenueId(e.target.value)}
                            placeholder={venues[0] ? String(venues[0].id) : '1'}
                            style={{ width: 160 }}
                        />
                        <datalist id="venue-suggestions">
                            {venues.map((venue) => (
                                <option key={venue.id} value={venue.id}>
                                    {venue.name}
                                </option>
                            ))}
                        </datalist>
                        <button onClick={loadVenue} disabled={loading}>
                            {loading ? 'Loading…' : 'Load'}
                        </button>
                    </div>

                    {error && <p style={{ color: '#e07' }}>{error}</p>}

                    {layout && (
                        <>
                            <p>
                                <strong>{layout.name}</strong> — {layout.address} — {layout.noSeats} seats
                            </p>
                            <SeatMap layout={layout} />
                        </>
                    )}
                </>
            )}

            {mode === 'author' && <VenueEditor />}
        </div>
    )
}

export default SeatMapPage
