package ru.pdfa.converter.infrastructure;

import org.springframework.stereotype.Component;
import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.domain.OutputFormat;

/**
 * Конвертация презентаций PPTX через фильтр PDF-экспорта LibreOffice Impress.
 */
@Component
public final class LibreOfficePresentationConversionStrategy extends AbstractLibreOfficeConversionStrategy {

    /**
     * Поддерживает презентации PPTX.
     *
     * @param format формат входного файла
     * @return {@code true} для PPTX
     */
    @Override
    public boolean supports(InputFormat format) {
        return format == InputFormat.PPTX;
    }

    /**
     * Выбирает фильтр Impress для экспорта презентации в PDF/A.
     *
     * @param target требуемый формат PDF/A
     * @return параметр для команды {@code --convert-to}
     */
    @Override
    protected String exportFilter(OutputFormat target) {
        return pdfExportFilter("impress_pdf_Export", target);
    }
}
