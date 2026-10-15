package ru.pdfa.converter.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Преобразует ошибки приложения в HTTP-ответы.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Возвращает статус из кода ошибки и её данные в теле ответа.
     *
     * @param exception ошибка приложения
     * @return HTTP-ответ с кодом, сообщением и деталями ошибки
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<AppErrorResponse> handleAppException(AppException exception) {
        AppErrorResponse response = new AppErrorResponse(
                exception.getCode(),
                exception.getMessage(),
                exception.getDetails()
        );

        return ResponseEntity.status(exception.getCode().getStatus()).body(response);
    }
}
