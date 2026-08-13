#!/usr/bin/env bash
#
# Live end-to-end verification of the PDA portal API.
#
# Usage:
#   TOKEN=xxxxx ./scripts/pda_verify.sh            # use an existing bearer token
#   EMAIL=you@store.com PASSWORD=secret ./scripts/pda_verify.sh   # log in first
#   ... add --write to also create + cancel one draft per document type
#
# Read-only by default. With --write it creates a draft PO / return / stock
# adjustment and immediately cancels each, so nothing is left pending.
#
set -u

BASE="${BASE:-https://retail-portal.sspos.co.uk}"
WRITE=0
[[ "${1:-}" == "--write" ]] && WRITE=1

pass=0; fail=0
req() { # method path [json-body]
  local method="$1" path="$2" body="${3:-}"
  local args=(-s -m 30 -w $'\n%{http_code}' -X "$method"
    -H "Accept: application/json" -H "Content-Type: application/json"
    -H "Authorization: Bearer $TOKEN")
  [[ -n "$body" ]] && args+=(-d "$body")
  curl "${args[@]}" "$BASE$path"
}
check() { # label method path [body]
  local out code label="$1"; shift
  out=$(req "$@"); code=$(echo "$out" | tail -1)
  local snippet; snippet=$(echo "$out" | head -1 | cut -c1-120)
  if [[ "$code" =~ ^2 ]]; then pass=$((pass+1)); printf '  ✅ %-28s HTTP %s\n' "$label" "$code" >&2
  else fail=$((fail+1)); printf '  ❌ %-28s HTTP %s | %s\n' "$label" "$code" "$snippet" >&2; fi
  echo "$out"
}

# --- obtain a token if only credentials were given -------------------------
if [[ -z "${TOKEN:-}" ]]; then
  if [[ -n "${EMAIL:-}" && -n "${PASSWORD:-}" ]]; then
    echo "Logging in as $EMAIL ..."
    resp=$(curl -s -m 30 -X POST -H "Accept: application/json" -H "Content-Type: application/json" \
      -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" "$BASE/api/pda/login")
    TOKEN=$(echo "$resp" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
    [[ -z "$TOKEN" ]] && { echo "Login failed: $resp"; exit 1; }
    echo "Got token."
  else
    echo "Set TOKEN=... or EMAIL=... PASSWORD=... (see header)."; exit 1
  fi
fi

echo; echo "== Auth =="
check "GET /me" GET /api/pda/me >/dev/null

echo; echo "== Lists =="
check "GET /purchase-orders"    GET /api/pda/purchase-orders    >/dev/null
check "GET /purchase-returns"   GET /api/pda/purchase-returns   >/dev/null
check "GET /stock-adjustments"  GET /api/pda/stock-adjustments  >/dev/null

if [[ "$WRITE" == 1 ]]; then
  echo; echo "== Write round-trip (create then cancel) =="

  po=$(check "POST /purchase-orders" POST /api/pda/purchase-orders \
    '{"supplier_id":1,"expected_delivery_date":"2026-12-31","items":[{"product_id":1,"quantity_ordered":1,"unit_cost":1.00}]}')
  id=$(echo "$po" | head -1 | sed -n 's/.*"data":{[^}]*"id":\([0-9]*\).*/\1/p')
  [[ -n "$id" ]] && check "POST /purchase-orders/$id/cancel" POST "/api/pda/purchase-orders/$id/cancel" >/dev/null

  pr=$(check "POST /purchase-returns" POST /api/pda/purchase-returns \
    '{"supplier_id":1,"reference_no":"PR-VERIFY","return_reason":"Verify","items":[{"product_id":1,"quantity":1,"cost_price":1.00,"reason":"Verify"}]}')
  id=$(echo "$pr" | head -1 | sed -n 's/.*"data":{[^}]*"id":\([0-9]*\).*/\1/p')
  [[ -n "$id" ]] && check "POST /purchase-returns/$id/cancel" POST "/api/pda/purchase-returns/$id/cancel" >/dev/null

  sa=$(check "POST /stock-adjustments" POST /api/pda/stock-adjustments \
    '{"product_id":1,"adjustment_type":"Stock Increase","direction":"IN","quantity":1,"unit_cost":1.00,"reason":"Verify"}')
  id=$(echo "$sa" | head -1 | sed -n 's/.*"data":{[^}]*"id":\([0-9]*\).*/\1/p')
  [[ -n "$id" ]] && check "POST /stock-adjustments/$id/cancel" POST "/api/pda/stock-adjustments/$id/cancel" >/dev/null
fi

echo; echo "== Result: $pass passed, $fail failed =="
[[ "$fail" == 0 ]]
