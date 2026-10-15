package ru.pdfa.converter.error;

import org.springframework.http.HttpStatus;

/**
 * Список кодов системных ошибок
 */
public enum ErrorCode {
    INVALID_FILE(HttpStatus.BAD_REQUEST),
    EMPTY_FILE(HttpStatus.BAD_REQUEST),
    FILE_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_EXTENSION(HttpStatus.UNPROCESSABLE_CONTENT),
    ;

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getStatus() {
        return httpStatus;
    }
}
