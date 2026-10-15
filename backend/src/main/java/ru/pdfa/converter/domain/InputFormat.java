package ru.pdfa.converter.domain;

import java.util.Optional;

/**
 * Разрешённые входные форматы для конвертации
 */
public enum InputFormat {
    DOC("doc"),
    DOCX("docx"),
    XLS("xls"),
    XLSX("xlsx"),
    PPTX("pptx"),
    PDF("pdf")
    ;

    private final String extension;

    InputFormat(String extension) {
        this.extension = extension;
    }

    /**
     * Получение строкового представления расширения формата
     * @return строковое представление расширения формата
     */
    public String toExtension() {
        return extension;
    }

    /**
     * Получение формата из строкового представления его расширения
     * @param extension строковое представление расширения формата
     * @return искомый формат, если получилось извлечь из строкового расширения.
     * Иначе {@code Optional.empty()}
     */
    public static Optional<InputFormat> fromExtension(String extension) {
        for (InputFormat format : values()) {
            if (format.extension.equalsIgnoreCase(extension)) {
                return Optional.of(format);
            }
        }

        return Optional.empty();
    }
}
