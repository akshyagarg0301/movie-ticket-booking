package com.example.cinema;

final class Pagination {
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;
    static final int DEFAULT_OFFSET = 0;
    static final String DEFAULT_LIMIT_VALUE = "" + DEFAULT_LIMIT;
    static final String DEFAULT_OFFSET_VALUE = "" + DEFAULT_OFFSET;

    static void validate(int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT || offset < 0) {
            throw ApiException.badRequest(
                    "limit must be 1.." + MAX_LIMIT + " and offset must be nonnegative");
        }
    }

    private Pagination() {}
}
