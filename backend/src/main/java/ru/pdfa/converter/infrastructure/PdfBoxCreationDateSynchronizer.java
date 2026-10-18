package ru.pdfa.converter.infrastructure;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import ru.pdfa.converter.application.PdfaCreationDateSynchronizer;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;

/**
 * Согласует XMP с датой создания PDF, записанной LibreOffice в PDF Info.
 */
@Component
public final class PdfBoxCreationDateSynchronizer implements PdfaCreationDateSynchronizer {
    private static final String XMP_NAMESPACE = "http://ns.adobe.com/xap/1.0/";

    /**
     * Меняет только дату создания в XMP и заменяет исходный PDF после успешного сохранения.
     *
     * @param pdf созданный LibreOffice PDF/A
     */
    @Override
    public void synchronize(Path pdf) {
        Path updated = null;
        try {
            updated = Files.createTempFile(pdf.getParent(), "pdfa-metadata-", ".pdf");
            try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
                Calendar creationDate = document.getDocumentInformation().getCreationDate();
                PDMetadata metadata = document.getDocumentCatalog().getMetadata();
                if (creationDate == null || metadata == null) {
                    throw AppException.build(
                            ErrorCode.FILE_PROCESSING_ERROR,
                            "В PDF отсутствует дата создания или XMP"
                    );
                }

                metadata.importXMPMetadata(updateCreationDate(metadata, creationDate));
                document.save(updated.toFile(), CompressParameters.NO_COMPRESSION);
            }
            Files.move(updated, pdf, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException | ParserConfigurationException | SAXException | TransformerException e) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "Не удалось согласовать дату создания PDF/A",
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

    private static byte[] updateCreationDate(PDMetadata metadata, Calendar creationDate)
            throws IOException, ParserConfigurationException, SAXException, TransformerException {
        DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
        documentFactory.setNamespaceAware(true);
        documentFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        documentFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        documentFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        documentFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Document xmp;
        try (InputStream input = metadata.exportXMPMetadata()) {
            xmp = documentFactory.newDocumentBuilder().parse(input);
        }

        NodeList dates = xmp.getElementsByTagNameNS(XMP_NAMESPACE, "CreateDate");
        if (dates.getLength() != 1) {
            throw AppException.build(
                    ErrorCode.FILE_PROCESSING_ERROR,
                    "XMP не содержит единственной даты создания"
            );
        }
        dates.item(0).setTextContent(creationDate.toInstant().toString());

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformerFactory.newTransformer().transform(new DOMSource(xmp), new StreamResult(output));
        return output.toByteArray();
    }
}
