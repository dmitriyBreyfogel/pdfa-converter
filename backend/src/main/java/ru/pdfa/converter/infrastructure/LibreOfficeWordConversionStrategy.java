package ru.pdfa.converter.infrastructure;

import org.springframework.stereotype.Component;
import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.domain.OutputFormat;

/**
 * Конвертация документов Word через фильтр PDF-экспорта LibreOffice Writer.
 */
@Component
public final class LibreOfficeWordConversionStrategy extends AbstractLibreOfficeConversionStrategy {

    /**
     * Поддерживает документы DOC и DOCX.
     *
     * @param format формат входного файла
     * @return {@code true} для DOC и DOCX
     */
    @Override
    public boolean supports(InputFormat format) {
        return format == InputFormat.DOC || format == InputFormat.DOCX;
    }

    /**
     * Выбирает фильтр Writer для экспорта документа в PDF/A.
     *
     * @param target требуемый формат PDF/A
     * @return параметр для команды {@code --convert-to}
     */
    @Override
    protected String exportFilter(OutputFormat target) {
        return pdfExportFilter("writer_pdf_Export", target);
    }
}
