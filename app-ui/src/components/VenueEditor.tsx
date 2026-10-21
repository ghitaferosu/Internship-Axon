import { useEffect, useRef, useState } from 'react'
import type { CSSProperties, DragEvent as ReactDragEvent, MouseEvent as ReactMouseEvent } from 'react'
import Panzoom, { type PanzoomObject } from '@panzoom/panzoom'
import { saveVenue } from '../api/venueLayout'
import type { BoundingBox } from '../lib/geometry'
import type { Point, VenueDrawRequest } from '../types/venueLayout'

const STAGE_RADIUS = 40
const DEFAULT_VIEW_BOX: BoundingBox = { x: -400, y: -400, width: 800, height: 800 }
const MIN_SCALE = 0.4
const MAX_SCALE = 20
// Matches Panzoom's own `step` option below, so the +/- buttons zoom by the
// same per-click amount as one wheel notch.
const ZOOM_STEP = 0.3

// A mousedown+mouseup on a section counts as a "click" (select/toggle) below
// this many screen pixels of movement; past it, it's a drag (move the
// section). Screen pixels for the same reason as CLOSE_SNAP_PX above - it's
// about filtering out accidental jitter, not a venue-space distance.
const DRAG_CLICK_THRESHOLD_PX = 4

const SECTION_STROKE = '#7fb3d9'
const SECTION_STROKE_SELECTED = '#ffd23d'
const HANDLE_FILL = '#ffd23d'
const HANDLE_STROKE = '#1c1c1c'
const HANDLE_RADIUS = 7
const ROTATE_HANDLE_RADIUS = 8
// How far beyond a shape's own edge the rotate handle floats - a fixed venue-unit
// offset (same convention as the other on-canvas affordances here) so it never
// collides with the length/width handles, which sit on the shape's own edges.
const ROTATE_HANDLE_DISTANCE = 35

// --- Parametric shapes ---------------------------------------------------
//
// Rectangle/Trapezoid sections dragged in from the palette are stored as a
// small parameter set, not just raw points - `points` is always *derived*
// from these parameters (pointsFromShape below), never hand-edited. That's
// what lets the resize handles change only length/width(s) and guarantees
// the result is still a rectangle/trapezoid, instead of exposing raw corner
// handles that could drag a shape into an arbitrary quadrilateral.
//
// `anchor` is the front-center point (nearest the stage side, as drawn) and
// `angle` is the direction rows extend away from it - the exact same
// convention SectionGeometryCalculator/lib/geometry.ts's seatPosition() use,
// which is why `angle` is sent straight through as SectionDrawRequest.angle
// instead of leaving the backend to guess it (see venue-authoring-rework and
// the SectionGeometryCalculator explicit-angle overload in project memory).
interface RectangleShape {
    type: 'rectangle'
    anchor: Point
    angle: number
    length: number
    width: number
}

interface TrapezoidShape {
    type: 'trapezoid'
    anchor: Point
    angle: number
    length: number
    bottomWidth: number
    topWidth: number
}

type SectionShape = RectangleShape | TrapezoidShape
type PaletteShapeType = SectionShape['type']
type HandleKind = 'length' | 'width' | 'bottomWidth' | 'topWidth' | 'rotate'

// Sized so the *default* row/seat counts (DEFAULT_ROWS x DEFAULT_SEATS_PER_ROW
// below) land on a comfortable rowSpacing out of the box - a too-small drop
// size packs seats close enough together that SeatMap's per-seat radius
// bottoms out and they're barely distinguishable. Resizable afterward either way.
const RECTANGLE_DEFAULTS = { length: 300, width: 200 }
const TRAPEZOID_DEFAULTS = { length: 300, bottomWidth: 220, topWidth: 140 }
// Floor on any shape dimension a handle can drag to - keeps a careless drag
// from collapsing a section down to zero/negative size.
const MIN_SHAPE_DIMENSION = 20

const DRAG_PAYLOAD_TYPE = 'application/x-section-shape'

function directionOf(angle: number): Point {
    return { x: Math.cos(angle), y: Math.sin(angle) }
}

// Perpendicular to directionOf(angle), using the same rotate-by-90-degrees
// convention lib/geometry.ts's seatPosition() uses server-side ((-sin, cos))
// - keeping these in lockstep matters so a section's seats end up spreading
// the same way its outline was actually drawn.
function perpendicularOf(angle: number): Point {
    return { x: -Math.sin(angle), y: Math.cos(angle) }
}

function dot(a: Point, b: Point): number {
    return a.x * b.x + a.y * b.y
}

// The only place a Rectangle/Trapezoid's 4 corners are computed - front/back
// come from anchor+angle+length, bottom/top widths spread perpendicular to
// that axis. Rectangle is a Trapezoid with equal widths at both ends; nothing
// here special-cases it beyond reading `width` for both.
function pointsFromShape(shape: SectionShape): Point[] {
    const dir = directionOf(shape.angle)
    const perp = perpendicularOf(shape.angle)
    const front = shape.anchor
    const back = { x: front.x + dir.x * shape.length, y: front.y + dir.y * shape.length }
    const bottomWidth = shape.type === 'rectangle' ? shape.width : shape.bottomWidth
    const topWidth = shape.type === 'rectangle' ? shape.width : shape.topWidth
    return [
        { x: front.x - (perp.x * bottomWidth) / 2, y: front.y - (perp.y * bottomWidth) / 2 },
        { x: front.x + (perp.x * bottomWidth) / 2, y: front.y + (perp.y * bottomWidth) / 2 },
        { x: back.x + (perp.x * topWidth) / 2, y: back.y + (perp.y * topWidth) / 2 },
        { x: back.x - (perp.x * topWidth) / 2, y: back.y - (perp.y * topWidth) / 2 },
    ]
}

