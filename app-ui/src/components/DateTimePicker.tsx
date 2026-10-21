import { useEffect, useRef, useState } from 'react'
import { DayPicker } from 'react-day-picker'
import { ro } from 'react-day-picker/locale'

// Value/onChange contract deliberately matches <input type="datetime-local">
// exactly ("YYYY-MM-DDTHH:mm", local time, no seconds/offset) - this is a
// drop-in replacement for that native input, not a new data shape, so
// EventEditor's toOffsetDateTime()/submit logic needs no changes at all.

function pad2(n: number): string {
    return String(n).padStart(2, '0')
}

// "YYYY-MM-DDTHH:mm" from a Date's *local* fields - toISOString() is UTC and
// would silently shift the value, which is exactly the bug a hand-rolled
// formatter here needs to avoid.
function toLocalValue(date: Date): string {
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

// "YYYY-MM-DDTHH:mm" parses reliably as local time in every modern browser
// (no trailing "Z"/offset = local, per the ISO 8601 subset Date() accepts) -
// the same assumption the native datetime-local input's own value already
// relied on.
function parseValue(value: string): Date | null {
    if (!value) return null
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? null : parsed
}

function formatDisplay(date: Date): string {
    return date.toLocaleString('ro-RO', { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const triggerClass =
    'mt-1.5 flex w-full items-center justify-between rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-left text-sm text-slate-900 outline-none transition hover:border-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10'

// Tailwind classNames for react-day-picker's parts - it ships unstyled-ish
// (a minimal reset only, no import of its own style.css here), so every part
// worth styling gets a Tailwind class matching the rest of this form's look.
const dayPickerClassNames = {
    months: 'flex',
    month: 'relative space-y-2',
    month_caption: 'flex items-center justify-center pt-1 text-sm font-semibold text-slate-900',
    nav: 'flex items-center justify-between absolute inset-x-1 top-1',
    button_previous: 'rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-700',
    button_next: 'rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-700',
    chevron: 'h-4 w-4 fill-current',
    month_grid: 'mt-2 w-full border-collapse',
    weekdays: 'flex',
    weekday: 'w-8 text-xs font-medium text-slate-400',
    week: 'mt-0.5 flex',
    day: 'p-0 text-center',
    day_button: 'flex h-8 w-8 items-center justify-center rounded-full text-sm text-slate-700 transition hover:bg-slate-100',
    today: '[&>button]:font-semibold [&>button]:text-indigo-600 [&>button]:ring-1 [&>button]:ring-inset [&>button]:ring-indigo-200',
    selected: '[&>button]:bg-indigo-600 [&>button]:font-semibold [&>button]:text-white [&>button]:hover:bg-indigo-600',
    outside: '[&>button]:text-slate-300',
    disabled: '[&>button]:text-slate-200 [&>button]:hover:bg-transparent',
}

export interface DateTimePickerProps {
    value: string
    onChange: (value: string) => void
    placeholder?: string
}

// Not a native form control, so HTML5 `required` constraint validation
// doesn't apply here - callers that need a required date/time should check
// `value` themselves before submitting (EventEditor does, with a proper
// error message, rather than relying on the browser to scroll to/highlight
// an invisible mirror input, which was a genuinely worse experience).
export default function DateTimePicker({ value, onChange, placeholder = 'Alege data și ora' }: DateTimePickerProps) {
    const [open, setOpen] = useState(false)
    const selected = parseValue(value)
    const [viewMonth, setViewMonth] = useState(() => selected ?? new Date())
    const containerRef = useRef<HTMLDivElement | null>(null)

    // Click-outside closes the popover, same pattern as any other dropdown -
    // not reacting to clicks on the trigger itself, which has its own toggle.
    useEffect(() => {
        if (!open) return undefined
        function onMouseDown(event: MouseEvent) {
            if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
                setOpen(false)
            }
        }
        document.addEventListener('mousedown', onMouseDown)
        return () => document.removeEventListener('mousedown', onMouseDown)
    }, [open])

    function openPicker() {
        setViewMonth(selected ?? new Date())
        setOpen(true)
    }

    function pickDay(day: Date) {
        // Keep the existing time-of-day (default noon for a brand new value) -
        // clicking a day shouldn't reset whatever hour/minute is already set.
        const base = selected ?? new Date(day.getFullYear(), day.getMonth(), day.getDate(), 12, 0)
        const next = new Date(day.getFullYear(), day.getMonth(), day.getDate(), base.getHours(), base.getMinutes())
        onChange(toLocalValue(next))
    }

    function setHour(hour: number) {
        const base = selected ?? new Date()
        onChange(toLocalValue(new Date(base.getFullYear(), base.getMonth(), base.getDate(), hour, base.getMinutes())))
    }

    function setMinute(minute: number) {
        const base = selected ?? new Date()
        onChange(toLocalValue(new Date(base.getFullYear(), base.getMonth(), base.getDate(), base.getHours(), minute)))
    }

    return (
        <div className="relative" ref={containerRef}>
            <button type="button" onClick={openPicker} className={triggerClass}>
                <span className={selected ? '' : 'text-slate-400'}>{selected ? formatDisplay(selected) : placeholder}</span>
                <svg className="h-4 w-4 shrink-0 text-slate-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path
                        fillRule="evenodd"
                        d="M6 2a1 1 0 0 1 1 1v1h6V3a1 1 0 1 1 2 0v1h1a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h1V3a1 1 0 0 1 1-1Zm10 6H4v8h12V8Z"
                        clipRule="evenodd"
                    />
                </svg>
            </button>

            {open && (
                <div className="absolute z-20 mt-1.5 w-72 rounded-xl border border-slate-200 bg-white p-3 shadow-lg ring-1 ring-slate-100">
                    <DayPicker
                        mode="single"
                        locale={ro}
                        selected={selected ?? undefined}
                        onSelect={(day) => day && pickDay(day)}
                        month={viewMonth}
                        onMonthChange={setViewMonth}
                        showOutsideDays
                        weekStartsOn={1}
                        classNames={dayPickerClassNames}
                    />

                    <div className="mt-3 flex items-center justify-between border-t border-slate-100 pt-3">
                        <div className="flex items-center gap-1.5 text-sm">
                            <label className="text-xs font-medium text-slate-500">Ora</label>
                            <select
                                value={selected ? selected.getHours() : ''}
                                onChange={(e) => setHour(Number(e.target.value))}
                                className="rounded-md border border-slate-300 bg-white px-1.5 py-1 text-sm text-slate-900 outline-none focus:border-indigo-500"
                            >
                                {!selected && <option value="" disabled>--</option>}
                                {Array.from({ length: 24 }, (_, h) => (
                                    <option key={h} value={h}>
                                        {pad2(h)}
                                    </option>
                                ))}
                            </select>
                            <span className="text-slate-400">:</span>
                            <select
                                value={selected ? selected.getMinutes() : ''}
                                onChange={(e) => setMinute(Number(e.target.value))}
                                className="rounded-md border border-slate-300 bg-white px-1.5 py-1 text-sm text-slate-900 outline-none focus:border-indigo-500"
                            >
                                {!selected && <option value="" disabled>--</option>}
                                {[0, 15, 30, 45].map((m) => (
                                    <option key={m} value={m}>
                                        {pad2(m)}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <button
                            type="button"
                            onClick={() => setOpen(false)}
                            className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-indigo-700"
                        >
                            Gata
                        </button>
                    </div>
                </div>
            )}
        </div>
    )
}
