package ru.pdfa.converter.infrastructure.metadata;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Проверяет нормализацию дат PDF Info, записанных Ghostscript.
 * Значения дат должны сохраниться после повторного сохранения PDF.
 */
class PdfBoxGhostscriptDateSynchronizerTest {
    @TempDir
    Path directory;

    @Test
    void preservesDatesAndRewritesGhostscriptTimezone() throws IOException {
        Path pdf = directory.resolve("ghostscript.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            PDDocumentInformation information = document.getDocumentInformation();
            information.getCOSObject().setString(COSName.CREATION_DATE, "D:20261022105334Z00'00'");
            information.getCOSObject().setString(COSName.MOD_DATE, "D:20261022105334Z00'00'");
            document.save(pdf.toFile());
        }

        new PdfBoxGhostscriptDateSynchronizer().synchronize(pdf);

        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PDDocumentInformation information = document.getDocumentInformation();
            assertEquals(Instant.parse("2026-10-22T10:53:34Z"),
                    information.getCreationDate().toInstant());
            assertEquals(Instant.parse("2026-10-22T10:53:34Z"),
                    information.getModificationDate().toInstant());
            assertFalse(information.getCOSObject().getString(COSName.CREATION_DATE).contains("Z00'00'"));
            assertFalse(information.getCOSObject().getString(COSName.MOD_DATE).contains("Z00'00'"));
        }
    }
}
