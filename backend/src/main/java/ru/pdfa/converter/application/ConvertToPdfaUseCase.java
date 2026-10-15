package ru.pdfa.converter.application;

import org.springframework.stereotype.Service;
import ru.pdfa.converter.application.validation.FileValidator;
import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.domain.OutputFormat;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.nio.file.Path;
import java.util.List;

@Service
public class ConvertToPdfaUseCase {
    private final List<ConversionStrategy> strategies;

    public ConvertToPdfaUseCase(List<ConversionStrategy> strategies) {
        this.strategies = strategies;
    }

    public Path execute(Path source, OutputFormat target, Path outputDirectory) {
        FileValidator.validate(source);

        InputFormat format = formatOfValidatedFile(source);
        ConversionStrategy strategy = findStrategy(format);

        return strategy.convert(source, target, outputDirectory);
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
