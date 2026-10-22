package ru.pdfa.converter.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.pdfa.converter.domain.InputFormat;
import ru.pdfa.converter.domain.OutputFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ConvertToPdfaUseCaseTest {
    @TempDir
    Path directory;

    @Test
    void synchronizesOfficePdfa1BeforeValidation() throws IOException {
        ConversionStrategy strategy = strategyFor(InputFormat.DOCX);
        PdfaCreationDateSynchronizer synchronizer = mock(PdfaCreationDateSynchronizer.class);
        GhostscriptDateSynchronizer ghostscriptSynchronizer = mock(GhostscriptDateSynchronizer.class);
        PdfaValidator validator = mock(PdfaValidator.class);
        Path source = Files.writeString(directory.resolve("document.docx"), "document");

        byte[] result = new ConvertToPdfaUseCase(List.of(strategy), synchronizer, ghostscriptSynchronizer, validator)
                .execute(source, OutputFormat.PDF_A_1b);

        assertArrayEquals("%PDF-test".getBytes(), result);
        var order = inOrder(synchronizer, validator);
        order.verify(synchronizer).synchronize(any(Path.class));
        order.verify(validator).validate(any(Path.class), eq(OutputFormat.PDF_A_1b));
        verifyNoInteractions(ghostscriptSynchronizer);
    }

    @Test
    void synchronizesGhostscriptPdfa1BeforeValidation() throws IOException {
        ConversionStrategy strategy = strategyFor(InputFormat.PDF);
        PdfaCreationDateSynchronizer synchronizer = mock(PdfaCreationDateSynchronizer.class);
        GhostscriptDateSynchronizer ghostscriptSynchronizer = mock(GhostscriptDateSynchronizer.class);
        PdfaValidator validator = mock(PdfaValidator.class);
        Path source = Files.writeString(directory.resolve("document.pdf"), "%PDF-source");

        new ConvertToPdfaUseCase(List.of(strategy), synchronizer, ghostscriptSynchronizer, validator)
                .execute(source, OutputFormat.PDF_A_1b);

        verifyNoInteractions(synchronizer);
        var order = inOrder(ghostscriptSynchronizer, validator);
        order.verify(ghostscriptSynchronizer).synchronize(any(Path.class));
        order.verify(validator).validate(any(Path.class), eq(OutputFormat.PDF_A_1b));
    }

    @Test
    void doesNotChangeOfficePdfa2() throws IOException {
        ConversionStrategy strategy = strategyFor(InputFormat.XLSX);
        PdfaCreationDateSynchronizer synchronizer = mock(PdfaCreationDateSynchronizer.class);
        GhostscriptDateSynchronizer ghostscriptSynchronizer = mock(GhostscriptDateSynchronizer.class);
        PdfaValidator validator = mock(PdfaValidator.class);
        Path source = Files.writeString(directory.resolve("document.xlsx"), "spreadsheet");

        new ConvertToPdfaUseCase(List.of(strategy), synchronizer, ghostscriptSynchronizer, validator)
                .execute(source, OutputFormat.PDF_A_2b);

        verifyNoInteractions(synchronizer, ghostscriptSynchronizer);
    }

    @Test
    void doesNotSynchronizeGhostscriptPdfa2() throws IOException {
        ConversionStrategy strategy = strategyFor(InputFormat.PDF);
        PdfaCreationDateSynchronizer synchronizer = mock(PdfaCreationDateSynchronizer.class);
        GhostscriptDateSynchronizer ghostscriptSynchronizer = mock(GhostscriptDateSynchronizer.class);
        PdfaValidator validator = mock(PdfaValidator.class);
        Path source = Files.writeString(directory.resolve("document.pdf"), "%PDF-source");

        new ConvertToPdfaUseCase(List.of(strategy), synchronizer, ghostscriptSynchronizer, validator)
                .execute(source, OutputFormat.PDF_A_2b);

        verifyNoInteractions(synchronizer, ghostscriptSynchronizer);
    }

    private static ConversionStrategy strategyFor(InputFormat inputFormat) {
        ConversionStrategy strategy = mock(ConversionStrategy.class);
        given(strategy.supports(inputFormat)).willReturn(true);
        given(strategy.convert(any(Path.class), any(OutputFormat.class), any(Path.class)))
                .willAnswer(invocation -> Files.writeString(
                        invocation.getArgument(2, Path.class).resolve("result.pdf"),
                        "%PDF-test"
                ));
        return strategy;
    }
}
