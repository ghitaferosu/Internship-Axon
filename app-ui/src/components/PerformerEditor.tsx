import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { getPerformers, seedPerformers } from '../api/feedingJob'
import type { PerformerSeedingResponse } from '../types/feedingJob'

const emptyForm = { name: '', genre: '', description: '' }

const inputClass =
    'mt-1.5 w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10'

export default function PerformerEditor() {
    const [form, setForm] = useState(emptyForm)
    const [performers, setPerformers] = useState<PerformerSeedingResponse[]>([])
    const [listError, setListError] = useState<string | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [submitting, setSubmitting] = useState(false)

    function reloadPerformers() {
        getPerformers()
            .then(setPerformers)
            .catch((err: unknown) => setListError(err instanceof Error ? err.message : 'Nu s-au putut încărca artiștii'))
    }

    useEffect(() => {
        reloadPerformers()
    }, [])

    async function handleSubmit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault()
        setSubmitting(true)
        setError(null)
        try {
            // DataFeedingPerformersController seeds in batches; this form submits
            // one performer at a time as a single-element batch.
            await seedPerformers([{ name: form.name.trim(), genre: form.genre.trim(), description: form.description.trim() }])
            setForm(emptyForm)
            reloadPerformers()
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Nu s-a putut crea artistul')
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <form onSubmit={handleSubmit} className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm ring-1 ring-slate-100">
                <h2 className="text-sm font-semibold text-slate-900">Artist / performer nou</h2>

                <label className="mt-4 block text-sm font-medium text-slate-700">
                    Nume
                    <input
                        required
                        value={form.name}
                        onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                        className={inputClass}
                        placeholder="Coldplay"
                    />
                </label>

                <label className="mt-4 block text-sm font-medium text-slate-700">
                    Gen
                    <input
                        required
                        value={form.genre}
                        onChange={(e) => setForm((prev) => ({ ...prev, genre: e.target.value }))}
                        className={inputClass}
                        placeholder="Rock"
                    />
                </label>

                <label className="mt-4 block text-sm font-medium text-slate-700">
                    Descriere
                    <textarea
                        value={form.description}
                        onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
                        className={inputClass}
                        rows={3}
                    />
                </label>

                {error && (
                    <div className="mt-4 rounded-lg bg-red-50 px-3.5 py-2.5 text-sm text-red-700 ring-1 ring-red-100">{error}</div>
                )}

                <button
                    type="submit"
                    disabled={submitting}
                    className="mt-4 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-lg shadow-indigo-600/25 transition hover:bg-indigo-700 disabled:opacity-60"
                >
                    {submitting ? 'Se salvează…' : 'Adaugă artist'}
                </button>
            </form>

            <div>
                <h2 className="text-sm font-semibold text-slate-900">Artiști existenți</h2>
                {listError && <p className="mt-2 text-sm text-red-600">{listError}</p>}
                {!listError && performers.length === 0 && <p className="mt-2 text-sm text-slate-500">Niciunul încă.</p>}
                <ul className="mt-3 space-y-2">
                    {performers.map((performer) => (
                        <li key={performer.id} className="rounded-lg bg-slate-50 p-3 text-sm ring-1 ring-slate-100">
                            <span className="font-mono text-xs text-indigo-600">#{performer.id}</span>{' '}
                            <span className="font-medium text-slate-800">{performer.name}</span>{' '}
                            <span className="text-slate-500">({performer.genre})</span>
                        </li>
                    ))}
                </ul>
            </div>
        </div>
    )
}
