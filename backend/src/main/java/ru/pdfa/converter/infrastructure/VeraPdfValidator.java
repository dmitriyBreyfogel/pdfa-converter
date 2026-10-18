package ru.pdfa.converter.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import ru.pdfa.converter.application.PdfaValidator;
import ru.pdfa.converter.domain.OutputFormat;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Проверяет созданный PDF/A с помощью командной строки veraPDF.
 */
@Component
public final class VeraPdfValidator implements PdfaValidator {
    private static final Logger log = LoggerFactory.getLogger(VeraPdfValidator.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Pattern CLOCK_ROLLBACK = Pattern.compile(
            "start:\\d+ must not be > finish:\\d+"
    );

    private final ProcessStarter processStarter;

    /**
     * Использует установленную команду veraPDF для проверки файлов.
     */
    public VeraPdfValidator() {
        this(VeraPdfValidator::startProcess);
    }

    VeraPdfValidator(ProcessStarter processStarter) {
        this.processStarter = Objects.requireNonNull(processStarter);
    }

    /**
     * Запускает проверку по профилю выбранного формата и читает отчёт veraPDF.
     *
     * @param pdf путь к созданному PDF
     * @param target требуемый формат PDF/A
     * @throws AppException если проверка не выполнена или PDF не соответствует профилю
     */
    @Override
    public void validate(Path pdf, OutputFormat target) {
        String profile = switch (target) {
            case PDF_A_1b -> "1b";
            case PDF_A_2b -> "2b";
            case PDF_A_3b -> "3b";
        };

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (validateOnce(pdf, target, profile)) {
                return;
            }
            if (attempt == MAX_ATTEMPTS) {
                throw AppException.build(
                        ErrorCode.FILE_PROCESSING_ERROR,
                        "veraPDF не смог проверить PDF из-за изменения системного времени"
                );
            }

            log.warn("veraPDF прервал проверку из-за изменения системного времени, попытка {}/{}",
                    attempt, MAX_ATTEMPTS);
            pauseBeforeRetry(attempt);
        }
    }

    /**
     * Выполняет одну проверку. Возвращает {@code false} только при сбое таймера veraPDF.
     */
    private boolean validateOnce(Path pdf, OutputFormat target, String profile) {
        try {
            Path directory = pdf.toAbsolutePath().getParent();
            Path report = Files.createTempFile(directory, "verapdf-", ".xml");
            Path errorLog = Files.createTempFile(directory, "verapdf-", ".log");

            Process process = processStarter.start(profile, pdf, report, errorLog);

            int exitCode = awaitValidation(process);
            String error = exitCode == 0 ? "" : Files.readString(errorLog);
            if (exitCode != 0 && isClockRollback(error)) {
                return false;
            }

            boolean compliant;
            try {
                compliant = isCompliant(report);
            }
            catch (IOException | ParserConfigurationException | SAXException e) {
                if (exitCode != 0) {
                    log.error("veraPDF завершился с кодом {}: {}", exitCode, error, e);
                    throw AppException.build(
                            ErrorCode.FILE_PROCESSING_ERROR,
                            "veraPDF завершился с кодом " + exitCode,
                            e
                    );
                }
                throw e;
            }
            if (!compliant) {
                throw AppException.build(
                        ErrorCode.PDF_A_VALIDATION_FAILED,
                        "Созданный PDF не соответствует формату " + target
                );
            }
            if (exitCode != 0) {
                log.error("veraPDF завершился с кодом {}: {}", exitCode, error);
                throw AppException.build(
                        ErrorCode.FILE_PROCESSING_ERROR,
                        "veraPDF завершился с кодом " + exitCode
                );
            }
            return true;
        }
        catch (IOException | ParserConfigurationException | SAXException e) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Не удалось проверить созданный PDF",
                    e
            );
        }
    }

    private static Process startProcess(String profile, Path pdf, Path report, Path errorLog)
            throws IOException {
        return new ProcessBuilder(
                "verapdf", "-f", profile, "--format", "xml", pdf.toString()
        )
                .redirectOutput(report.toFile())
                .redirectError(errorLog.toFile())
                .start();
    }

    private static boolean isClockRollback(String error) {
        return CLOCK_ROLLBACK.matcher(error).find()
                && error.contains("org.verapdf.component.AuditDurationImpl.fromValues");
    }

    /**
     * Разносит повторные проверки на одну и две секунды.
     */
    private static void pauseBeforeRetry(int attempt) {
        try {
            TimeUnit.SECONDS.sleep(attempt);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Проверка PDF/A прервана",
                    e
            );
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(String profile, Path pdf, Path report, Path errorLog) throws IOException;
    }

    /**
     * Ожидает завершения veraPDF не более двух минут.
     */
    private static int awaitValidation(Process process) {
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw AppException.build(
                        ErrorCode.FILE_PROCESSING_ERROR,
                        "Превышено время проверки PDF/A"
                );
            }
            return process.exitValue();
        }
        catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Проверка PDF/A прервана",
                    e
            );
        }
    }

    /**
     * Читает признак соответствия из единственного отчёта в XML veraPDF.
     */
    private static boolean isCompliant(Path report)
            throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        try (InputStream input = Files.newInputStream(report)) {
            NodeList reports = factory.newDocumentBuilder()
                    .parse(input)
                    .getElementsByTagName("validationReport");
            if (reports.getLength() != 1) {
                throw new IOException("Отчёт veraPDF не содержит единственного результата проверки");
            }

            String value = ((Element) reports.item(0)).getAttribute("isCompliant");
            if (!value.equals("true") && !value.equals("false")) {
                throw new IOException("Отчёт veraPDF содержит некорректный результат проверки");
            }
            return Boolean.parseBoolean(value);
        }
    }
}
