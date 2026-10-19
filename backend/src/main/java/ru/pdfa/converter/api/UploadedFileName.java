package ru.pdfa.converter.api;

import org.springframework.web.multipart.MultipartFile;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

/**
 * Извлекает имя файла из multipart-загрузки.
 * Удаляет компоненты пути, переданные клиентом вместе с именем.
 */
final class UploadedFileName {
    private UploadedFileName() {
    }

    /**
     * @param file загруженный файл
     * @return имя файла без компонентов пути
     * @throws AppException если имя отсутствует
     */
    static String from(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw AppException.build(ErrorCode.INVALID_FILE_EXTENSION, "Не указано имя входного файла");
        }

        String normalized = original.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.isBlank()) {
            throw AppException.build(ErrorCode.INVALID_FILE_EXTENSION, "Не указано имя входного файла");
        }
        return name;
    }
}
