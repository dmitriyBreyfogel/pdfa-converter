package ru.pdfa.converter.application;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import ru.pdfa.converter.application.validation.FileValidator;
import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Проверяет входной документ и готовит PDF для просмотра его исходных страниц.
 * Исходный PDF возвращается без конвертации, офисный документ экспортируется отдельно.
 */
@Service
public class CreateDocumentPreviewUseCase {
    private static final Logger log = LoggerFactory.getLogger(CreateDocumentPreviewUseCase.class);

    private final DocumentPreviewConverter previewConverter;

    /**
     * @param previewConverter преобразование офисных документов в обычный PDF
     */
    public CreateDocumentPreviewUseCase(DocumentPreviewConverter previewConverter) {
        this.previewConverter = previewConverter;
    }

    /**
     * Проверяет файл и возвращает PDF для предпросмотра без проверки на PDF/A.
     * Временные файлы экспорта удаляются после чтения результата.
     *
     * @param source путь к загруженному файлу
     * @return содержимое исходного PDF или PDF, созданного из офисного документа
     * @throws AppException если файл не прошёл проверку или предпросмотр не создан
     */
    public byte[] execute(Path source) {
        FileValidator.validate(source);
        String fileName = source.getFileName().toString();
        InputFormat format = InputFormat.fromExtension(fileName.substring(fileName.lastIndexOf('.') + 1))
                .orElseThrow(); // FileValidator уже подтвердил расширение.

        if (format == InputFormat.PDF) {
            return readOriginalPdf(source);
        }

        Path outputDirectory = null;
        try {
            outputDirectory = Files.createTempDirectory("pdfa-preview-output-");
            Path preview = previewConverter.convert(source, format, outputDirectory);
            return Files.readAllBytes(preview);
        } catch (IOException e) {
            throw AppException.build(ErrorCode.FILE_PROCESSING_ERROR,
                    "Не удалось подготовить предпросмотр документа", e);
        } finally {
            if (outputDirectory != null) {
                try {
                    FileSystemUtils.deleteRecursively(outputDirectory);
                } catch (IOException e) {
                    log.warn("Не удалось удалить временный каталог предпросмотра {}", outputDirectory, e);
                }
            }
        }
    }

    /**
     * Проверяет, что исходный PDF открывается и содержит страницы.
     *
     * @param source путь к исходному PDF
     * @return неизменённые байты исходного файла
     */
    private static byte[] readOriginalPdf(Path source) {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() == 0) {
                throw AppException.build(ErrorCode.INVALID_FILE, "У PDF нет страниц для предпросмотра");
            }
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw AppException.build(ErrorCode.INVALID_FILE, "Не удалось открыть PDF для предпросмотра", e);
        }
    }
}
