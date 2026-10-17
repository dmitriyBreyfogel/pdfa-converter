package ru.pdfa.converter.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.pdfa.converter.domain.OutputFormat;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class VeraPdfValidatorTest {
    private static final String CLOCK_ERROR = """
            Exception in thread "main" java.lang.IllegalArgumentException: start:1792261386628 must not be > finish:1792261341955
            \tat org.verapdf.component.AuditDurationImpl.fromValues(AuditDurationImpl.java:113)
            """;

    @TempDir
    Path directory;

    @Test
    void retriesClockRollbackAndAcceptsOnlySuccessfulValidation() throws IOException {
        Path pdf = Files.writeString(directory.resolve("result.pdf"), "%PDF-test");
        AtomicInteger attempts = new AtomicInteger();
        List<Path> reports = new ArrayList<>();
        VeraPdfValidator validator = new VeraPdfValidator((profile, source, report, errorLog) -> {
            assertEquals("2b", profile);
            assertEquals(pdf, source);
            reports.add(report);
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                Files.writeString(errorLog, CLOCK_ERROR);
                return completedProcess(1);
            }
            Files.writeString(report, compliantReport(true));
            return completedProcess(0);
        });

        validator.validate(pdf, OutputFormat.PDF_A_2b);

        assertEquals(3, attempts.get());
        assertNotEquals(reports.get(0), reports.get(1));
        assertNotEquals(reports.get(1), reports.get(2));
    }

    @Test
    void stopsAfterThreeClockFailures() throws IOException {
        Path pdf = Files.writeString(directory.resolve("result.pdf"), "%PDF-test");
        AtomicInteger attempts = new AtomicInteger();
        VeraPdfValidator validator = new VeraPdfValidator((profile, source, report, errorLog) -> {
            attempts.incrementAndGet();
            Files.writeString(errorLog, CLOCK_ERROR);
            return completedProcess(1);
        });

        AppException error = assertThrows(AppException.class,
                () -> validator.validate(pdf, OutputFormat.PDF_A_2b));

        assertEquals(ErrorCode.FILE_PROCESSING_ERROR, error.getCode());
        assertEquals(3, attempts.get());
        assertTrue(((String) error.getDetails().get("log")).contains("AuditDurationImpl"));
    }

    @Test
    void doesNotRetryNonCompliantPdf() throws IOException {
        Path pdf = Files.writeString(directory.resolve("result.pdf"), "%PDF-test");
        AtomicInteger attempts = new AtomicInteger();
        VeraPdfValidator validator = new VeraPdfValidator((profile, source, report, errorLog) -> {
            attempts.incrementAndGet();
            Files.writeString(report, compliantReport(false));
            return completedProcess(1);
        });

        AppException error = assertThrows(AppException.class,
                () -> validator.validate(pdf, OutputFormat.PDF_A_2b));

        assertEquals(ErrorCode.PDF_A_VALIDATION_FAILED, error.getCode());
        assertEquals(1, attempts.get());
        assertTrue(((String) error.getDetails().get("report")).contains("isCompliant=\"false\""));
    }

    @Test
    void reportsOtherProcessFailuresWithIncompleteXml() throws IOException {
        Path pdf = Files.writeString(directory.resolve("result.pdf"), "%PDF-test");
        AtomicInteger attempts = new AtomicInteger();
        VeraPdfValidator validator = new VeraPdfValidator((profile, source, report, errorLog) -> {
            attempts.incrementAndGet();
            Files.writeString(report, "<report>");
            Files.writeString(errorLog, "veraPDF failed for another reason");
            return completedProcess(1);
        });

        AppException error = assertThrows(AppException.class,
                () -> validator.validate(pdf, OutputFormat.PDF_A_2b));

        assertEquals(ErrorCode.FILE_PROCESSING_ERROR, error.getCode());
        assertEquals("veraPDF завершился с кодом 1", error.getMessage());
        assertEquals(1, attempts.get());
        assertEquals("veraPDF failed for another reason", error.getDetails().get("log"));
    }

    private static String compliantReport(boolean compliant) {
        return "<report><validationReport isCompliant=\"" + compliant + "\"/></report>";
    }

    private static Process completedProcess(int exitCode) {
        Process process = mock(Process.class);
        try {
            given(process.waitFor(120, TimeUnit.SECONDS)).willReturn(true);
        }
        catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        given(process.exitValue()).willReturn(exitCode);
        return process;
    }
}