// Mirrors SectionGeometryCalculator.java's MIN_SPACING reject threshold -
// kept in sync by hand (see that class's own doc comment) since there's no
// shared code across the Java/TypeScript boundary. Below this, geometry.ts's
// seatRadius() can no longer keep dots from touching/overlapping no matter
// how it degrades, so the backend now rejects it outright rather than
// silently flooring - this client-side check exists so an admin sees that
// coming before hitting Save, not after.
const MIN_USABLE_SPACING = 1

// A Rectangle's own box already *is* its inscribed rectangle (no shrinking
// needed, unlike the backend's general polygon fit); a Trapezoid's usable
// seat-axis width is bounded by its narrower end. Both are a fast,
// close-enough stand-in for SectionGeometryCalculator's real point-in-
// polygon shrink-fit - good enough for a live warning before save, not
// meant to be pixel-exact (the backend has the final say at save time).
function sectionCapacity(shape: SectionShape, rows: number, seatsPerRow: number): { rowSpacing: number; colSpacing: number } {
    const seatAxisWidth = shape.type === 'rectangle' ? shape.width : Math.min(shape.bottomWidth, shape.topWidth)
    return {
        rowSpacing: Math.floor(shape.length / rows),
        colSpacing: Math.floor(seatAxisWidth / seatsPerRow),
    }
}

function isSectionTooDense(shape: SectionShape, rows: number, seatsPerRow: number): boolean {
    const { rowSpacing, colSpacing } = sectionCapacity(shape, rows, seatsPerRow)
    return rowSpacing < MIN_USABLE_SPACING || colSpacing < MIN_USABLE_SPACING
}

function shapeBack(shape: SectionShape): Point {
    const dir = directionOf(shape.angle)
    return { x: shape.anchor.x + dir.x * shape.length, y: shape.anchor.y + dir.y * shape.length }
}

// Midpoint between front and back edges - the pivot rotation spins the shape
// around, so rotating spins it in place instead of swinging the front corner
// around like a pendulum.
function shapeMidpoint(shape: SectionShape): Point {
    const back = shapeBack(shape)
    return { x: (shape.anchor.x + back.x) / 2, y: (shape.anchor.y + back.y) / 2 }
}

// Re-anchors a shape to a new angle while keeping its midpoint fixed.
function rotateShape(shape: SectionShape, angle: number): SectionShape {
    const mid = shapeMidpoint(shape)
    const dir = directionOf(angle)
    const anchor = { x: mid.x - (dir.x * shape.length) / 2, y: mid.y - (dir.y * shape.length) / 2 }
    return { ...shape, angle, anchor }
}

// Applies one handle drag's effect, given the shape *before* this drag frame
// and the current mouse position in venue space. Each kind only ever touches
// one parameter - there's no path from here to an arbitrary quadrilateral.
function applyHandleDrag(shape: SectionShape, kind: HandleKind, point: Point): SectionShape {
    const dir = directionOf(shape.angle)
    const perp = perpendicularOf(shape.angle)
    const front = shape.anchor
    const along = (from: Point, axis: Point) => dot({ x: point.x - from.x, y: point.y - from.y }, axis)

    switch (kind) {
        case 'rotate': {
            const mid = shapeMidpoint(shape)
            return rotateShape(shape, Math.atan2(point.y - mid.y, point.x - mid.x))
        }
        case 'length':
            return { ...shape, length: Math.max(MIN_SHAPE_DIMENSION, along(front, dir)) }
        case 'width':
            return shape.type === 'rectangle'
                ? { ...shape, width: Math.max(MIN_SHAPE_DIMENSION, 2 * along(front, perp)) }
                : shape
        case 'bottomWidth':
            return shape.type === 'trapezoid'
                ? { ...shape, bottomWidth: Math.max(MIN_SHAPE_DIMENSION, 2 * along(front, perp)) }
                : shape
        case 'topWidth': {
            if (shape.type !== 'trapezoid') return shape
            const back = shapeBack(shape)
            return { ...shape, topWidth: Math.max(MIN_SHAPE_DIMENSION, 2 * along(back, perp)) }
        }
    }
}

interface EditorSection {
    id: string
    name: string
    // Always exactly pointsFromShape(shape) - kept as a stored field (rather
    // than recomputed on every render) since it's what's actually drawn and
    // sent to the backend, but `shape` is the source of truth for every
    // mutation (move/resize/rotate/mirror/paste all update `shape` first,
    // then re-derive `points` from it).
    points: Point[]
    rows: number
    seatsPerRow: number
    shape: SectionShape
}

interface Clipboard {
    name: string
    points: Point[]
    rows: number
    seatsPerRow: number
    shape: SectionShape
}

interface DragState {
    sectionId: string
    startVenue: Point
    startScreenX: number
    startScreenY: number
    originalPoints: Point[]
    originalShape: SectionShape
    moved: boolean
}

