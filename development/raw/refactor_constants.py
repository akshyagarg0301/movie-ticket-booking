from pathlib import Path
root=Path('outputs/movie-ticket-booking')
java=root/'src/main/java/com/example/cinema'
def write(name, content):
 (java/name).write_text(content.strip()+'\n')
for name, values in {
 'BookingStatus': 'HELD, CONFIRMED, EXPIRED, CANCELLED',
 'ShowStatus': 'OPEN, CANCELLED',
 'SeatTier': 'REGULAR, PREMIUM',
 'SeatAvailability': 'AVAILABLE, HELD, BOOKED, UNAVAILABLE',
 'Role': 'ADMIN, CUSTOMER',
 'PaymentOutcome': 'SUCCEEDED, DECLINED',
 'NotificationType': 'CONFIRMATION, REMINDER, CANCELLATION',
}.items():
 write(name+'.java',f'package com.example.cinema;\n\nenum {name} {{\n    {values}\n}}')
write('PaymentToken.java','''package com.example.cinema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Tokens accepted by the local payment simulator. */
enum PaymentToken {
    SUCCESS("tok_success", PaymentOutcome.SUCCEEDED),
    DECLINE("tok_decline", PaymentOutcome.DECLINED);

    private final String value;
    private final PaymentOutcome outcome;

    PaymentToken(String value, PaymentOutcome outcome) {
        this.value = value;
        this.outcome = outcome;
    }

    @JsonValue
    public String value() { return value; }

    PaymentOutcome outcome() { return outcome; }

    @JsonCreator
    public static PaymentToken fromValue(String value) {
        for (PaymentToken token : values()) {
            if (token.value.equals(value)) return token;
        }
        throw new IllegalArgumentException("Unknown payment simulator token");
    }
}
''')
write('ValidationRules.java','''package com.example.cinema;

final class ValidationRules {
    static final int MIN_USERNAME_LENGTH = 3;
    static final int MAX_USERNAME_LENGTH = 50;
    static final int MIN_PASSWORD_LENGTH = 10;
    static final int MAX_PASSWORD_BYTES = 72;
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_TIMEZONE_LENGTH = 60;
    static final int MAX_TITLE_LENGTH = 160;
    static final int MAX_SEAT_LABEL_LENGTH = 10;
    static final int MAX_LAYOUT_SEATS = 1_000;
    static final int MAX_BOOKING_SEATS = 10;
    static final int MAX_REFUND_CUTOFF_MINUTES = 10_080;
    static final long MAX_SEAT_PRICE = 10_000_000;
    static final long MAX_DISCOUNT_VALUE = 100_000_000;
    static final int MIN_DISCOUNT_CODE_LENGTH = 3;
    static final int MAX_DISCOUNT_CODE_LENGTH = 30;
    static final int MIN_IDEMPOTENCY_KEY_LENGTH = 8;
    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    static final String USERNAME_PATTERN = "[a-zA-Z0-9_.-]{" + MIN_USERNAME_LENGTH + "," + MAX_USERNAME_LENGTH + "}";
    static final String SEAT_LABEL_PATTERN = "[A-Z][A-Z0-9-]{0," + (MAX_SEAT_LABEL_LENGTH - 1) + "}";
    static final String DISCOUNT_CODE_PATTERN = "[A-Z0-9]{" + MIN_DISCOUNT_CODE_LENGTH + "," + MAX_DISCOUNT_CODE_LENGTH + "}";
    static final String IDEMPOTENCY_KEY_PATTERN = "[A-Za-z0-9_-]{" + MIN_IDEMPOTENCY_KEY_LENGTH + "," + MAX_IDEMPOTENCY_KEY_LENGTH + "}";

    private ValidationRules() {}
}
''')
write('BookingRules.java','''package com.example.cinema;

import java.time.Duration;
import java.util.Currency;

final class BookingRules {
    static final Currency CURRENCY = Currency.getInstance("INR");
    static final String MINOR_UNIT_NAME = "paise";
    static final int FULL_PERCENT = 100;
    static final int MIN_HOLD_MINUTES = 1;
    static final int MAX_HOLD_MINUTES = 30;
    static final Duration REMINDER_LEAD_TIME = Duration.ofHours(1);

    private BookingRules() {}
}
''')
write('Pagination.java','''package com.example.cinema;

final class Pagination {
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;
    static final int DEFAULT_OFFSET = 0;
    static final String DEFAULT_LIMIT_VALUE = "" + DEFAULT_LIMIT;
    static final String DEFAULT_OFFSET_VALUE = "" + DEFAULT_OFFSET;

    static void validate(int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT || offset < 0)
            throw ApiException.badRequest("limit must be 1.." + MAX_LIMIT + " and offset must be nonnegative");
    }

    private Pagination() {}
}
''')
write('ApiPaths.java','''package com.example.cinema;

final class ApiPaths {
    static final String ROOT = "/api";
    static final String ADMIN = "/admin";
    static final String DESCENDANTS = "/**";
    static final String BY_ID = "/{id}";
    static final String CUSTOMERS = "/customers";
    static final String CITIES = "/cities";
    static final String THEATERS = "/theaters";
    static final String SCREENS = "/screens";
    static final String SHOWS = "/shows";
    static final String SEATS = "/seats";
    static final String REFUND_POLICIES = "/refund-policies";
    static final String DISCOUNTS = "/discounts";
    static final String BOOKINGS = "/bookings";
    static final String NOTIFICATIONS = "/notifications";
    static final String CANCEL = "/cancel";
    static final String PRICING = "/pricing";
    static final String PAYMENTS = "/payments";

    static final String SHOW = SHOWS + BY_ID;
    static final String SHOW_SEATS = SHOW + SEATS;
    static final String BOOKING = BOOKINGS + BY_ID;
    static final String BOOKING_PAYMENTS = BOOKING + PAYMENTS;
    static final String BOOKING_CANCEL = BOOKING + CANCEL;
    static final String ADMIN_CITIES = ADMIN + CITIES;
    static final String ADMIN_CITY = ADMIN_CITIES + BY_ID;
    static final String ADMIN_THEATERS = ADMIN + THEATERS;
    static final String ADMIN_THEATER = ADMIN_THEATERS + BY_ID;
    static final String ADMIN_SCREENS = ADMIN + SCREENS;
    static final String ADMIN_SCREEN = ADMIN_SCREENS + BY_ID;
    static final String ADMIN_SCREEN_SEATS = ADMIN_SCREEN + SEATS;
    static final String ADMIN_REFUND_POLICIES = ADMIN + REFUND_POLICIES;
    static final String ADMIN_REFUND_POLICY = ADMIN_REFUND_POLICIES + BY_ID;
    static final String ADMIN_SHOWS = ADMIN + SHOWS;
    static final String ADMIN_SHOW = ADMIN_SHOWS + BY_ID;
    static final String ADMIN_SHOW_PRICING = ADMIN_SHOW + PRICING;
    static final String ADMIN_SHOW_CANCEL = ADMIN_SHOW + CANCEL;
    static final String ADMIN_DISCOUNTS = ADMIN + DISCOUNTS;
    static final String ADMIN_DISCOUNT = ADMIN_DISCOUNTS + "/{code}";

    private ApiPaths() {}
}
''')
# These keys are the shared JDBC aliases and response fields, not SQL fragments.
fields=['id','username','passwordHash','role','status','showId','screenId','theaterId','cityId','bookingId',
 'startsAt','expiresAt','timezone','policyId','label','tier','price','currency','availability',
 'regularPrice','premiumPrice','weekendMarkup','cutoffMinutes','refundPercent','refundCutoff',
 'enabled','minSpend','maxUses','percent','maxDiscount','outcome','total','paid','title','kind',
 'deliveredAt','dueAt','message','attempts','payment','booking','seats','payments','refund']
