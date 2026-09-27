# API reference

Base URL: `http://localhost:8080/api`. Send JSON and use HTTP Basic for protected routes. Request and response field names use camelCase. All money is in INR paise and all timestamps use ISO 8601 with an offset.

## Routes

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/customers` | Public | Register a customer |
| GET | `/cities` | Public | List cities |
| GET | `/theaters?cityId=1` | Public | List/filter theaters |
| GET | `/shows` | Public | Browse future, open shows |
| GET | `/shows/{id}` | Public | Show details |
| GET | `/shows/{id}/seats` | Public | Seat availability and current prices |
| POST | `/bookings` | Customer | Hold 1–10 seats |
| GET | `/bookings` | Customer | Own booking history |
| GET | `/bookings/{id}` | Customer | Own booking, payments and refund |
| POST | `/bookings/{id}/payments` | Customer | Simulate payment and confirm |
| POST | `/bookings/{id}/cancel` | Customer | Cancel and apply refund policy |
| GET | `/notifications` | Customer | Delivered inbox messages |
| POST / PUT | `/admin/cities` / `/admin/cities/{id}` | Admin | Create / rename city |
| POST / PUT | `/admin/theaters` / `/admin/theaters/{id}` | Admin | Create / rename theater |
| POST / PUT | `/admin/screens` / `/admin/screens/{id}` | Admin | Create / rename screen |
| GET | `/admin/screens` | Admin | List screens |
| GET / PUT | `/admin/screens/{id}/seats` | Admin | Read / replace layout |
| GET / POST | `/admin/refund-policies` | Admin | List / create policies |
| PUT | `/admin/refund-policies/{id}` | Admin | Update policy for future holds |
| POST / PUT | `/admin/shows` / `/admin/shows/{id}` | Admin | Schedule / update show |
| PUT | `/admin/shows/{id}/pricing` | Admin | Update prices and policy |
| POST | `/admin/shows/{id}/cancel` | Admin | Cancel show and fully refund active paid bookings |
| GET / POST | `/admin/discounts` | Admin | List / create codes |
| DELETE | `/admin/discounts/{code}` | Admin | Disable a code |

Create responses use 201. Catalog creates return `{"id": 1}`; registration returns username/role; a hold returns the booking. Discount creation returns an empty 201. Updates return an empty 200. Repeated cancellation returns the same booking/refund and does not create another refund.

`GET /shows` accepts optional `cityId`, `theaterId`, `title` (case-insensitive substring), `from` (inclusive) and `to` (exclusive). Results are ordered by start time and ID. Shows, booking history and notifications use `limit` (1–100, default 50) and `offset` (nonnegative, default 0). Small catalog lists are unpaginated. Public browsing excludes cancelled and already-started shows; a known show's detail remains readable for history.

## Create a small catalog

Replace IDs with the ones returned by your requests. Dates must be in the future.

```sh
curl -u "admin:$ADMIN_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"name":"Pune","timezone":"Asia/Kolkata"}' http://localhost:8080/api/admin/cities

curl -u "admin:$ADMIN_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"cityId":1,"name":"Central Cinema"}' http://localhost:8080/api/admin/theaters

curl -u "admin:$ADMIN_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"theaterId":1,"name":"Screen 1"}' http://localhost:8080/api/admin/screens

curl -X PUT -u "admin:$ADMIN_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"seats":[{"label":"A1","tier":"REGULAR"},{"label":"A2","tier":"REGULAR"},{"label":"B1","tier":"PREMIUM"}]}' \
  http://localhost:8080/api/admin/screens/1/seats

curl -u "admin:$ADMIN_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"name":"Standard","cutoffMinutes":120,"refundPercent":80}' \
  http://localhost:8080/api/admin/refund-policies

curl -u "admin:$ADMIN_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"screenId":1,"title":"Arrival","startsAt":"2030-01-05T18:00:00+05:30","endsAt":"2030-01-05T20:00:00+05:30","pricing":{"regularPrice":10000,"premiumPrice":15000,"weekendMarkup":20,"policyId":1}}' \
  http://localhost:8080/api/admin/shows

curl -u "admin:$ADMIN_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"code":"SAVE20","percent":20,"maxDiscount":3000,"minSpend":10000,"maxUses":100,"expiresAt":"2030-01-05T12:30:00Z"}' \
  http://localhost:8080/api/admin/discounts
```

Names are limited to 100 characters (show titles: 160). Use valid IANA timezones, uppercase seat labels of up to 10 characters, and uppercase alphanumeric discount codes of 3–30 characters. Layouts contain at most 1,000 distinct, non-null seats. Prices must be positive and at most 10,000,000 paise per seat. Percentages, duration bounds, date order and referenced resources are validated. Unknown JSON fields are rejected. Seat tiers must be the exact enum names `REGULAR` or `PREMIUM`; numeric ordinals are rejected.

## Customer flow

```sh
curl -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"alice-demo-password"}' http://localhost:8080/api/customers

curl http://localhost:8080/api/shows/1/seats

curl -u alice:alice-demo-password -H 'Content-Type: application/json' \
  -d '{"showId":1,"seats":["A1","B1"],"discountCode":"SAVE20"}' \
  http://localhost:8080/api/bookings
```

Registration requires a 3–50 character username (`A-Z`, `a-z`, digits, `_`, `.` or `-`) and a password of at least ten characters, no more than 72 UTF-8 bytes. Usernames are case-sensitive. The sample credentials above are disposable examples.

A hold response includes:

```json
{
  "id": "a030a5aa-378c-4bb9-8bce-029bcfa1ea92",
  "showId": 1,
  "status": "HELD",
  "expiresAt": "2026-09-27T10:05:00Z",
  "subtotal": 30000,
  "discount": 3000,
  "total": 27000,
  "currency": "INR",
  "seats": [{"label":"A1","price":12000},{"label":"B1","price":18000}],
  "payments": [],
  "refund": null
}
```

This is an abbreviated example; the actual response also includes the owner, creation time, discount code, payment flag and refund snapshot. `expiresAt` is generated by the server, not chosen by the client. A held price is fixed even if the seat map later displays an updated price.

```sh
BOOKING_ID='replace-with-returned-id'
curl -u alice:alice-demo-password -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"checkout-attempt-001","token":"tok_success"}' \
  "http://localhost:8080/api/bookings/$BOOKING_ID/payments"

curl -X POST -u alice:alice-demo-password "http://localhost:8080/api/bookings/$BOOKING_ID/cancel"
curl -u alice:alice-demo-password http://localhost:8080/api/bookings
curl -u alice:alice-demo-password http://localhost:8080/api/notifications
```

Payment returns `{ "payment": { ... }, "booking": { ... } }`. Use `tok_decline` to test a decline. Idempotency keys contain 8–100 alphanumeric, underscore or hyphen characters. Keep the same key when retrying the exact request. A previously successful payment remains successful in its retry response even if the current booking has since been cancelled; the booking section reports current state.

## Errors and boundaries

```json
{"type":"about:blank","title":"Conflict","status":409,"detail":"Seat A1 is no longer available"}
```

- Seats are `AVAILABLE`, `HELD`, `BOOKED`, or `UNAVAILABLE` when a show is closed. No other customer's booking reference is exposed in the public seat map.
- At the exact hold deadline, payment fails. The next competing hold can take those seats immediately.
- Missing or foreign booking IDs return 404. Customers cannot call any admin route.
- Refund values can be zero. An unpaid cancellation has `refund: null` because no payment exists to refund.
- Outbox delivery normally occurs within five seconds. Inbox responses only show delivered messages. There is no email/SMS integration.
