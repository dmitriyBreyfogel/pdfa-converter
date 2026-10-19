package ru.pdfa.converter.infrastructure;

import org.springframework.stereotype.Component;
import ru.pdfa.converter.application.DocumentPreviewConverter;
import ru.pdfa.converter.domain.InputFormat;

import java.nio.file.Path;

/**
 * Экспортирует офисные документы в обычный PDF через LibreOffice.
 * Настройки PDF/A для предпросмотра не применяются.
 */
@Component
public final class LibreOfficeDocumentPreviewConverter implements DocumentPreviewConverter {
    /**
     * Выбирает PDF-фильтр по формату исходного документа и запускает экспорт.
     *
     * @param source путь к проверенному офисному документу
     * @param format формат входного документа
     * @param outputDirectory каталог для результата и временных файлов
     * @return путь к созданному PDF
     */
    @Override
    public Path convert(Path source, InputFormat format, Path outputDirectory) {
        String filter = switch (format) {
            case DOC, DOCX -> "writer_pdf_Export";
            case XLS, XLSX -> "calc_pdf_Export";
            case PPTX -> "impress_pdf_Export";
            case PDF -> throw new IllegalArgumentException("PDF не требует экспорта для предпросмотра");
        };
        return LibreOfficePdfExporter.export(source, outputDirectory, "pdf:" + filter);
    }
}
