package ru.pdfa.converter.application;

import ru.pdfa.converter.domain.InputFormat;

import java.nio.file.Path;

/**
 * Контракт преобразования офисного документа в обычный PDF для предпросмотра.
 * PDF-файл передаётся пользователю без вызова этого контракта.
 */
public interface DocumentPreviewConverter {

    /**
     * Создаёт непустой PDF из проверенного офисного документа.
     * Поддерживаются форматы DOC, DOCX, XLS, XLSX и PPTX.
     *
     * @param source путь к проверенному входному документу
     * @param format формат входного документа, отличный от PDF
     * @param outputDirectory существующий каталог для результата и временных файлов
     * @return путь к созданному PDF внутри {@code outputDirectory}
     * @throws ru.pdfa.converter.error.AppException если экспорт не удался
     * @throws IllegalArgumentException если передан формат PDF
     */
    Path convert(Path source, InputFormat format, Path outputDirectory);
}
