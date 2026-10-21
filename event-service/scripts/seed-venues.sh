#!/usr/bin/env bash
set -euo pipefail

EVENT_SERVICE="${EVENT_SERVICE_URL:-http://localhost:8081}"
SECRET="${INTERNAL_SHARED_SECRET:-dev-only-secret}"

if ! curl -s -o /dev/null --max-time 5 "$EVENT_SERVICE/api/events"; then
    echo "event-service nu raspunde pe $EVENT_SERVICE" >&2
    echo "Porneste-l intai - el calculeaza geometria sectiunilor." >&2
    exit 1
fi

post_venue() {
    local name="$1" payload="$2"
    local code
    code=$(curl -s -o /tmp/seed-venue-out.json -w "%{http_code}" \
        -X POST "$EVENT_SERVICE/admin/venues" \
        -H "X-Internal-Secret: $SECRET" \
        -H "Content-Type: application/json" \
        -d "$payload" || echo "000")

    if [ "$code" = "201" ]; then
        local seats
        seats=$(sed -n 's/.*"noSeats":\([0-9]*\).*/\1/p' /tmp/seed-venue-out.json)
        printf '  %-18s OK  %s locuri\n' "$name" "$seats"
    else
        printf '  %-18s ESUAT (HTTP %s)\n' "$name" "$code"
        cat /tmp/seed-venue-out.json
        exit 1
    fi
}

echo "Creez venue-urile pe $EVENT_SERVICE ..."

post_venue "Arena Nationala" '{
  "name": "Arena Nationala",
  "address": "Bd. Basarabia 37, Bucuresti",
  "sections": [
    { "name": "Tribuna Nord", "rows": 10, "seatsPerRow": 20,
      "points": [{"x":-200,"y":150},{"x":200,"y":150},{"x":200,"y":350},{"x":-200,"y":350}] },
    { "name": "Tribuna Sud", "rows": 10, "seatsPerRow": 20,
      "points": [{"x":-200,"y":-350},{"x":200,"y":-350},{"x":200,"y":-150},{"x":-200,"y":-150}] },
    { "name": "Peluza Est", "rows": 8, "seatsPerRow": 15,
      "points": [{"x":150,"y":-150},{"x":350,"y":-150},{"x":350,"y":150},{"x":150,"y":150}] },
    { "name": "Peluza Vest", "rows": 8, "seatsPerRow": 15,
      "points": [{"x":-350,"y":-150},{"x":-150,"y":-150},{"x":-150,"y":150},{"x":-350,"y":150}] }
  ]
}'

post_venue "Sala Palatului" '{
  "name": "Sala Palatului",
  "address": "Str. Ion Campineanu 28, Bucuresti",
  "sections": [
    { "name": "Parter", "rows": 8, "seatsPerRow": 15,
      "points": [{"x":-150,"y":80},{"x":150,"y":80},{"x":150,"y":260},{"x":-150,"y":260}] },
    { "name": "Balcon", "rows": 5, "seatsPerRow": 12,
      "points": [{"x":-120,"y":300},{"x":120,"y":300},{"x":120,"y":420},{"x":-120,"y":420}] }
  ]
}'

post_venue "BT Arena" '{
  "name": "BT Arena",
  "address": "Aleea Stadionului 2, Cluj-Napoca",
  "sections": [
    { "name": "Sector A", "rows": 6, "seatsPerRow": 12,
      "points": [{"x":-160,"y":120},{"x":160,"y":120},{"x":160,"y":280},{"x":-160,"y":280}] },
    { "name": "Sector B", "rows": 6, "seatsPerRow": 12,
      "points": [{"x":140,"y":-120},{"x":300,"y":-120},{"x":300,"y":120},{"x":140,"y":120}] },
    { "name": "Sector C", "rows": 6, "seatsPerRow": 12,
      "points": [{"x":-300,"y":-120},{"x":-140,"y":-120},{"x":-140,"y":120},{"x":-300,"y":120}] }
  ]
}'

# Proportia fiecarui poligon e aleasa apropiat de (rows-1)/(seatsPerRow-1).
# Cu o singura spatiere pentru ambele axe, doar asa grila umple forma; o cutie
# prea adanca fata de numarul de randuri lasa margini late.
post_venue "Sala Polivalenta" '{
  "name": "Sala Polivalenta",
  "address": "Aleea Stadionului 4, Cluj-Napoca",
  "sections": [
    { "name": "Tribuna A", "rows": 12, "seatsPerRow": 24,
      "points": [{"x":-200,"y":150},{"x":200,"y":150},{"x":200,"y":340},{"x":-200,"y":340}] },
    { "name": "Tribuna B", "rows": 12, "seatsPerRow": 24,
      "points": [{"x":-200,"y":-340},{"x":200,"y":-340},{"x":200,"y":-150},{"x":-200,"y":-150}] },
    { "name": "Loja", "rows": 4, "seatsPerRow": 16,
      "points": [{"x":150,"y":-150},{"x":210,"y":-150},{"x":210,"y":150},{"x":150,"y":150}] }
  ]
}'

post_venue "Beraria H" '{
  "name": "Beraria H",
  "address": "Sos. Kiseleff 32, Bucuresti",
  "sections": [
    { "name": "Ring", "rows": 6, "seatsPerRow": 18,
      "points": [{"x":-150,"y":100},{"x":150,"y":100},{"x":150,"y":190},{"x":-150,"y":190}] },
    { "name": "Balcon", "rows": 3, "seatsPerRow": 14,
      "points": [{"x":-130,"y":-180},{"x":130,"y":-180},{"x":130,"y":-140},{"x":-130,"y":-140}] }
  ]
}'

post_venue "Papp Laszlo Arena" '{
  "name": "Papp Laszlo Arena",
  "address": "Stefania ut 2, Budapest",
  "sections": [
    { "name": "Szektor Eszak", "rows": 8, "seatsPerRow": 18,
      "points": [{"x":-200,"y":150},{"x":200,"y":150},{"x":200,"y":315},{"x":-200,"y":315}] },
    { "name": "Szektor Del", "rows": 8, "seatsPerRow": 18,
      "points": [{"x":-200,"y":-315},{"x":200,"y":-315},{"x":200,"y":-150},{"x":-200,"y":-150}] },
    { "name": "Szektor Kelet", "rows": 8, "seatsPerRow": 18,
      "points": [{"x":150,"y":-200},{"x":315,"y":-200},{"x":315,"y":200},{"x":150,"y":200}] },
    { "name": "Szektor Nyugat", "rows": 8, "seatsPerRow": 18,
      "points": [{"x":-315,"y":-200},{"x":-150,"y":-200},{"x":-150,"y":200},{"x":-315,"y":200}] }
  ]
}'

echo
echo "Gata. Acum ruleaza restul datelor:"
echo "  docker exec -i api-gateway-postgres-1 psql -U user -d event_db < scripts/seed-dev-data.sql"
