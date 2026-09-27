#!/usr/bin/env python3
"""Exercise the running API, including an actual HTTP seat-allocation race."""
import base64
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import json
import os
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import uuid

BASE = os.environ.get('BASE_URL', 'http://localhost:8080')
ADMIN = (os.environ.get('ADMIN_USERNAME', 'admin'), os.environ['ADMIN_PASSWORD'])
SUFFIX = uuid.uuid4().hex[:8]
CUSTOMER = ('demo-' + SUFFIX, 'demo-customer-password')


def call(method, path, body=None, user=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if user:
        headers['Authorization'] = 'Basic ' + base64.b64encode((':'.join(user)).encode()).decode()
    request = Request(BASE + '/api' + path, data=json.dumps(body).encode() if body is not None else None,
                      headers=headers, method=method)
    try:
        with urlopen(request, timeout=20) as response:
            code, raw = response.status, response.read()
    except HTTPError as error:
        code, raw = error.code, error.read()
    result = json.loads(raw) if raw else None
    if expected is not None and code != expected:
        raise AssertionError(f'{method} {path}: expected {expected}, got {code}: {result}')
    return (code, result) if expected is None else result


def main():
    call('POST', '/customers', dict(username=CUSTOMER[0], password=CUSTOMER[1]), expected=201)
    city = call('POST', '/admin/cities', dict(name='Pune ' + SUFFIX, timezone='Asia/Kolkata'), ADMIN, 201)['id']
    theater = call('POST', '/admin/theaters', dict(cityId=city, name='Central Cinema'), ADMIN, 201)['id']
    screen = call('POST', '/admin/screens', dict(theaterId=theater, name='Screen 1'), ADMIN, 201)['id']
    seats = [dict(label=f'{row}{n}', tier='PREMIUM' if row == 'B' else 'REGULAR') for row in 'AB' for n in range(1, 7)]
    call('PUT', f'/admin/screens/{screen}/seats', dict(seats=seats), ADMIN)
    policy = call('POST', '/admin/refund-policies', dict(name='Standard', cutoffMinutes=120, refundPercent=80), ADMIN, 201)['id']
    start = datetime.now(timezone.utc).replace(microsecond=0) + timedelta(days=2)
    show = call('POST', '/admin/shows', dict(screenId=screen, title='Arrival', startsAt=start.isoformat(),
                endsAt=(start + timedelta(hours=2)).isoformat(),
                pricing=dict(regularPrice=10000, premiumPrice=15000, weekendMarkup=20, policyId=policy)), ADMIN, 201)['id']
    code = 'SAVE' + SUFFIX.upper()
    call('POST', '/admin/discounts', dict(code=code, percent=20, maxDiscount=3000, minSpend=10000,
         maxUses=20, expiresAt=start.isoformat()), ADMIN, 201)
    shows = call('GET', f'/shows?cityId={city}')
    assert len(shows) == 1
    print('Created city, theater, screen, layout, refund policy, show and discount.')

    held = call('POST', '/bookings', dict(showId=show, seats=['A1', 'B1'], discountCode=code), CUSTOMER, 201)
    booking = held['id']
    declined = call('POST', f'/bookings/{booking}/payments', dict(idempotencyKey='decline-' + SUFFIX, token='tok_decline'), CUSTOMER)
    assert declined['payment']['outcome'] == 'DECLINED'
    request = dict(idempotencyKey='success-' + SUFFIX, token='tok_success')
    paid = call('POST', f'/bookings/{booking}/payments', request, CUSTOMER)
    retry = call('POST', f'/bookings/{booking}/payments', request, CUSTOMER)
    assert paid['payment']['id'] == retry['payment']['id']
    assert paid['booking']['status'] == 'CONFIRMED'
    print(f'Booked A1 + B1: subtotal={held["subtotal"]}, discount={held["discount"]}, paid={held["total"]} paise.')
    print('Decline, successful retry, and duplicate-payment protection verified.')

    with ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(lambda _: call('POST', '/bookings', dict(showId=show, seats=['A2']), CUSTOMER, None), range(6)))
    assert sum(code == 201 for code, _ in results) == 1, results
    assert sum(code == 409 for code, _ in results) == 5, results
    print('Concurrent HTTP booking: 1 winner, 5 conflicts, no double allocation.')

    cancelled = call('POST', f'/bookings/{booking}/cancel', {}, CUSTOMER)
    assert cancelled['status'] == 'CANCELLED'
    assert cancelled['refund']['amount'] == (held['total'] * 80 + 50) // 100
    retry_cancel = call('POST', f'/bookings/{booking}/cancel', {}, CUSTOMER)
    assert retry_cancel['refund'] == cancelled['refund']
    print(f'Cancellation refunded {cancelled["refund"]["amount"]} paise; retry did not refund twice.')
    call('POST', f'/admin/shows/{show}/cancel', {}, ADMIN)
    assert all(row['availability'] == 'UNAVAILABLE' for row in call('GET', f'/shows/{show}/seats'))

    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        inbox = call('GET', '/notifications', user=CUSTOMER)
        if len(inbox) >= 2:
            break
        time.sleep(1)
    assert any(item['kind'] == 'CANCELLATION' for item in inbox), inbox
    print('Background notifications delivered to the customer inbox.')
    history = call('GET', '/bookings', user=CUSTOMER)
    assert len(history) == 2
    print(json.dumps({'customer': CUSTOMER[0], 'showId': show, 'bookingId': booking,
                      'historyCount': len(history), 'notificationCount': len(inbox)}, indent=2))
    print('All HTTP demo checks passed.')


if __name__ == '__main__':
    main()
