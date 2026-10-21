import { useState } from 'react'
import { Link } from 'react-router'
import VenueEditor from '../components/VenueEditor'
import PerformerEditor from '../components/PerformerEditor'
import EventEditor from '../components/EventEditor'

type Tab = 'venues' | 'performers' | 'events'

const TABS: { id: Tab; label: string }[] = [
    { id: 'venues', label: 'Locații (venues)' },
    { id: 'performers', label: 'Artiști' },
    { id: 'events', label: 'Evenimente' },
]

// Single entry point for every "Event data seeding (system)" endpoint
// (DataFeedingVenuesController/DataFeedingPerformersController/
// DataFeedingEventsController) - previously only venue authoring had a UI
// (buried in SeatMapPage's "Author venue" toggle), so creating a performer or
// an event required curl/Postman straight against event-service.
const AdminPage = () => {
    const [tab, setTab] = useState<Tab>('venues')

    return (
        <div className="mx-auto max-w-6xl px-4 py-8">
            <Link to="/events" className="text-sm font-semibold text-indigo-600 hover:text-indigo-700">
                ← Înapoi la evenimente
            </Link>

            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900">Administrare date</h1>
            <p className="mt-1 text-sm text-slate-500">
                Seedează locații, artiști și evenimente direct în event-service - fluxul de "data feeding job" din propunerea
                proiectului.
            </p>

            <div className="mt-6 flex gap-1 border-b border-slate-200">
                {TABS.map(({ id, label }) => (
                    <button
                        key={id}
                        type="button"
                        onClick={() => setTab(id)}
                        className={`rounded-t-lg px-4 py-2 text-sm font-medium transition ${
                            tab === id ? 'border-b-2 border-indigo-600 text-indigo-600' : 'text-slate-500 hover:text-slate-700'
                        }`}
                    >
                        {label}
                    </button>
                ))}
            </div>

            <div className="mt-6">
                {tab === 'venues' && <VenueEditor />}
                {tab === 'performers' && <PerformerEditor />}
                {tab === 'events' && <EventEditor />}
            </div>
        </div>
    )
}

export default AdminPage
