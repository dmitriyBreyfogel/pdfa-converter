package ru.pdfa.converter.infrastructure;

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
import java.util.concurrent.TimeUnit;

/**
 * Проверяет созданный PDF/A с помощью командной строки veraPDF.
 */
@Component
public final class VeraPdfValidator implements PdfaValidator {

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

        try {
            Path directory = pdf.toAbsolutePath().getParent();
            Path report = Files.createTempFile(directory, "verapdf-", ".xml");
            Path log = Files.createTempFile(directory, "verapdf-", ".log");

            Process process = new ProcessBuilder(
                    "verapdf", "-f", profile, "--format", "xml", pdf.toString()
            )
                    .redirectOutput(report.toFile())
                    .redirectError(log.toFile())
                    .start();

            int exitCode = awaitValidation(process);
            if (!isCompliant(report)) {
                throw AppException.build(
                        ErrorCode.PDF_A_VALIDATION_FAILED,
                        "Созданный PDF не соответствует формату " + target
                );
            }
            if (exitCode != 0) {
                throw AppException.build(
                        ErrorCode.FILE_PROCESSING_ERROR,
                        "veraPDF завершился с кодом " + exitCode
                );
            }
        }
        catch (IOException | ParserConfigurationException | SAXException e) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Не удалось проверить созданный PDF",
                    e
            );
        }
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
