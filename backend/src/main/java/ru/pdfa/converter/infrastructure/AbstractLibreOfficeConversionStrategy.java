package ru.pdfa.converter.infrastructure;

import ru.pdfa.converter.application.ConversionStrategy;
import ru.pdfa.converter.domain.OutputFormat;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Общий запуск LibreOffice для стратегий конвертации в PDF/A.
 * Входной файл и каталог результата должны быть подготовлены вызывающим кодом.
 */
public abstract class AbstractLibreOfficeConversionStrategy implements ConversionStrategy {

    /**
     * Запускает LibreOffice и возвращает путь к созданному PDF.
     *
     * @param source путь к проверенному входному файлу
     * @param target требуемый формат PDF/A
     * @param outputDirectory каталог для результата и временных файлов LibreOffice
     * @return путь к созданному PDF
     * @throws AppException если запуск, конвертация или создание PDF завершились ошибкой
     */
    @Override
    public final Path convert(
            Path source,
            OutputFormat target,
            Path outputDirectory
    ) {
        Path result = expectedPdfPath(source, outputDirectory);

        try {
            Process process = startLibreOffice(source, outputDirectory, exportFilter(target));
            awaitConversion(process);
            requireCreatedPdf(result);
            return result;
        } catch (IOException e) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Не удалось выполнить конвертацию",
                    e
            );
        }
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

    /**
     * Вычисляет имя выходного PDF по имени проверенного входного файла.
     */
    private static Path expectedPdfPath(Path source, Path outputDirectory) {
        String sourceName = source.getFileName().toString();
        int extensionIndex = sourceName.lastIndexOf('.');
        String resultName = sourceName.substring(0, extensionIndex) + ".pdf";
        return outputDirectory.resolve(resultName);
    }

    /**
     * Создаёт отдельный профиль и лог, затем запускает процесс LibreOffice.
     */
    private static Process startLibreOffice(
            Path source,
            Path outputDirectory,
            String exportFilter
    ) throws IOException {
        Path profile = Files.createTempDirectory(outputDirectory, "libreoffice-profile-");
        Path log = Files.createTempFile(outputDirectory, "libreoffice-", ".log");

        return new ProcessBuilder(
                "libreoffice",
                "-env:UserInstallation=" + profile.toUri(),
                "--headless",
                "--convert-to", exportFilter,
                "--outdir", outputDirectory.toString(),
                source.toString()
        )
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    /**
     * Ожидает завершения процесса не более двух минут и проверяет код выхода.
     */
    private static void awaitConversion(Process process) {
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw AppException.build(
                        ErrorCode.FILE_PROCESSING_ERROR,
                        "Превышено время конвертации"
                );
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Конвертация прервана",
                    e
            );
        }

        if (process.exitValue() != 0) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "LibreOffice завершился с кодом " + process.exitValue()
            );
        }
    }

    /**
     * Проверяет, что LibreOffice создал непустой PDF.
     */
    private static void requireCreatedPdf(Path result) throws IOException {
        if (!Files.isRegularFile(result) || Files.size(result) == 0) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "LibreOffice не создал PDF"
            );
        }
    }
}
