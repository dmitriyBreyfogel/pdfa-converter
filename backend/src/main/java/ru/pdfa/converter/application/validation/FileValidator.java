package ru.pdfa.converter.application.validation;

import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Утилитный класс для валидации файла.
 * Валидация файла происходит по пути к нему через метод {@code validate()}
 */
public final class FileValidator {
    private FileValidator() {
        // Util class
    }

    /**
     * Проверяет входной файл: его тип, размер и расширение.
     *
     * @param source путь к проверяемому файлу
     * @throws AppException если файл не прошёл одну из проверок
     */
    public static void validate(Path source) {
        requireRegularFile(source);
        requireNonEmptyFile(source);
        requireSupportedExtension(source);
    }

    /**
     * Проверяет, что путь указывает на существующий обычный файл
     *
     * @param source путь к проверяемому файлу
     * @throws AppException если файл не существует или путь указывает не на обычный файл
     */
    private static void requireRegularFile(Path source) {
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
    private static void requireNonEmptyFile(Path source) {
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

    /**
     * Проверяет наличие и поддержку расширения файла.
     *
     * @param source путь к проверяемому файлу
     * @throws AppException если расширение отсутствует или не поддерживается
     */
    private static void requireSupportedExtension(Path source) {
        String fileName = source.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');

        if (extensionIndex <= 0 || extensionIndex == fileName.length() - 1) {
            throw AppException.build(
                    ErrorCode.INVALID_FILE_EXTENSION,
                    "У входного файла нет расширения"
            );
        }

        String extension = fileName.substring(extensionIndex + 1);
        if (InputFormat.fromExtension(extension).isEmpty()) {
            throw AppException.build(
                    ErrorCode.UNSUPPORTED_EXTENSION,
                    "Расширение входного файла не поддерживается"
            );
        }
    }
}
