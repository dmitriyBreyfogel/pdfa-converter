package ru.pdfa.converter.infrastructure;

import org.springframework.stereotype.Component;
import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.domain.OutputFormat;

/**
 * Повторный экспорт входного PDF в PDF/A через LibreOffice Draw.
 * Такой экспорт может не сохранить аннотации и другие интерактивные элементы PDF.
 */
@Component
public final class LibreOfficePdfConversionStrategy extends AbstractLibreOfficeConversionStrategy {

    /**
     * Поддерживает входные PDF-файлы.
     *
     * @param format формат входного файла
     * @return {@code true} для PDF
     */
    @Override
    public boolean supports(InputFormat format) {
        return format == InputFormat.PDF;
    }

    /**
     * Выбирает фильтр Draw для экспорта PDF в PDF/A.
     *
     * @param target требуемый формат PDF/A
     * @return параметр для команды {@code --convert-to}
     */
    @Override
    protected String exportFilter(OutputFormat target) {
        return pdfExportFilter("draw_pdf_Export", target);
    }
}
