package ru.pdfa.converter.application.preview;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CreateDocumentPreviewUseCaseTest {
    @TempDir
    Path directory;

    @Test
    void returnsOriginalPdfWithoutConversion() throws IOException {
        Path source = directory.resolve("document.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(source.toFile());
        }
        DocumentPreviewConverter converter = mock(DocumentPreviewConverter.class);

        byte[] preview = new CreateDocumentPreviewUseCase(converter).execute(source);

        assertArrayEquals(Files.readAllBytes(source), preview);
        verifyNoInteractions(converter);
    }

    @Test
    void convertsOfficeDocumentAndDeletesTemporaryOutput() throws IOException {
        Path source = Files.writeString(directory.resolve("document.docx"), "document");
        DocumentPreviewConverter converter = mock(DocumentPreviewConverter.class);
        AtomicReference<Path> outputDirectory = new AtomicReference<>();
        given(converter.convert(eq(source), eq(InputFormat.DOCX), any(Path.class)))
                .willAnswer(invocation -> {
                    Path output = invocation.getArgument(2);
                    outputDirectory.set(output);
                    return Files.writeString(output.resolve("preview.pdf"), "%PDF-preview");
                });

        byte[] preview = new CreateDocumentPreviewUseCase(converter).execute(source);

        assertArrayEquals("%PDF-preview".getBytes(), preview);
        assertFalse(Files.exists(outputDirectory.get()));
    }

    @Test
    void rejectsBrokenPdf() throws IOException {
        Path source = Files.writeString(directory.resolve("broken.pdf"), "THIS_IS_NOT_A_PDF");

        AppException error = assertThrows(AppException.class,
                () -> new CreateDocumentPreviewUseCase(mock(DocumentPreviewConverter.class)).execute(source));

        assertEquals(ErrorCode.INVALID_FILE, error.getCode());
    }
}
