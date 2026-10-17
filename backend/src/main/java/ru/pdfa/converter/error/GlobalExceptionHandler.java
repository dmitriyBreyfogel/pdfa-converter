package ru.pdfa.converter.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    /**
     * Возвращает ошибку, если multipart-запрос превысил настроенный лимит.
     *
     * @param exception ошибка превышения размера загрузки
     * @return HTTP-ответ с кодом FILE_TOO_LARGE
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<AppErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return handleAppException(AppException.build(
                ErrorCode.FILE_TOO_LARGE,
                "Размер загружаемого файла превышает допустимый"
        ));
    }
}
