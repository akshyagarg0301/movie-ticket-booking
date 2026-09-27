package com.example.cinema;

import org.springframework.http.HttpStatus;

class ApiException extends RuntimeException {
    final HttpStatus status;
    ApiException(HttpStatus status, String message) { super(message); this.status = status; }
    static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
    static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "Resource not found"); }
}
