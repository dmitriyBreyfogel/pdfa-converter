package ru.pdfa.converter.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.pdfa.converter.application.conversion.ConvertToPdfaUseCase;
import ru.pdfa.converter.domain.OutputFormat;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Принимает файл для конвертации и возвращает проверенный PDF/A.
 */
@RestController
public class PdfaConversionController implements PdfaConversionApi {
    private static final Logger log = LoggerFactory.getLogger(PdfaConversionController.class);

    private final ConvertToPdfaUseCase convertToPdfa;

    /**
     * @param convertToPdfa сценарий конвертации файла в PDF/A
     */
    public PdfaConversionController(ConvertToPdfaUseCase convertToPdfa) {
        this.convertToPdfa = convertToPdfa;
    }

    /**
     * Сохраняет загрузку во временный файл и возвращает результат для скачивания.
     *
     * @param file загруженный файл
     * @param target требуемый формат PDF/A
     * @return PDF/A с заголовком для скачивания
     */
    @Override
    public ResponseEntity<byte[]> convert(MultipartFile file, OutputFormat target) {
        String fileName = UploadedFileName.from(file);
        int extensionIndex = fileName.lastIndexOf('.');
        String suffix = extensionIndex > 0 ? fileName.substring(extensionIndex) : "";

        Path source = null;
        try {
            source = Files.createTempFile("pdfa-input-", suffix);
            file.transferTo(source);

            byte[] pdf = convertToPdfa.execute(source, target);
            String downloadName = fileName.substring(0, extensionIndex) + ".pdf";
            String disposition = ContentDisposition.attachment()
                    .filename(downloadName, StandardCharsets.UTF_8)
                    .build()
                    .toString();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentLength(pdf.length)
                    .body(pdf);
        }
        catch (IOException e) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Не удалось обработать загруженный файл",
                    e
            );
        }
        finally {
            if (source != null) {
                try {
                    Files.deleteIfExists(source);
                }
                catch (IOException e) {
                    log.warn("Не удалось удалить временный входной файл {}", source, e);
                }
            }
        }
    }

}
