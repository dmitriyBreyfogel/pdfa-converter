package ru.pdfa.converter.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import ru.pdfa.converter.application.validation.FileValidator;
import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.domain.OutputFormat;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Проверяет входной файл и конвертирует его в выбранный формат PDF/A.
 */
@Service
public class ConvertToPdfaUseCase {
    private static final Logger log = LoggerFactory.getLogger(ConvertToPdfaUseCase.class);

    private final List<ConversionStrategy> strategies;
    private final PdfaCreationDateSynchronizer creationDateSynchronizer;
    private final GhostscriptDateSynchronizer ghostscriptDateSynchronizer;
    private final PdfaValidator pdfaValidator;

    /**
     * @param strategies зарегистрированные стратегии конвертации
     * @param creationDateSynchronizer согласование даты создания офисного PDF/A-1b
     * @param ghostscriptDateSynchronizer нормализация дат Ghostscript PDF/A-1b
     * @param pdfaValidator проверка результата на соответствие PDF/A
     */
    public ConvertToPdfaUseCase(
            List<ConversionStrategy> strategies,
            PdfaCreationDateSynchronizer creationDateSynchronizer,
            GhostscriptDateSynchronizer ghostscriptDateSynchronizer,
            PdfaValidator pdfaValidator
    ) {
        this.strategies = strategies;
        this.creationDateSynchronizer = creationDateSynchronizer;
        this.ghostscriptDateSynchronizer = ghostscriptDateSynchronizer;
        this.pdfaValidator = pdfaValidator;
    }

    /**
     * Конвертирует файл, проверяет результат и удаляет временные выходные файлы.
     *
     * @param source путь к входному файлу
     * @param target требуемый формат PDF/A
     * @return содержимое сконвертированного PDF
     */
    public byte[] execute(Path source, OutputFormat target) {
        FileValidator.validate(source);

        InputFormat format = formatOfValidatedFile(source);
        ConversionStrategy strategy = findStrategy(format);

        Path outputDirectory = null;
        try {
            outputDirectory = Files.createTempDirectory("pdfa-output-");
            Path result = strategy.convert(source, target, outputDirectory);
            if (format != InputFormat.PDF && target == OutputFormat.PDF_A_1b) {
                creationDateSynchronizer.synchronize(result);
            }
            else if (format == InputFormat.PDF && target == OutputFormat.PDF_A_1b) {
                ghostscriptDateSynchronizer.synchronize(result);
            }
            pdfaValidator.validate(result, target);
            return Files.readAllBytes(result);
        }
        catch (IOException e) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Не удалось обработать временные файлы конвертации",
                    e
            );
        }
        finally {
            if (outputDirectory != null) {
                try {
                    FileSystemUtils.deleteRecursively(outputDirectory);
                }
                catch (IOException e) {
                    log.warn("Не удалось удалить временный каталог {}", outputDirectory, e);
                }
            }
        }
    }

    private InputFormat formatOfValidatedFile(Path source) {
        String fileName = source.getFileName().toString();
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);

        // После FileValidator.validate() расширение существует и поддерживается.
        return InputFormat.fromExtension(extension).orElseThrow();
    }

    private ConversionStrategy findStrategy(InputFormat format) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(format))
                .findFirst()
                .orElseThrow(() -> AppException.build(
                        ErrorCode.FILE_PROCESSING_ERROR,
                        "Не найдена стратегия конвертации для " + format
                ));
    }
}
