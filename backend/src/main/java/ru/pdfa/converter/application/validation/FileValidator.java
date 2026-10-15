package ru.pdfa.converter.application.validation;

import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileValidator {
    private FileValidator() {
        // Util class
    }

    /**
     * Проверяет, что путь указывает на существующий обычный файл
     *
     * @param source путь к проверяемому файлу
     * @throws AppException если файл не существует или путь указывает не на обычный файл
     */
    public static void requireRegularFile(Path source) {
        if (!Files.isRegularFile(source)) {
            throw AppException.build(
                    ErrorCode.INVALID_FILE,
                    "Входной файл не существует или не является обычным файлом"
            );
        }
    }

    /**
     * Проверяет, что путь указывает на непустой файл
     *
     * @param source путь к проверяемому файлу
     * @throws AppException если размер файла равен нулю или размер не удалось определить
     */
    public static void requireNonEmptyFile(Path source) {
        try {
            if (Files.size(source) == 0) {
                throw AppException.build(
                        ErrorCode.EMPTY_FILE,
                        "Входной файл пуст"
                );
            }
        }
        catch (IOException error) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Не удалось определить размер входного файла",
                    error
            );
        }
    }
}
