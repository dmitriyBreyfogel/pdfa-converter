package ru.pdfa.converter.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.pdfa.converter.domain.OutputFormats;

@RestController
@RequestMapping("/api")
public interface PdfaConversionApi {

    /**
     * Конвертирование файла в выбранный формат PDF-A
     *
     * @param file файл для конвертации
     * <p>
     *  Может быть:
     *         {@code DOCX},
     *         {@code DOC},
     *         {@code PDF},
     *         {@code EXCEL},
     *         {@code PPTX}
     * </p>
     * @param target целевой формат PDF-A конвертации
     * @return сконвертированный файл целевого формата
     */
    @PostMapping(
            value = "/convert",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    ResponseEntity<byte[]> convert(
            @RequestParam("file") MultipartFile file,
            @RequestParam("target") OutputFormats target
    );
}
