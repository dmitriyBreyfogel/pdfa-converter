package ru.pdfa.converter.application.conversion;

import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.domain.OutputFormat;

import java.nio.file.Path;

/**
 * Контракт стратегий конвертирования
 */
public interface ConversionStrategy {

    /**
     * Поддерживает ли текущая стратегия конвертации данный формат
     * @param format формат файла, который необходимо конвертировать
     * @return {@code true} если текущий формат поддерживается, {@code false} иначе
     */
    boolean supports(InputFormat format);

    /**
     * Конвертировать файл данной стратегией в выбранный выходной формат
     * @param source путь к файлу, который необходимо конвертировать
     * @param target формат выходного файла конвертации
     * @param outputDirectory путь к директории, в которую отправится сконвертированный файл
     * @return путь к сконвертированному файлу
     */
    Path convert(Path source, OutputFormat target, Path outputDirectory);
}
