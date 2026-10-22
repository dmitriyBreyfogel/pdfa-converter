package ru.pdfa.converter.infrastructure.metadata;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.springframework.stereotype.Component;
import ru.pdfa.converter.application.conversion.GhostscriptDateSynchronizer;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;

/**
 * Нормализует запись дат Ghostscript в PDF Info без изменения дат XMP.
 * Сохраняет готовый PDF/A-1b во временный файл перед заменой результата.
 */
@Component
public final class PdfBoxGhostscriptDateSynchronizer implements GhostscriptDateSynchronizer {

    /**
     * Перезаписывает обе даты PDF Info через PDFBox, сохраняя моменты времени.
     *
     * @param pdf созданный Ghostscript PDF/A-1b
     * @throws AppException если даты отсутствуют или PDF не удалось сохранить
     */
    @Override
    public void synchronize(Path pdf) {
        Path updated = null;
        try {
            updated = Files.createTempFile(pdf.getParent(), "pdfa-ghostscript-dates-", ".pdf");
            try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
                PDDocumentInformation information = document.getDocumentInformation();
                Calendar creationDate = information.getCreationDate();
                Calendar modificationDate = information.getModificationDate();
                if (creationDate == null || modificationDate == null) {
                    throw AppException.build(
                            ErrorCode.FILE_PROCESSING_ERROR,
                            "В PDF/A отсутствует дата создания или изменения"
                    );
                }

                information.setCreationDate(creationDate);
                information.setModificationDate(modificationDate);
                document.save(updated.toFile(), CompressParameters.NO_COMPRESSION);
            }
            Files.move(updated, pdf, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Не удалось согласовать даты Ghostscript PDF/A",
                    e
            );
        }
        finally {
            if (updated != null) {
                try {
                    Files.deleteIfExists(updated);
                }
                catch (IOException ignored) {
                    // Временный каталог целиком удаляется сценарием конвертации.
                }
            }
        }
    }
}
