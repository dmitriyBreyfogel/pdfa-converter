package ru.pdfa.converter.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import ru.pdfa.converter.domain.OutputFormat;

@RequestMapping("/api")
public interface PdfaConversionApi {

    /**
     * Конвертирование файла в выбранный формат PDF-A
     *
     * @param file файл для конвертации. Должен соответствовать форматам {@link ru.pdfa.converter.domain.InputFormat}
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
            @RequestParam("target") OutputFormat target
    );
}
