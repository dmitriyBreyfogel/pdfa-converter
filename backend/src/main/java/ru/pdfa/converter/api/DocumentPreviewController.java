package ru.pdfa.converter.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.pdfa.converter.application.CreateDocumentPreviewUseCase;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Принимает загруженный документ по отдельному запросу предпросмотра.
 * Возвращает PDF с исходными страницами без запуска основной конвертации в PDF/A.
 */
@RestController
@RequestMapping("/api")
public class DocumentPreviewController {
    private static final Logger log = LoggerFactory.getLogger(DocumentPreviewController.class);

    private final CreateDocumentPreviewUseCase createPreview;

    /**
     * @param createPreview сценарий подготовки исходных страниц для просмотра
     */
    public DocumentPreviewController(CreateDocumentPreviewUseCase createPreview) {
        this.createPreview = createPreview;
    }

    /**
     * Сохраняет загрузку во временный файл и возвращает PDF для показа в браузере.
     *
     * @param file загруженный документ поддерживаемого формата
     * @return PDF исходных страниц без кеширования ответа
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> preview(@RequestParam("file") MultipartFile file) {
        String fileName = UploadedFileName.from(file);
        int extensionIndex = fileName.lastIndexOf('.');
        String suffix = extensionIndex > 0 ? fileName.substring(extensionIndex) : "";

        Path source = null;
        try {
            source = Files.createTempFile("pdfa-preview-input-", suffix);
            file.transferTo(source);
            byte[] pdf = createPreview.execute(source);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentLength(pdf.length)
                    .body(pdf);
        } catch (IOException e) {
            throw AppException.build(ErrorCode.FILE_PROCESSING_ERROR,
                    "Не удалось обработать загруженный файл", e);
        } finally {
            if (source != null) {
                try {
                    Files.deleteIfExists(source);
                } catch (IOException e) {
                    log.warn("Не удалось удалить временный входной файл предпросмотра {}", source, e);
                }
            }
        }
    }
}
