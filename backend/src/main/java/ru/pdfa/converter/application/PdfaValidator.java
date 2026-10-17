package ru.pdfa.converter.application;

import ru.pdfa.converter.domain.OutputFormat;

import java.nio.file.Path;

/**
 * Контракт проверки результата конвертации на соответствие PDF/A.
 */
public interface PdfaValidator {

    /**
     * Проверяет PDF по требованиям выбранного формата PDF/A.
     *
     * @param pdf путь к созданному PDF
     * @param target требуемый формат PDF/A
     */
    void validate(Path pdf, OutputFormat target);
}
