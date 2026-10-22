package ru.pdfa.converter.infrastructure.libreoffice;

import org.springframework.stereotype.Component;
import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.domain.OutputFormat;

/**
 * Конвертация таблиц Excel через фильтр PDF-экспорта LibreOffice Calc.
 */
@Component
public final class LibreOfficeExcelConversionStrategy extends AbstractLibreOfficeConversionStrategy {

    /**
     * Поддерживает таблицы XLS и XLSX.
     *
     * @param format формат входного файла
     * @return {@code true} для XLS и XLSX
     */
    @Override
    public boolean supports(InputFormat format) {
        return format == InputFormat.XLS || format == InputFormat.XLSX;
    }

    /**
     * Выбирает фильтр Calc для экспорта таблицы в PDF/A.
     *
     * @param target требуемый формат PDF/A
     * @return параметр для команды {@code --convert-to}
     */
    @Override
    protected String exportFilter(OutputFormat target) {
        return pdfExportFilter("calc_pdf_Export", target);
    }
}
