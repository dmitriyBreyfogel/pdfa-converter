package ru.pdfa.converter.infrastructure.libreoffice;

import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.domain.OutputFormat;

/**
 * Повторный экспорт входного PDF в PDF/A через LibreOffice Draw.
 * Такой экспорт может заменить шрифты и не сохранить интерактивные элементы PDF.
 * Стратегия оставлена для возможного повторного использования, но не регистрируется как компонент.
 */
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
