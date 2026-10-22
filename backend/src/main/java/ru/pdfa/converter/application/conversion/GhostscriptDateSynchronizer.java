package ru.pdfa.converter.application.conversion;

import java.nio.file.Path;

/**
 * Согласует запись дат в PDF/A-1b, созданном Ghostscript.
 */
public interface GhostscriptDateSynchronizer {

    /**
     * Перезаписывает даты PDF Info в совместимом формате, сохраняя их значения.
     *
     * @param pdf путь к созданному Ghostscript PDF/A-1b
     */
    void synchronize(Path pdf);
}
