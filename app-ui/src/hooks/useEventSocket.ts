import {useEffect, useRef, useState} from "react";

const WS_BASE_URL = "ws://127.0.0.1:8080";

interface TicketStatusMessage{
    eventId: number
    seatId: number
    status: 'AVAILABLE' | 'HELD' | 'SOLD'
    occurredAt: string
    heldUntil?: string | null
}

const RECONNECT_DELAY_MS = 2000;

export function useEventSocket(eventId: number | null) {
    const [seatStatus, setSeatStatus] = useState<Map<number, TicketStatusMessage>>(new Map)
    const [connected, setConnected] = useState(false)
    const wsRef = useRef<WebSocket | null>(null)
    const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
    const closedByUs = useRef(false)

    // Reset stale per-seat status from a previous event as soon as `eventId`
    // changes, before this render commits - not in the effect below, so it
    // happens during render (React's endorsed way to adjust state in
    // response to a prop change: https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes)
    // instead of as a same-render-then-another setState-in-effect cascade.
    // Uses useState (not a ref) for prevEventId, matching that pattern - a
    // ref mutated during render isn't safe under concurrent rendering.
    const [prevEventId, setPrevEventId] = useState(eventId)
    if (prevEventId !== eventId) {
        setPrevEventId(eventId)
        setSeatStatus(new Map())
    }

    useEffect(() => {
        if(eventId === null) return

        closedByUs.current = false

        function connect() {
            const ws = new WebSocket(`${WS_BASE_URL}/ws/events/${eventId}`)
            wsRef.current = ws

            ws.onopen = () => setConnected(true)

            ws.onmessage = (event) => {
                const message: TicketStatusMessage = JSON.parse(event.data)
                setSeatStatus((prev) => new Map(prev).set(message.seatId, message))
            }

            ws.onclose = () => {setConnected(false)
                if(!closedByUs.current) {
                    reconnectTimer.current = setTimeout(connect, RECONNECT_DELAY_MS)
                }
            }

            ws.onerror = () => {
                ws.close()
            }
        }

        connect()
        return () => {
            closedByUs.current = true
            if(reconnectTimer.current) clearTimeout(reconnectTimer.current)
            wsRef.current?.close()
        }
    }, [eventId])

    return {seatStatus, connected}
}
