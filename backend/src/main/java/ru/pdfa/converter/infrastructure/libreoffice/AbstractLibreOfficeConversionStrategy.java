package ru.pdfa.converter.infrastructure.libreoffice;

import ru.pdfa.converter.application.ConversionStrategy;
import ru.pdfa.converter.domain.OutputFormat;

import java.nio.file.Path;

/**
 * Общая основа стратегий экспорта офисных документов в PDF/A.
 * Формирует параметры PDF/A и передаёт запуск LibreOffice общему экспортёру.
 */
public abstract class AbstractLibreOfficeConversionStrategy implements ConversionStrategy {

    /**
     * Запускает LibreOffice и возвращает путь к созданному PDF.
     *
     * @param source путь к проверенному входному файлу
     * @param target требуемый формат PDF/A
     * @param outputDirectory каталог для результата и временных файлов LibreOffice
     * @return путь к созданному PDF
     * @throws ru.pdfa.converter.error.AppException если запуск, конвертация или создание PDF завершились ошибкой
     */
    @Override
    public final Path convert(
            Path source,
            OutputFormat target,
            Path outputDirectory
    ) {
        return LibreOfficePdfExporter.export(source, outputDirectory, exportFilter(target));
    }

    /**
     * Возвращает фильтр экспорта PDF для конкретного типа документа.
     *
     * @param target требуемый формат PDF/A
     * @return параметр для команды {@code --convert-to}
     */
    protected abstract String exportFilter(OutputFormat target);

    /**
     * Формирует параметры PDF/A для фильтра LibreOffice.
     *
     * @param filterName имя фильтра экспорта
     * @param target требуемый формат PDF/A
     * @return параметр для команды {@code --convert-to}
     */
    protected final String pdfExportFilter(String filterName, OutputFormat target) {
        String version = switch (target) {
            case PDF_A_1b -> "1";
            case PDF_A_2b -> "2";
            case PDF_A_3b -> "3";
        };

        return "pdf:" + filterName
                + ":{\"SelectPdfVersion\":{\"type\":\"long\",\"value\":\""
                + version + "\"}}";
    }
}