interface HandleDragState {
    sectionId: string
    kind: HandleKind
}

// Minimal shape both React's synthetic MouseEvent and the native window
// MouseEvent satisfy - screenToVenuePoint only ever needs clientX/clientY,
// so it can be called from JSX handlers and window-level listeners alike.
interface ScreenPoint {
    clientX: number
    clientY: number
}

// Converts a mouse event's screen coordinates into the content group's local
// (venue-space) coordinate system. getScreenCTM() reflects the full transform
// chain - the SVG's own viewBox scaling *and* whatever Panzoom has applied to
// contentEl (pan/zoom) - so this stays correct regardless of current
// pan/zoom state, without this component needing to know Panzoom's internals.
function screenToVenuePoint(event: ScreenPoint, svgEl: SVGSVGElement, contentEl: SVGGElement): Point {
    const ctm = contentEl.getScreenCTM()
    if (!ctm) return { x: 0, y: 0 }
    const point = svgEl.createSVGPoint()
    point.x = event.clientX
    point.y = event.clientY
    const local = point.matrixTransform(ctm.inverse())
    return { x: local.x, y: local.y }
}

function centroidOf(points: Point[]): Point {
    const sum = points.reduce((acc, p) => ({ x: acc.x + p.x, y: acc.y + p.y }), { x: 0, y: 0 })
    return { x: sum.x / points.length, y: sum.y / points.length }
}

function pointsToAttr(points: Point[]): string {
    return points.map((p) => `${p.x},${p.y}`).join(' ')
}

function translatePoints(points: Point[], dx: number, dy: number): Point[] {
    return points.map((p) => ({ x: p.x + dx, y: p.y + dy }))
}

// Reflects a shape in place (about `center`) - "flip horizontal"/"flip
// vertical" in the same sense as Figma/Illustrator. To get a mirrored *copy*
// on the other side of the venue (e.g. the matching stand on the opposite
// side of the stage), copy/paste the section first, then mirror the pasted
// duplicate and drag it into place - deliberately kept as two composable
// steps rather than one "mirror across the stage" operation, since that
// would need to guess which axis/reference point the admin actually wants.
//
// Derived, not guessed: reflecting the front->back direction vector across a
// vertical line negates its x-component, which is exactly what
// angle -> pi - angle does to (cos angle, sin angle); reflecting across a
// horizontal line negates the y-component, i.e. angle -> -angle. The anchor
// reflects like any other point; length/width(s) are unchanged - reflection
// only changes orientation and handedness, not size. Verified this
// reconstructs the exact same four corners a direct point-by-point
// reflection of pointsFromShape(shape) would produce.
function mirrorShape(shape: SectionShape, axis: 'horizontal' | 'vertical', center: Point): SectionShape {
    if (axis === 'horizontal') {
        return { ...shape, angle: Math.PI - shape.angle, anchor: { x: 2 * center.x - shape.anchor.x, y: shape.anchor.y } }
    }
    return { ...shape, angle: -shape.angle, anchor: { x: shape.anchor.x, y: 2 * center.y - shape.anchor.y } }
}

// Paste offsets the duplicate by this much (venue units) so it's visibly
// distinct from the section it was copied from, instead of sitting exactly
// on top of it, before the admin drags it into place.
const PASTE_OFFSET = 24

let nextSectionSeq = 1

const DEFAULT_ROWS = 10
const DEFAULT_SEATS_PER_ROW = 20

const primaryButtonClass =
    'rounded-lg bg-indigo-600 px-3.5 py-1.5 text-sm font-semibold text-white shadow-sm shadow-indigo-600/20 transition hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed'
const secondaryButtonClass =
    'rounded-lg border border-slate-300 bg-white px-3.5 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed'
const compactInputClass =
    'rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-900 outline-none transition focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10'
const labelClass = 'text-sm font-medium text-slate-700'

