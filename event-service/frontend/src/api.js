async function getJson(path) {
  const res = await fetch(path);
  if (!res.ok) {
    throw new Error(`${path} -> HTTP ${res.status}: ${await res.text()}`);
  }
  return res.json();
}

async function postJson(path, body) {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!res.ok) {
    throw new Error(`${path} -> HTTP ${res.status}: ${await res.text()}`);
  }
  return res.json();
}

export function getVenueLayout(venueId) {
  return getJson(`/venues/${venueId}/layout`);
}

// Matches VenueDrawRequestDTO on the backend: {name, address,
// sections:[{name, points:[{x,y}]}]} - raw drawn polygons, no computed
// geometry. SectionGeometryCalculator runs server-side inside seedVenue().
export function saveVenue(venue) {
  return postJson('/admin/venues', venue);
}

const SEATS_PAGE_SIZE = 200;

// Backend paginates /sections/{id}/seats (see VenueLayoutService.getSeats) so
// no single request/response is unbounded for a huge section. This yields
// each page's seats as they arrive, so the caller can render incrementally
// instead of waiting on the whole section to load.
export async function* getSectionSeatPages(sectionId, size = SEATS_PAGE_SIZE) {
  let page = 0;
  while (true) {
    const pageDto = await getJson(`/sections/${sectionId}/seats?page=${page}&size=${size}`);
    yield pageDto.content;
    if (page >= pageDto.totalPages - 1) break;
    page += 1;
  }
}