import re
constants={key:re.sub(r'(?<!^)(?=[A-Z])','_',key).upper() for key in fields}
write('ApiFields.java', 'package com.example.cinema;\n\n/** Column aliases and JSON keys shared by the JDBC-backed API views. */\nfinal class ApiFields {\n'+
 '\n'.join(f'    static final String {name} = "{key}";' for key,name in constants.items())+'\n\n    private ApiFields() {}\n}')
# Shared key literals become constants only in production Java; test assertions keep explicit wire values.
for name in ['Bookings','Catalog','CatalogController','BookingController','Db','Security','Jobs','Notifications']:
 p=java/(name+'.java'); s=p.read_text()
 for key,constant in constants.items(): s=s.replace('"'+key+'"',constant)
 s=s.replace('package com.example.cinema;','package com.example.cinema;\n\nimport static com.example.cinema.ApiFields.*;')
 p.write_text(s)
# Paths in controller annotations share exactly the same definitions used by security.
paths={'/api':'ROOT','/customers':'CUSTOMERS','/cities':'CITIES','/theaters':'THEATERS','/shows':'SHOWS','/shows/{id}':'SHOW','/shows/{id}/seats':'SHOW_SEATS',
 '/bookings':'BOOKINGS','/bookings/{id}':'BOOKING','/bookings/{id}/payments':'BOOKING_PAYMENTS','/bookings/{id}/cancel':'BOOKING_CANCEL','/notifications':'NOTIFICATIONS',
 '/admin/cities':'ADMIN_CITIES','/admin/cities/{id}':'ADMIN_CITY','/admin/theaters':'ADMIN_THEATERS','/admin/theaters/{id}':'ADMIN_THEATER',
 '/admin/screens':'ADMIN_SCREENS','/admin/screens/{id}':'ADMIN_SCREEN','/admin/screens/{id}/seats':'ADMIN_SCREEN_SEATS',
 '/admin/refund-policies':'ADMIN_REFUND_POLICIES','/admin/refund-policies/{id}':'ADMIN_REFUND_POLICY',
 '/admin/shows':'ADMIN_SHOWS','/admin/shows/{id}':'ADMIN_SHOW','/admin/shows/{id}/pricing':'ADMIN_SHOW_PRICING','/admin/shows/{id}/cancel':'ADMIN_SHOW_CANCEL',
 '/admin/discounts':'ADMIN_DISCOUNTS','/admin/discounts/{code}':'ADMIN_DISCOUNT'}