export default function VenueEditor() {
    const [venueName, setVenueName] = useState('')
    const [address, setAddress] = useState('')
    const [sections, setSections] = useState<EditorSection[]>([])
    const [saveStatus, setSaveStatus] = useState<string | null>(null)
    const [isSaving, setIsSaving] = useState(false)
    const [selectedSectionId, setSelectedSectionId] = useState<string | null>(null)
    const [clipboard, setClipboard] = useState<Clipboard | null>(null)

    const svgRef = useRef<SVGSVGElement | null>(null)
    const contentRef = useRef<SVGGElement | null>(null)
    const panzoomRef = useRef<PanzoomObject | null>(null)
    // Mutable, not state: updated on every mousemove during a section drag, and
    // read (not rendered) by the window listeners below - putting this in state
    // would re-run the drag effect on every pixel of movement for no benefit.
    const dragRef = useRef<DragState | null>(null)
    const handleDragRef = useRef<HandleDragState | null>(null)
    // Mirrors `sections` for the copy shortcut's keydown effect below to read
    // without needing `sections` in that effect's deps - dragging updates
    // `sections` on every mousemove without touching selectedSectionId/draft/
    // clipboard, so without this a copy mid-drag (or right after one, before
    // any of those three change) would copy stale, pre-drag points.
    const sectionsRef = useRef(sections)
    useEffect(() => {
        sectionsRef.current = sections
    }, [sections])

    useEffect(() => {
        const contentEl = contentRef.current
        const svgEl = svgRef.current
        if (!contentEl || !svgEl) return undefined

        const panzoom = Panzoom(contentEl, {
            minScale: MIN_SCALE,
            maxScale: MAX_SCALE,
            step: ZOOM_STEP,
            cursor: 'crosshair',
            // canvas:true so pan-drag works from empty space, same as SeatMap. A
            // plain click (mousedown+mouseup with no drag distance) still reaches
            // our onClick below afterwards - Panzoom only intercepts actual drags.
            canvas: true,
        })
        panzoomRef.current = panzoom
        svgEl.addEventListener('wheel', panzoom.zoomWithWheel, { passive: false })

        return () => {
            svgEl.removeEventListener('wheel', panzoom.zoomWithWheel)
            panzoom.destroy()
            panzoomRef.current = null
        }
    }, [])

    // Zoom in/out by one step, anchored on whatever content point is
    // currently centered in the viewport, so repeated clicks zoom toward
    // wherever the admin is already looking instead of jumping back to the
    // canvas's default center every time.
    function zoomBy(factor: number) {
        const panzoom = panzoomRef.current
        if (!panzoom) return
        const currentScale = panzoom.getScale()
        const targetScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, currentScale * factor))
        if (targetScale === currentScale) return

        const pan = panzoom.getPan()
        const viewBox = DEFAULT_VIEW_BOX
        const viewCenterX = viewBox.x + viewBox.width / 2
        const viewCenterY = viewBox.y + viewBox.height / 2
        const pivotX = viewCenterX / currentScale - pan.x
        const pivotY = viewCenterY / currentScale - pan.y

        panzoom.zoom(targetScale, { animate: true, force: true })
        panzoom.pan(viewCenterX / targetScale - pivotX, viewCenterY / targetScale - pivotY, {
            animate: true,
            force: true,
        })
    }

    // Resets to the untouched default framing (scale 1, no pan) - the same
    // view the canvas starts in.
    function resetView() {
        const panzoom = panzoomRef.current
        if (!panzoom) return
        panzoom.zoom(1, { animate: true, force: true })
        panzoom.pan(0, 0, { animate: true, force: true })
    }

    // Delete/Backspace removes the selected section, Escape deselects,
    // Ctrl/Cmd+C copies it, Ctrl/Cmd+V pastes the clipboard as a new (offset)
    // section - only while not typing in a text input.
    useEffect(() => {
        if (!selectedSectionId && !clipboard) return undefined

        function onKeyDown(event: KeyboardEvent) {
            if ((document.activeElement as HTMLElement | null)?.tagName === 'INPUT') return
            const meta = event.ctrlKey || event.metaKey

            if (meta && event.key.toLowerCase() === 'c' && selectedSectionId) {
                event.preventDefault()
                copySelectedSection()
            } else if (meta && event.key.toLowerCase() === 'v' && clipboard) {
                event.preventDefault()
                pasteClipboard()
            } else if (!meta && (event.key === 'Delete' || event.key === 'Backspace') && selectedSectionId) {
                event.preventDefault()
                removeSection(selectedSectionId)
            } else if (event.key === 'Escape' && selectedSectionId) {
                setSelectedSectionId(null)
            }
        }

        window.addEventListener('keydown', onKeyDown)
        return () => window.removeEventListener('keydown', onKeyDown)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedSectionId, clipboard])

    // Section drag-to-move. Bound once at the window level (not per-section
    // React handlers) since a drag can move the pointer outside the section's
    // own shape, or outside the SVG entirely, mid-gesture. handleSectionMouseDown
    // below is what actually populates dragRef to arm these.
    useEffect(() => {
        function onMouseMove(event: MouseEvent) {
            const drag = dragRef.current
            const svgEl = svgRef.current
            const contentEl = contentRef.current
            if (!drag || !svgEl || !contentEl) return

            if (!drag.moved) {
                const screenDist = Math.hypot(event.clientX - drag.startScreenX, event.clientY - drag.startScreenY)
                if (screenDist < DRAG_CLICK_THRESHOLD_PX) return
                drag.moved = true
            }

            const current = screenToVenuePoint(event, svgEl, contentEl)
            const dx = current.x - drag.startVenue.x
            const dy = current.y - drag.startVenue.y
            setSections((prev) =>
                prev.map((s) => {
                    if (s.id !== drag.sectionId) return s
                    // Keep shape.anchor in lockstep with points while dragging -
                    // otherwise a later resize/rotate would regenerate points
                    // from the shape's stale, pre-drag position.
                    const shape = {
                        ...drag.originalShape,
                        anchor: { x: drag.originalShape.anchor.x + dx, y: drag.originalShape.anchor.y + dy },
                    }
                    return { ...s, points: translatePoints(drag.originalPoints, dx, dy), shape }
                }),
            )
        }

        function onMouseUp() {
            const drag = dragRef.current
            if (!drag) return
            if (drag.moved) {
                // A drag just ended - leave the section selected so the move's result is visible.
                setSelectedSectionId(drag.sectionId)
            } else {
                // No movement past the threshold - this was a click, not a drag: toggle selection.
                setSelectedSectionId((prev) => (prev === drag.sectionId ? null : drag.sectionId))
            }
            dragRef.current = null
        }

        window.addEventListener('mousemove', onMouseMove)
        window.addEventListener('mouseup', onMouseUp)
        return () => {
            window.removeEventListener('mousemove', onMouseMove)
            window.removeEventListener('mouseup', onMouseUp)
        }
    }, [])

    // Resize/rotate handle dragging - a distinct gesture from whole-section
    // move above, so the two don't get tangled: a handle mousedown stops
    // propagation before it can arm handleSectionMouseDown.
    useEffect(() => {
        function onMouseMove(event: MouseEvent) {
            const drag = handleDragRef.current
            const svgEl = svgRef.current
            const contentEl = contentRef.current
            if (!drag || !svgEl || !contentEl) return
            const point = screenToVenuePoint(event, svgEl, contentEl)

            setSections((prev) =>
                prev.map((s) => {
                    if (s.id !== drag.sectionId) return s
                    const nextShape = applyHandleDrag(s.shape, drag.kind, point)
                    return { ...s, shape: nextShape, points: pointsFromShape(nextShape) }
                }),
            )
        }

        function onMouseUp() {
            handleDragRef.current = null
        }

        window.addEventListener('mousemove', onMouseMove)
        window.addEventListener('mouseup', onMouseUp)
        return () => {
            window.removeEventListener('mousemove', onMouseMove)
            window.removeEventListener('mouseup', onMouseUp)
        }
    }, [])

    function handleSectionMouseDown(event: ReactMouseEvent<SVGGElement>, section: EditorSection) {
        event.stopPropagation()
        const svgEl = svgRef.current
        const contentEl = contentRef.current
        if (!svgEl || !contentEl) return

        dragRef.current = {
            sectionId: section.id,
            startVenue: screenToVenuePoint(event, svgEl, contentEl),
            startScreenX: event.clientX,
            startScreenY: event.clientY,
            originalPoints: section.points,
            originalShape: section.shape,
            moved: false,
        }
    }

    function startHandleDrag(event: ReactMouseEvent, section: EditorSection, kind: HandleKind) {
        event.stopPropagation()
        handleDragRef.current = { sectionId: section.id, kind }
    }

    // Drag-and-drop placement from the shape palette. No mousedown-drag-to-size
    // step - the shape lands at a fixed default size, centered on the drop
    // point, facing away from the stage by default; resize/rotate handles
    // (rendered once it's selected) are how the admin actually shapes it.
    function handlePaletteDragStart(event: ReactDragEvent<HTMLDivElement>, type: PaletteShapeType) {
        event.dataTransfer.setData(DRAG_PAYLOAD_TYPE, type)
        event.dataTransfer.effectAllowed = 'copy'
    }

    function handleCanvasDragOver(event: ReactDragEvent<SVGSVGElement>) {
        if (!event.dataTransfer.types.includes(DRAG_PAYLOAD_TYPE)) return
        event.preventDefault()
        event.dataTransfer.dropEffect = 'copy'
    }

    function handleCanvasDrop(event: ReactDragEvent<SVGSVGElement>) {
        const type = event.dataTransfer.getData(DRAG_PAYLOAD_TYPE)
        if (type !== 'rectangle' && type !== 'trapezoid') return
        event.preventDefault()
        if (!svgRef.current || !contentRef.current) return
        const dropPoint = screenToVenuePoint(event, svgRef.current, contentRef.current)
        addShapeSection(type, dropPoint)
    }

    function addShapeSection(type: PaletteShapeType, center: Point) {
        // Faces due "north" (away from the stage at the origin) by default -
        // just a sensible starting orientation, not a guess baked into what
        // gets saved; the rotate handle is how the admin actually aims it.
        const angle = -Math.PI / 2
        const dir = directionOf(angle)
        const length = type === 'rectangle' ? RECTANGLE_DEFAULTS.length : TRAPEZOID_DEFAULTS.length
        const anchor = { x: center.x - (dir.x * length) / 2, y: center.y - (dir.y * length) / 2 }
        const shape: SectionShape =
            type === 'rectangle'
                ? { type: 'rectangle', anchor, angle, ...RECTANGLE_DEFAULTS }
                : { type: 'trapezoid', anchor, angle, ...TRAPEZOID_DEFAULTS }

        const id = `shape-${nextSectionSeq++}`
        setSections((prev) => [
            ...prev,
            {
                id,
                name: `Section ${prev.length + 1}`,
                points: pointsFromShape(shape),
                rows: DEFAULT_ROWS,
                seatsPerRow: DEFAULT_SEATS_PER_ROW,
                shape,
            },
        ])
        setSelectedSectionId(id)
    }

    function setSectionCapacity(id: string, field: 'rows' | 'seatsPerRow', value: number) {
        const safe = Number.isFinite(value) ? Math.max(1, Math.floor(value)) : 1
        setSections((prev) => prev.map((s) => (s.id === id ? { ...s, [field]: safe } : s)))
    }

    function removeSection(id: string) {
        setSections((prev) => prev.filter((s) => s.id !== id))
        setSelectedSectionId((prev) => (prev === id ? null : prev))
    }

    function copySelectedSection() {
        const section = sectionsRef.current.find((s) => s.id === selectedSectionId)
        if (!section) return
        setClipboard({
            name: section.name,
            points: section.points,
            rows: section.rows,
            seatsPerRow: section.seatsPerRow,
            shape: section.shape,
        })
    }

    function pasteClipboard() {
        if (!clipboard) return
        const id = `section-${nextSectionSeq++}`
        const shape = {
            ...clipboard.shape,
            anchor: { x: clipboard.shape.anchor.x + PASTE_OFFSET, y: clipboard.shape.anchor.y + PASTE_OFFSET },
        }
        setSections((prev) => [
            ...prev,
            {
                id,
                name: `${clipboard.name} copy`,
                points: pointsFromShape(shape),
                rows: clipboard.rows,
                seatsPerRow: clipboard.seatsPerRow,
                shape,
            },
        ])
        setSelectedSectionId(id)
    }

    function mirrorSelectedSection(axis: 'horizontal' | 'vertical') {
        if (!selectedSectionId) return
        setSections((prev) =>
            prev.map((s) => {
                if (s.id !== selectedSectionId) return s
                const shape = mirrorShape(s.shape, axis, centroidOf(s.points))
                return { ...s, shape, points: pointsFromShape(shape) }
            }),
        )
    }

    function handleCanvasClick() {
        setSelectedSectionId(null)
    }

    async function handleSave() {
        const tooDenseNames = sections
            .filter((s) => isSectionTooDense(s.shape, s.rows, s.seatsPerRow))
            .map((s) => s.name)
        if (tooDenseNames.length > 0) {
            const proceed = window.confirm(
                `${tooDenseNames.join(', ')} ${tooDenseNames.length === 1 ? 'has' : 'have'} too many seats for ` +
                    `its size and will render with overlapping seats - the server may also reject it outright. ` +
                    `Save anyway?`,
            )
            if (!proceed) return
        }

        const payload: VenueDrawRequest = {
            name: venueName,
            address,
            sections: sections.map(({ name, points, rows, seatsPerRow, shape }) => ({
                name,
                points,
                rows,
                seatsPerRow,
                angle: shape.angle,
            })),
        }
        setIsSaving(true)
        setSaveStatus('Saving…')
        try {
            const saved = await saveVenue(payload)
            setSaveStatus(`Saved "${saved.name}" (id ${saved.id}) with ${saved.sections.length} section(s) and ${saved.noSeats} seat(s).`)
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Failed to save'
            setSaveStatus(`Failed to save: ${message}`)
        } finally {
            setIsSaving(false)
        }
    }

    const viewBox = DEFAULT_VIEW_BOX
    const selectedSection = sections.find((s) => s.id === selectedSectionId) ?? null
    const totalSeats = sections.reduce((sum, s) => sum + s.rows * s.seatsPerRow, 0)

    return (
        <div className="flex flex-col gap-4 lg:flex-row">
            <aside className="flex shrink-0 flex-row gap-3 lg:w-40 lg:flex-col">
                <h3 className="hidden text-sm font-semibold text-slate-900 lg:block">Predefined shapes</h3>
                <p className="hidden text-xs text-slate-500 lg:block">Drag onto the canvas to place a section.</p>
                <ShapePaletteItem type="rectangle" label="Rectangle" onDragStart={handlePaletteDragStart} />
                <ShapePaletteItem type="trapezoid" label="Trapezoid" onDragStart={handlePaletteDragStart} />
            </aside>

            <div className="min-w-0 flex-1">
                <div className="mb-3 flex flex-wrap items-center gap-3">
                    <label className={labelClass} htmlFor="venueName">
                        Venue name
                    </label>
                    <input
                        id="venueName"
                        type="text"
                        value={venueName}
                        onChange={(e) => setVenueName(e.target.value)}
                        className={compactInputClass}
                    />
                    <label className={labelClass} htmlFor="venueAddress">
                        Address
                    </label>
                    <input
                        id="venueAddress"
                        type="text"
                        value={address}
                        onChange={(e) => setAddress(e.target.value)}
                        className={compactInputClass}
                    />
                </div>

                <div className="mb-3 flex flex-wrap items-center gap-3">
                    <button type="button" onClick={copySelectedSection} disabled={!selectedSectionId} className={secondaryButtonClass}>
                        Copy
                    </button>
                    <button type="button" onClick={pasteClipboard} disabled={!clipboard} className={secondaryButtonClass}>
                        Paste
                    </button>
                    <button
                        type="button"
                        onClick={() => mirrorSelectedSection('horizontal')}
                        disabled={!selectedSectionId}
                        className={secondaryButtonClass}
                    >
                        Mirror ↔
                    </button>
                    <button
                        type="button"
                        onClick={() => mirrorSelectedSection('vertical')}
                        disabled={!selectedSectionId}
                        className={secondaryButtonClass}
                    >
                        Mirror ↕
                    </button>
                    {selectedSection && (
                        <>
                            <span className="text-sm font-medium text-amber-600">{selectedSection.name}:</span>
                            <label className={labelClass} htmlFor="sectionRows">
                                Rows
                            </label>
                            <input
                                id="sectionRows"
                                type="number"
                                min={1}
                                className={`${compactInputClass} w-[4.5rem]`}
                                value={selectedSection.rows}
                                onChange={(e) => setSectionCapacity(selectedSection.id, 'rows', Number(e.target.value))}
                            />
                            <label className={labelClass} htmlFor="sectionSeatsPerRow">
                                Seats/row
                            </label>
                            <input
                                id="sectionSeatsPerRow"
                                type="number"
                                min={1}
                                className={`${compactInputClass} w-[4.5rem]`}
                                value={selectedSection.seatsPerRow}
                                onChange={(e) => setSectionCapacity(selectedSection.id, 'seatsPerRow', Number(e.target.value))}
                            />
                            <span className="text-sm text-slate-500">
                                = {selectedSection.rows * selectedSection.seatsPerRow} seats
                            </span>
                            {isSectionTooDense(selectedSection.shape, selectedSection.rows, selectedSection.seatsPerRow) && (
                                <span className="text-sm font-medium text-red-600">
                                    ⚠ Too many seats for this section's size - seats will overlap. Enlarge the shape or reduce
                                    rows/seats-per-row.
                                </span>
                            )}
                        </>
                    )}
                    {sections.length > 0 && (
                        <span className="text-xs text-slate-500">
                            Click a section to select it, drag it to move it. Delete/Backspace removes it, Esc deselects, Ctrl/Cmd+C/V
                            copies/pastes. Drag a resize/rotate handle to reshape a Rectangle/Trapezoid.
                        </span>
                    )}
                </div>

                <div style={{ position: 'relative' }}>
                <svg
                    ref={svgRef}
                    viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`}
                    style={{ width: '100%', height: '70vh', background: '#111', borderRadius: 8, display: 'block' }}
                    onClick={handleCanvasClick}
                    onDragOver={handleCanvasDragOver}
                    onDrop={handleCanvasDrop}
                >
                    <style>{`
                        .zoom-btn:hover { background: rgba(255,255,255,0.18); }
                        .zoom-btn:active { background: rgba(255,255,255,0.28); }
                    `}</style>
                    <g ref={contentRef}>
                        <rect x={viewBox.x} y={viewBox.y} width={viewBox.width} height={viewBox.height} fill="#111" />

                        <circle cx={0} cy={0} r={STAGE_RADIUS} fill="#333" stroke="#666" />
                        <text x={0} y={0} fill="#aaa" fontSize={12} textAnchor="middle" dominantBaseline="middle">
                            STAGE
                        </text>

                        {sections.map((section) => {
                            const centroid = centroidOf(section.points)
                            const isSelected = section.id === selectedSectionId
                            const tooDense = isSectionTooDense(section.shape, section.rows, section.seatsPerRow)
                            return (
                                // panzoom-exclude keeps Panzoom's own canvas-drag from starting
                                // when the mousedown lands on a section - see handleSectionMouseDown,
                                // which owns drag-to-move + click-to-select for these instead.
                                <g
                                    key={section.id}
                                    className="panzoom-exclude"
                                    onMouseDown={(event) => handleSectionMouseDown(event, section)}
                                    // Stop the native click (fired after our mouseup handling below)
                                    // from bubbling to the canvas's onClick and immediately undoing
                                    // the selection it just set.
                                    onClick={(event) => event.stopPropagation()}
                                    style={{ cursor: 'grab' }}
                                >
                                    <polygon
                                        points={pointsToAttr(section.points)}
                                        fill="#2b5b8455"
                                        stroke={tooDense ? '#e04a4a' : isSelected ? SECTION_STROKE_SELECTED : SECTION_STROKE}
                                        strokeWidth={isSelected || tooDense ? 3 : 2}
                                        strokeDasharray={tooDense ? '5 3' : undefined}
                                    />
                                    <text x={centroid.x} y={centroid.y} fill="#fff" fontSize={14} textAnchor="middle" dominantBaseline="middle">
                                        {section.name}
                                    </text>
                                    {tooDense && (
                                        <text x={centroid.x} y={centroid.y + 16} fill="#e04a4a" fontSize={11} textAnchor="middle">
                                            ⚠ too many seats
                                        </text>
                                    )}
                                    <text
                                        x={centroid.x}
                                        y={centroid.y + (tooDense ? 32 : 18)}
                                        fill="#e07"
                                        fontSize={12}
                                        textAnchor="middle"
                                        style={{ cursor: 'pointer' }}
                                        onMouseDown={(event) => event.stopPropagation()}
                                        onClick={(event) => {
                                            event.stopPropagation()
                                            removeSection(section.id)
                                        }}
                                    >
                                        remove
                                    </text>
                                </g>
                            )
                        })}

                        {selectedSection && (
                            <SectionHandles
                                section={selectedSection}
                                onHandleMouseDown={(event, kind) => startHandleDrag(event, selectedSection, kind)}
                            />
                        )}
                    </g>
                </svg>

                <ZoomControls
                    onZoomIn={() => zoomBy(Math.exp(ZOOM_STEP))}
                    onZoomOut={() => zoomBy(Math.exp(-ZOOM_STEP))}
                    onReset={resetView}
                />
                </div>

                <div className="mt-3 flex flex-wrap items-center gap-3">
                    <button type="button" onClick={handleSave} disabled={sections.length === 0 || isSaving} className={primaryButtonClass}>
                        Save venue
                    </button>
                    {sections.length > 0 && (
                        <span className="text-xs text-slate-500">
                            {sections.length} section(s), {totalSeats} seats total
                        </span>
                    )}
                    {saveStatus && <span className="text-xs text-slate-500">{saveStatus}</span>}
                </div>
            </div>
        </div>
    )
}

function ShapePaletteItem({
    type,
    label,
    onDragStart,
}: {
    type: PaletteShapeType
    label: string
    onDragStart: (event: ReactDragEvent<HTMLDivElement>, type: PaletteShapeType) => void
}) {
    return (
        <div
            draggable
            onDragStart={(event) => onDragStart(event, type)}
            className="flex w-24 shrink-0 cursor-grab flex-col items-center gap-1.5 rounded-xl border border-slate-200 bg-white p-3 text-center shadow-sm transition hover:border-indigo-300 hover:shadow active:cursor-grabbing lg:w-full"
            title={`Drag onto the canvas to add a ${label.toLowerCase()} section`}
        >
            <svg viewBox="0 0 48 32" width={44} height={30}>
                {type === 'rectangle' ? (
                    <rect x={6} y={4} width={36} height={24} rx={2} fill="#2b5b84" stroke="#7fb3d9" strokeWidth={2} />
                ) : (
                    <polygon points="14,4 34,4 42,28 6,28" fill="#2b5b84" stroke="#7fb3d9" strokeWidth={2} />
                )}
            </svg>
            <span className="text-xs font-medium text-slate-700">{label}</span>
        </div>
    )
}

// Top-right zoom cluster - buttons on top of the existing wheel-zoom/drag-pan,
// which keeps working unchanged alongside these. SeatMap.tsx/BookingSeatMap.tsx
// each keep their own copy since these components don't otherwise share a UI
// module.
function ZoomControls({ onZoomIn, onZoomOut, onReset }: { onZoomIn: () => void; onZoomOut: () => void; onReset: () => void }) {
    const buttonStyle: CSSProperties = {
        width: 28,
        height: 28,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'rgba(0,0,0,0.7)',
        color: '#fff',
        border: '1px solid rgba(255,255,255,0.25)',
        borderRadius: 6,
        fontSize: 15,
        lineHeight: 1,
        cursor: 'pointer',
    }
    return (
        <div style={{ position: 'absolute', top: 8, right: 8, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <button type="button" className="zoom-btn" onClick={onZoomIn} style={buttonStyle} title="Mărește"
                    aria-label="Mărește">
                +
            </button>
            <button type="button" className="zoom-btn" onClick={onZoomOut} style={buttonStyle} title="Micșorează"
                    aria-label="Micșorează">
                −
            </button>
            <button type="button" className="zoom-btn" onClick={onReset} style={buttonStyle}
                    title="Resetează vizualizarea" aria-label="Resetează vizualizarea">
                ⟳
            </button>
        </div>
    )
}

// Resize/rotate handles for the selected section - always a Rectangle/
// Trapezoid, since palette placement is the only way to create a section and
// mirroring preserves `shape` (see mirrorShape); its parameters are known
// well enough to guarantee a drag can't deform it into something else.
function SectionHandles({
    section,
    onHandleMouseDown,
}: {
    section: EditorSection
    onHandleMouseDown: (event: ReactMouseEvent, kind: HandleKind) => void
}) {
    const handleProps = (kind: HandleKind) => ({
        className: 'panzoom-exclude',
        onMouseDown: (event: ReactMouseEvent<SVGCircleElement>) => onHandleMouseDown(event, kind),
        onClick: (event: ReactMouseEvent<SVGCircleElement>) => event.stopPropagation(),
        style: { cursor: 'grab' },
    })

    const shape = section.shape
    const dir = directionOf(shape.angle)
    const perp = perpendicularOf(shape.angle)
    const front = shape.anchor
    const back = shapeBack(shape)
    const bottomWidth = shape.type === 'rectangle' ? shape.width : shape.bottomWidth
    const topWidth = shape.type === 'rectangle' ? shape.width : shape.topWidth

    const shapeHandles: { pos: Point; kind: HandleKind }[] = [
        {
            pos: { x: front.x + (perp.x * bottomWidth) / 2, y: front.y + (perp.y * bottomWidth) / 2 },
            kind: shape.type === 'rectangle' ? 'width' : 'bottomWidth',
        },
        { pos: back, kind: 'length' },
    ]
    if (shape.type === 'trapezoid') {
        shapeHandles.push({
            pos: { x: back.x + (perp.x * topWidth) / 2, y: back.y + (perp.y * topWidth) / 2 },
            kind: 'topWidth',
        })
    }
    const rotateHandlePos: Point = { x: back.x + dir.x * ROTATE_HANDLE_DISTANCE, y: back.y + dir.y * ROTATE_HANDLE_DISTANCE }

    return (
        <>
            {shapeHandles.map(({ pos, kind }) => (
                <circle key={kind} cx={pos.x} cy={pos.y} r={HANDLE_RADIUS} fill={HANDLE_FILL} stroke={HANDLE_STROKE} strokeWidth={1.5} {...handleProps(kind)} />
            ))}
            <circle
                cx={rotateHandlePos.x}
                cy={rotateHandlePos.y}
                r={ROTATE_HANDLE_RADIUS}
                fill="none"
                stroke={HANDLE_FILL}
                strokeWidth={2}
                {...handleProps('rotate')}
            />
        </>
    )
}
