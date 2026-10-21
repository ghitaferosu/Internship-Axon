import { useState } from 'react';
import { getVenueLayout } from './api.js';
import SeatMap from './SeatMap.jsx';
import VenueEditor from './VenueEditor.jsx';

export default function App() {
  const [mode, setMode] = useState('view'); // 'view' | 'author'
  const [venueId, setVenueId] = useState('1');
  const [layout, setLayout] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  function loadVenue() {
    setLoading(true);
    setError(null);
    setLayout(null);
    getVenueLayout(venueId)
      .then(setLayout)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
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
              value={venueId}
              onChange={(e) => setVenueId(e.target.value)}
              style={{ width: 80 }}
            />
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
  );
}
