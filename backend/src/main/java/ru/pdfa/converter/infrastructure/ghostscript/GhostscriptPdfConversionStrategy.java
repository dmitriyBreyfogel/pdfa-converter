package ru.pdfa.converter.infrastructure.ghostscript;

import org.springframework.stereotype.Component;
import ru.pdfa.converter.application.conversion.ConversionStrategy;
import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.domain.OutputFormat;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Конвертирует входной PDF в PDF/A через Ghostscript без импорта в LibreOffice Draw.
 * При экспорте в PDF/A-1b страницы с прозрачностью могут быть преобразованы в изображения.
 */
@Component
public final class GhostscriptPdfConversionStrategy implements ConversionStrategy {
    private static final String OUTPUT_INTENT = "/app/pdfa-output-intent.ps";
    private static final String ICC_PROFILE = "/usr/share/color/icc/ghostscript/srgb.icc";

    /**
     * @param format формат входного файла
     * @return {@code true} для PDF
     */
    @Override
    public boolean supports(InputFormat format) {
        return format == InputFormat.PDF;
    }

    /**
     * Запускает Ghostscript с нужной версией PDF/A и возвращает созданный файл.
     *
     * @param source путь к входному PDF
     * @param target требуемый формат PDF/A
     * @param outputDirectory каталог для результата и лога
     * @return путь к созданному PDF/A
     * @throws AppException если Ghostscript не завершил конвертацию успешно
     */
    @Override
    public Path convert(Path source, OutputFormat target, Path outputDirectory) {
        String sourceName = source.getFileName().toString();
        Path result = outputDirectory.resolve(sourceName);

        try {
            Path log = Files.createTempFile(outputDirectory, "ghostscript-", ".log");
            Process process = new ProcessBuilder(
                    "gs",
                    "-dBATCH",
                    "-dNOPAUSE",
                    "-dSAFER",
                    "-dPDFA=" + pdfaVersion(target),
                    "-dPDFACompatibilityPolicy=1",
                    "-sDEVICE=pdfwrite",
                    "-sColorConversionStrategy=RGB",
                    "--permit-file-read=" + ICC_PROFILE,
                    "-sOutputFile=" + result,
                    OUTPUT_INTENT,
                    source.toString()
            )
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();

            awaitConversion(process);
            if (!Files.isRegularFile(result) || Files.size(result) == 0) {
                throw AppException.build(
                        ErrorCode.FILE_PROCESSING_ERROR,
                        "Ghostscript не создал PDF"
                );
            }
            return result;
        }
        catch (IOException e) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Не удалось выполнить конвертацию через Ghostscript",
                    e
            );
        }
    }

    private static int pdfaVersion(OutputFormat target) {
        return switch (target) {
            case PDF_A_1b -> 1;
            case PDF_A_2b -> 2;
            case PDF_A_3b -> 3;
        };
    }

    private static void awaitConversion(Process process) {
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw AppException.build(
                        ErrorCode.FILE_PROCESSING_ERROR,
                        "Превышено время конвертации через Ghostscript"
                );
            }
        }
        catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Конвертация через Ghostscript прервана",
                    e
            );
        }

        if (process.exitValue() != 0) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Ghostscript завершился с кодом " + process.exitValue()
            );
        }
    }
}
