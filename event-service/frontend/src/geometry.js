// Mirrors the backend's SectionGeometryCalculator convention: a section's
// `angle`/`origin`/`rowSpacing` (from the layout endpoint) describe where
// row `rowStart` sits and how far apart rows/seats are; individual seat
// positions are only ever needed once a section is expanded, so they're
// computed here rather than sent over the wire for every seat.
export function seatPosition(section, row, seatNumber) {
  const { angle, originX, originY, rowSpacing, rowStart, seatStart, seatEnd } = section;
  const mid = (seatStart + seatEnd) / 2;
  const rowOffset = (row - rowStart) * rowSpacing;
  const perpOffset = (seatNumber - mid) * rowSpacing;

  // Rows extend away from the stage along `angle`; seats within a row spread
  // along the perpendicular axis - the same rotate-by-angle step
  // SectionGeometryCalculator applies server-side to a whole polygon,
  // applied here to a single (rowOffset, perpOffset) point instead.
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);
  return {
    x: originX + rowOffset * cos - perpOffset * sin,
    y: originY + rowOffset * sin + perpOffset * cos
  };
}

// Bounding box of a section's drawn outline - used for the SVG viewBox and
// for zooming a section to fill the viewport. This is a viewport-math
// helper only; the outline itself (not this box) is what actually gets
// rendered - see SeatMap.jsx's <polygon points={sectionPolygonPoints(...)}>.
export function sectionBoundingBox(section) {
  const xs = section.vertices.map((v) => v.x);
  const ys = section.vertices.map((v) => v.y);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);

  return { x: minX, y: minY, width: maxX - minX, height: maxY - minY };
}

// Centroid of a section's drawn outline - used to center its name label.
// Same convention SectionGeometryCalculator uses server-side to derive angle.
export function sectionCentroid(section) {
  const sum = section.vertices.reduce((acc, v) => ({ x: acc.x + v.x, y: acc.y + v.y }), { x: 0, y: 0 });
  return { x: sum.x / section.vertices.length, y: sum.y / section.vertices.length };
}

// SVG <polygon points="..."> attribute value from a section's drawn vertices.
export function sectionPolygonPoints(section) {
  return section.vertices.map((v) => `${v.x},${v.y}`).join(' ');
}
