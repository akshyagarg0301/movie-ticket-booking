package com.example.cinema;

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