for name in ['CatalogController','BookingController']:
 p=java/(name+'.java'); s=p.read_text()
 for path,constant in paths.items(): s=s.replace('"'+path+'"','ApiPaths.'+constant)
 s=s.replace('defaultValue = "50"','defaultValue = Pagination.DEFAULT_LIMIT_VALUE').replace('defaultValue = "0"','defaultValue = Pagination.DEFAULT_OFFSET_VALUE')
 p.write_text(s)
for name in ['Catalog','Bookings','BookingController']:
 p=java/(name+'.java'); s=p.read_text().replace('if (limit < 1 || limit > 100 || offset < 0) throw ApiException.badRequest("limit must be 1..100 and offset must be nonnegative");','Pagination.validate(limit, offset);'); p.write_text(s)
# Keep the API payloads unchanged while making Java callers use domain types.
p=java/'Requests.java'; s=p.read_text()
s=s.replace('import java.util.List;', 'import java.util.List;\nimport static com.example.cinema.ValidationRules.*;\nimport static com.example.cinema.BookingRules.FULL_PERCENT;')
s=s.replace('    enum Tier { REGULAR, PREMIUM }\n','').replace('@NotNull Tier tier','@NotNull SeatTier tier')
for pattern,constant in {'[a-zA-Z0-9_.-]{3,50}':'USERNAME_PATTERN','[A-Z][A-Z0-9-]{0,9}':'SEAT_LABEL_PATTERN','[A-Z0-9]{3,30}':'DISCOUNT_CODE_PATTERN','[A-Za-z0-9_-]{8,100}':'IDEMPOTENCY_KEY_PATTERN'}.items(): s=s.replace('"'+pattern+'"',constant)
s=s.replace('@Size(min = 10, max = 72)','@Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_BYTES)')
for value,constant in [('1000','MAX_LAYOUT_SEATS'),('100','MAX_NAME_LENGTH'),('60','MAX_TIMEZONE_LENGTH'),('160','MAX_TITLE_LENGTH'),('10','MAX_BOOKING_SEATS')]:
 s=s.replace('@Size(max = '+value+')','@Size(max = '+constant+')')
s=s.replace('@Max(10080)','@Max(MAX_REFUND_CUTOFF_MINUTES)').replace('@Max(100000000)','@Max(MAX_DISCOUNT_VALUE)').replace('@Max(10000000)','@Max(MAX_SEAT_PRICE)').replace('@Max(100)','@Max(FULL_PERCENT)')
s=s.replace('@NotNull @Pattern(regexp = "tok_success|tok_decline") String token','@NotNull PaymentToken token')
p.write_text(s)
p=java/'Db.java'; s=p.read_text().replace('    static long number(','''    static <E extends Enum<E>> E enumValue(Map<String, Object> row, String key, Class<E> type) {
        return Enum.valueOf(type, string(row, key));
    }

    static long number('''); p.write_text(s)
p=java/'Pricing.java'; s=p.read_text().replace('import java.time.*;', 'import java.time.*;\nimport static com.example.cinema.BookingRules.FULL_PERCENT;').replace('100 + weekendMarkup','FULL_PERCENT + weekendMarkup').replace('startsAt.minusSeconds(cutoffMinutes * 60L)','startsAt.minus(Duration.ofMinutes(cutoffMinutes))').replace('(amount * percent + 50) / 100','(amount * percent + FULL_PERCENT / 2) / FULL_PERCENT'); p.write_text(s)
