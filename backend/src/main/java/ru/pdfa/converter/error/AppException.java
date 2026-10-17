package ru.pdfa.converter.error;

import java.util.Map;

/**
 * Класс системной ошибки приложения
 */
public class AppException extends RuntimeException {
    private final ErrorCode code;
    private final Map<String, Object> details;

    private AppException(ErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = (details == null) ? Map.of() : Map.copyOf(details);
    }

    /**
     * Сборка системной ошибки
     * @param code код ошибки
     * @param message сообщение к ошибке
     * @param details детали ошибки
     * @return собранная системная ошибка
     */
    public static AppException build(ErrorCode code, String message, Map<String, Object> details) {
        return new AppException(code, message, details, null);
    }

    public static AppException build(ErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        return new AppException(code, message, details, cause);
    }

    /**
     * Сборка системной ошибки с исходной причиной
     * @param code код ошибки
     * @param message сообщение к ошибке
     * @param cause исходная причина
     * @return собранная системная ошибка
     */
    public static AppException build(ErrorCode code, String message, Throwable cause) {
        return new AppException(code, message, null, cause);
    }

    /**
     * Сборка системной ошибки
     * @param code код ошибки
     * @param message сообщение к ошибке
     * @return собранная системная ошибка
     */
    public static AppException build(ErrorCode code, String message) {
        return new AppException(code, message, null, null);
    }

    /**
     * Сборка системной ошибки
     * @param code код ошибки
     * @return собранная системная ошибка
     */
    public static AppException build(ErrorCode code) {
        return new AppException(code, null, null, null);
    }

    /* Getters */
    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
