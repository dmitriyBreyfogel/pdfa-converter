package ru.pdfa.converter.application;

import java.nio.file.Path;

/**
 * Согласует даты создания в метаданных уже созданного PDF/A.
 */
public interface PdfaCreationDateSynchronizer {

    /**
     * Записывает дату создания из PDF Info в XMP.
     *
     * @param pdf путь к созданному PDF/A
     */
    void synchronize(Path pdf);
}
