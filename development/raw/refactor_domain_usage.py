from pathlib import Path
java=Path('outputs/movie-ticket-booking/src/main/java/com/example/cinema')
def edit(name, changes):
 p=java/(name+'.java'); s=p.read_text()
 for old,new in changes:
  if old not in s: raise SystemExit(f'Missing replacement in {name}: {old}')
  s=s.replace(old,new)
 p.write_text(s)
edit('Bookings',[
 ('if (holdMinutes < 1 || holdMinutes > 30) throw new IllegalArgumentException("Hold duration must be 1..30 minutes");', 'if (holdMinutes < BookingRules.MIN_HOLD_MINUTES || holdMinutes > BookingRules.MAX_HOLD_MINUTES)\n            throw new IllegalArgumentException("Hold duration must be " + BookingRules.MIN_HOLD_MINUTES + ".." + BookingRules.MAX_HOLD_MINUTES + " minutes");'),
 ("SELECT s.label, s.tier, CASE WHEN b.status = 'CONFIRMED' THEN 'BOOKED'\n              WHEN b.status = 'HELD' AND b.expires_at > ? THEN 'HELD' ELSE 'AVAILABLE' END AS availability", "SELECT s.label, s.tier, CASE WHEN b.status = ? THEN ?\n              WHEN b.status = ? AND b.expires_at > ? THEN ? ELSE ? END AS availability"),
 ('""", clock.instant(), showId);', '""", BookingStatus.CONFIRMED.name(), SeatAvailability.BOOKED.name(), BookingStatus.HELD.name(),\n                clock.instant(), SeatAvailability.HELD.name(), SeatAvailability.AVAILABLE.name(), showId);'),
 ('string(show, STATUS).equals("OPEN")', 'enumValue(show, STATUS, ShowStatus.class) == ShowStatus.OPEN'),
 ('row.put(CURRENCY, "INR");', 'row.put(CURRENCY, BookingRules.CURRENCY.getCurrencyCode());'),
 ('row.put(AVAILABILITY, "UNAVAILABLE")', 'row.put(AVAILABILITY, SeatAvailability.UNAVAILABLE.name())'),
 ('seatPrice(show, string(row, TIER))','seatPrice(show, enumValue(row, TIER, SeatTier.class))'),
 ('seatPrice(show, string(seat, TIER))','seatPrice(show, enumValue(seat, TIER, SeatTier.class))'),
 ('now.plusSeconds(holdMinutes * 60L)', 'now.plus(Duration.ofMinutes(holdMinutes))'),
 ("VALUES (?, ?, ?, 'HELD', ?, ?, ?, ?, ?, ?, ?, ?)", 'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'),
 ('""", id, username, request.showId(), now, expiry, subtotal, discount, subtotal - discount,', '""", id, username, request.showId(), BookingStatus.HELD.name(), now, expiry, subtotal, discount, subtotal - discount,'),
 ('private long seatPrice(Map<String, Object> show, String tier)', 'private long seatPrice(Map<String, Object> show, SeatTier tier)'),
 ('tier.equals("PREMIUM")', 'tier == SeatTier.PREMIUM'),
 ("(paid = TRUE OR (status = 'HELD' AND expires_at > ?))", '(paid = TRUE OR (status = ? AND expires_at > ?))'),
 ('""", Integer.class, code, now);', '""", Integer.class, code, BookingStatus.HELD.name(), now);'),
 ('String outcome = request.token().equals("tok_success") ? "SUCCEEDED" : "DECLINED";', 'PaymentOutcome outcome = request.token().outcome();'),
 ('!outcome.equals(payment.get(OUTCOME))','outcome != enumValue(payment, OUTCOME, PaymentOutcome.class)'),
 ('!string(booking, STATUS).equals("HELD")','enumValue(booking, STATUS, BookingStatus.class) != BookingStatus.HELD'),
 ('request.idempotencyKey(), outcome, booking.get(TOTAL)', 'request.idempotencyKey(), outcome.name(), booking.get(TOTAL)'),
 ('outcome.equals("SUCCEEDED")', 'outcome == PaymentOutcome.SUCCEEDED'),
 ('"UPDATE booking SET status = \'CONFIRMED\', paid = TRUE WHERE id = ?", id', '"UPDATE booking SET status = ?, paid = TRUE WHERE id = ?", BookingStatus.CONFIRMED.name(), id'),
 ('enqueue(id, "CONFIRMATION",', 'enqueue(id, NotificationType.CONFIRMATION,'),
 ('instant(show, STARTS_AT).minusSeconds(3600)', 'instant(show, STARTS_AT).minus(BookingRules.REMINDER_LEAD_TIME)'),
 ('enqueue(id, "REMINDER",', 'enqueue(id, NotificationType.REMINDER,'),
 ('if (Set.of("CANCELLED", "EXPIRED").contains(string(booking, STATUS))) return view(id, username);', 'BookingStatus status = enumValue(booking, STATUS, BookingStatus.class);\n        if (status == BookingStatus.CANCELLED || status == BookingStatus.EXPIRED) return view(id, username);'),
 ('string(show, STATUS).equals("CANCELLED")', 'enumValue(show, STATUS, ShowStatus.class) == ShowStatus.CANCELLED'),
 ('"SELECT * FROM booking WHERE show_id = ? AND status IN (\'HELD\', \'CONFIRMED\')", showId', '"SELECT * FROM booking WHERE show_id = ? AND status IN (?, ?)",\n                showId, BookingStatus.HELD.name(), BookingStatus.CONFIRMED.name()'),
 ('"UPDATE movie_show SET status = \'CANCELLED\' WHERE id = ?", showId', '"UPDATE movie_show SET status = ? WHERE id = ?", ShowStatus.CANCELLED.name(), showId'),
 ('"UPDATE booking SET status = \'CANCELLED\' WHERE id = ?", id', '"UPDATE booking SET status = ? WHERE id = ?", BookingStatus.CANCELLED.name(), id'),
 ('"DELETE FROM notification WHERE booking_id = ? AND kind = \'REMINDER\' AND delivered_at IS NULL", id', '"DELETE FROM notification WHERE booking_id = ? AND kind = ? AND delivered_at IS NULL", id, NotificationType.REMINDER.name()'),
 ('enqueue((UUID) id, "CANCELLATION", "Booking cancelled. Refund: " + refund + " paise.",', 'enqueue((UUID) id, NotificationType.CANCELLATION, "Booking cancelled. Refund: " + refund + " " + BookingRules.MINOR_UNIT_NAME + ".",'),
 ("(SELECT id FROM booking WHERE show_id = ? AND status = 'HELD' AND expires_at <= ?)", '(SELECT id FROM booking WHERE show_id = ? AND status = ? AND expires_at <= ?)'),
 ('""", showId, showId, now);', '""", showId, showId, BookingStatus.HELD.name(), now);'),
 ('"UPDATE booking SET status = \'EXPIRED\' WHERE show_id = ? AND status = \'HELD\' AND expires_at <= ?", showId, now', '"UPDATE booking SET status = ? WHERE show_id = ? AND status = ? AND expires_at <= ?",\n            BookingStatus.EXPIRED.name(), showId, BookingStatus.HELD.name(), now'),
 ('private void enqueue(UUID id, String kind, String message, Instant due)', 'private void enqueue(UUID id, NotificationType kind, String message, Instant due)'),
 ('UUID.randomUUID(), id, kind, message, due', 'UUID.randomUUID(), id, kind.name(), message, due'),
 ('!enumValue(show, STATUS, ShowStatus.class) == ShowStatus.OPEN', 'enumValue(show, STATUS, ShowStatus.class) != ShowStatus.OPEN'),
 ('string(booking, STATUS).equals("HELD")', 'enumValue(booking, STATUS, BookingStatus.class) == BookingStatus.HELD'),
 ('booking.put(STATUS, "EXPIRED")', 'booking.put(STATUS, BookingStatus.EXPIRED.name())'),
 ('booking.put(CURRENCY, "INR")', 'booking.put(CURRENCY, BookingRules.CURRENCY.getCurrencyCode())'),
])
edit('Catalog',[
 ("WHERE s.status = 'OPEN' AND s.starts_at > ?", 'WHERE s.status = ? AND s.starts_at > ?'),
 ('args.add(clock.instant());','args.add(ShowStatus.OPEN.name());\n        args.add(clock.instant());'),
 ('!string(show, STATUS).equals("OPEN")','enumValue(show, STATUS, ShowStatus.class) != ShowStatus.OPEN'),
 ('weekend_markup, policy_id)\n            VALUES (?, ?, ?, ?, ?, ?, ?, ?)', 'weekend_markup, policy_id, status)\n            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'),
 ('p.weekendMarkup(), p.policyId());','p.weekendMarkup(), p.policyId(), ShowStatus.OPEN.name());'),
 ("WHERE screen_id = ? AND status = 'OPEN'", 'WHERE screen_id = ? AND status = ?'),
 ('Integer.class, request.screenId(), request.endsAt()', 'Integer.class, request.screenId(), ShowStatus.OPEN.name(), request.endsAt()'),
])
edit('Notifications',[
 ('if ("REMINDER".equals(notification.get(KIND)) && !"CONFIRMED".equals(booking.get(STATUS)))',
  'if (Db.enumValue(notification, KIND, NotificationType.class) == NotificationType.REMINDER\n                && Db.enumValue(booking, STATUS, BookingStatus.class) != BookingStatus.CONFIRMED)'),
])
edit('BookingController',[
 ('length > 72', 'length > ValidationRules.MAX_PASSWORD_BYTES'),
 ('"Password must be at most 72 UTF-8 bytes"','"Password must be at most " + ValidationRules.MAX_PASSWORD_BYTES + " UTF-8 bytes"'),
 ('"INSERT INTO app_user VALUES (?, ?, \'CUSTOMER\')", request.username(), passwords.encode(request.password())',
  '"INSERT INTO app_user VALUES (?, ?, ?)", request.username(), passwords.encode(request.password()), Role.CUSTOMER.name()'),
 ('ROLE, "CUSTOMER"', 'ROLE, Role.CUSTOMER.name()'),
])
edit('Security',[
 ('import org.springframework.http.HttpMethod;', 'import org.springframework.http.HttpMethod;\nimport org.springframework.http.HttpHeaders;\nimport org.springframework.http.HttpStatus;\nimport org.springframework.http.MediaType;\nimport org.springframework.http.ProblemDetail;'),
 ('import java.util.Map;\n',''),
 ('class Security {', 'class Security {\n    private static final String BASIC_AUTH_CHALLENGE = "Basic realm=cinema";'),
 ('.roles(Db.string(row, ROLE))', '.roles(Db.enumValue(row, ROLE, Role.class).name())'),
 ('"/api/customers"', 'ApiPaths.ROOT + ApiPaths.CUSTOMERS'),
 ('"/api/cities", "/api/theaters", "/api/shows", "/api/shows/*", "/api/shows/*/seats"',
  'ApiPaths.ROOT + ApiPaths.CITIES, ApiPaths.ROOT + ApiPaths.THEATERS,\n                    ApiPaths.ROOT + ApiPaths.SHOWS, ApiPaths.ROOT + ApiPaths.SHOW, ApiPaths.ROOT + ApiPaths.SHOW_SEATS'),
 ('"/api/admin/**"', 'ApiPaths.ROOT + ApiPaths.ADMIN + ApiPaths.DESCENDANTS'),
 ('"/api/bookings/**", "/api/notifications"', 'ApiPaths.ROOT + ApiPaths.BOOKINGS + ApiPaths.DESCENDANTS, ApiPaths.ROOT + ApiPaths.NOTIFICATIONS'),
 ('.hasRole("ADMIN")', '.hasRole(Role.ADMIN.name())'),
 ('.hasRole("CUSTOMER")', '.hasRole(Role.CUSTOMER.name())'),
 ('res.setStatus(401); res.setHeader("WWW-Authenticate", "Basic realm=cinema");', 'res.setStatus(HttpStatus.UNAUTHORIZED.value()); res.setHeader(HttpHeaders.WWW_AUTHENTICATE, BASIC_AUTH_CHALLENGE);'),
 ('res.setStatus(403);', 'res.setStatus(HttpStatus.FORBIDDEN.value());'),
 ('res.setContentType("application/problem+json")', 'res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)'),
 ('Map.of(STATUS, 401, TITLE, "Unauthorized", "detail", "Valid credentials are required.")', 'ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Valid credentials are required.")'),
 ('Map.of(STATUS, 403, TITLE, "Forbidden", "detail", "Your role cannot perform this action.")', 'ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Your role cannot perform this action.")'),
 ('"SELECT COUNT(*) FROM app_user WHERE role = \'ADMIN\'", Integer.class', '"SELECT COUNT(*) FROM app_user WHERE role = ?", Integer.class, Role.ADMIN.name()'),
 ('username.matches("[a-zA-Z0-9_.-]{3,50}")', 'username.matches(ValidationRules.USERNAME_PATTERN)'),
 ('password.length() < 10', 'password.length() < ValidationRules.MIN_PASSWORD_LENGTH'),
 ('length > 72', 'length > ValidationRules.MAX_PASSWORD_BYTES'),
 ('"Set ADMIN_PASSWORD (10+ characters, at most 72 UTF-8 bytes) before the first start."', '"Set ADMIN_PASSWORD (" + ValidationRules.MIN_PASSWORD_LENGTH + "+ characters, at most "\n                        + ValidationRules.MAX_PASSWORD_BYTES + " UTF-8 bytes) before the first start."'),
 ('"INSERT INTO app_user VALUES (?, ?, \'ADMIN\')", username, passwords.encode(password)', '"INSERT INTO app_user VALUES (?, ?, ?)", username, passwords.encode(password), Role.ADMIN.name()'),
])
edit('Jobs',[
 ('import java.time.Clock;', 'import java.time.Clock;\nimport java.time.Duration;'),
 ('class Jobs {', '''class Jobs {
    private static final int BATCH_SIZE = 100;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    private static final int MAX_RETRY_EXPONENT = 10;'''),
 ('${cinema.jobs.delay-ms:5000}', '${cinema.jobs.delay-ms}'),
 ('"SELECT DISTINCT show_id FROM booking WHERE status = \'HELD\' AND expires_at <= ? LIMIT 100", clock.instant()',
  '"SELECT DISTINCT show_id FROM booking WHERE status = ? AND expires_at <= ? LIMIT ?",\n                BookingStatus.HELD.name(), clock.instant(), BATCH_SIZE'),
 ('"SELECT id, attempts FROM notification WHERE delivered_at IS NULL AND due_at <= ? ORDER BY due_at LIMIT 100", clock.instant()',
  '"SELECT id, attempts FROM notification WHERE delivered_at IS NULL AND due_at <= ? ORDER BY due_at LIMIT ?", clock.instant(), BATCH_SIZE'),
 ('long delay = Math.min(3600, 5L << Math.min(10, Db.number(row, ATTEMPTS)));',
  'long multiplier = 1L << Math.min(MAX_RETRY_EXPONENT, Db.number(row, ATTEMPTS));\n                Duration delay = INITIAL_RETRY_DELAY.multipliedBy(multiplier);\n                if (delay.compareTo(MAX_RETRY_DELAY) > 0) delay = MAX_RETRY_DELAY;'),
 ('clock.instant().plusSeconds(delay)', 'clock.instant().plus(delay)'),
])
# Constructor calls are typed; HTTP assertions retain literal strings to check the public contract independently.
p=Path('outputs/movie-ticket-booking/src/test/java/com/example/cinema/BookingIntegrationTest.java'); s=p.read_text().replace('Requests.Tier.', 'SeatTier.')
import re
s=re.sub(r'(new Requests\.Payment\([^\n]+), "tok_success"\)',r'\1, PaymentToken.SUCCESS)',s)
s=re.sub(r'(new Requests\.Payment\([^\n]+), "tok_decline"\)',r'\1, PaymentToken.DECLINE)',s)
p.write_text(s)
