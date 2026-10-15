package ru.pdfa.converter.error;

import java.util.Map;

/**
 * Тело HTTP-ответа при ошибке приложения.
 *
 * @param code код ошибки
 * @param message описание ошибки
 * @param details дополнительные сведения об ошибке
 */
public record AppErrorResponse(
        ErrorCode code,
        String message,
        Map<String, Object> details
) {
}
