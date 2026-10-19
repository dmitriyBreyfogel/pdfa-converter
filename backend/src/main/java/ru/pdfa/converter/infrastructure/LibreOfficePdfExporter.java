package ru.pdfa.converter.infrastructure;

import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Запускает экспорт документа в PDF через LibreOffice с отдельным профилем.
 * Используется основной конвертацией в PDF/A и предпросмотром исходника.
 */
final class LibreOfficePdfExporter {
    private LibreOfficePdfExporter() {
    }

    /**
     * Запускает LibreOffice с указанным фильтром и проверяет созданный PDF.
     *
     * @param source проверенный входной документ
     * @param outputDirectory существующий отдельный каталог результата
     * @param exportFilter полный параметр команды {@code --convert-to}
     * @return путь к созданному PDF
     * @throws AppException если LibreOffice не создал непустой PDF
     */
    static Path export(Path source, Path outputDirectory, String exportFilter) {
        String sourceName = source.getFileName().toString();
        Path result = outputDirectory.resolve(sourceName.substring(0, sourceName.lastIndexOf('.')) + ".pdf");
        if (Files.exists(result, LinkOption.NOFOLLOW_LINKS)) {
            throw AppException.build(ErrorCode.FILE_PROCESSING_ERROR, "Выходной PDF уже существует");
        }

        try {
            Path profile = Files.createTempDirectory(outputDirectory, "libreoffice-profile-");
            Path log = Files.createTempFile(outputDirectory, "libreoffice-", ".log");
            Process process = new ProcessBuilder(
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

            awaitConversion(process);
            if (!Files.isRegularFile(result) || Files.size(result) == 0) {
                throw AppException.build(ErrorCode.FILE_PROCESSING_ERROR, "LibreOffice не создал PDF");
            }
            return result;
        } catch (IOException e) {
            throw AppException.build(ErrorCode.FILE_PROCESSING_ERROR, "Не удалось выполнить конвертацию", e);
        }
    }

    /**
     * Ожидает завершения LibreOffice не более двух минут.
     *
     * @param process запущенный процесс LibreOffice
     * @throws AppException если процесс завершился с ошибкой или превысил время ожидания
     */
    private static void awaitConversion(Process process) {
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw AppException.build(ErrorCode.FILE_PROCESSING_ERROR, "Превышено время конвертации");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw AppException.build(ErrorCode.FILE_PROCESSING_ERROR, "Конвертация прервана", e);
        }

        if (process.exitValue() != 0) {
            throw AppException.build(ErrorCode.FILE_PROCESSING_ERROR,
                    "LibreOffice завершился с кодом " + process.exitValue());
        }
    }
}
