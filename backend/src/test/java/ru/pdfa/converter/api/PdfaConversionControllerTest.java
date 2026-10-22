package ru.pdfa.converter.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import ru.pdfa.converter.application.conversion.ConvertToPdfaUseCase;
import ru.pdfa.converter.domain.OutputFormat;
import ru.pdfa.converter.error.AppException;
import ru.pdfa.converter.error.ErrorCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PdfaConversionController.class)
class PdfaConversionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConvertToPdfaUseCase convertToPdfa;

    @Test
    void convertsUploadedFileAndDeletesTemporaryInput() throws Exception {
        byte[] uploaded = "document".getBytes();
        byte[] converted = "%PDF-1.7".getBytes();
        AtomicReference<Path> source = new AtomicReference<>();
        given(convertToPdfa.execute(any(Path.class), eq(OutputFormat.PDF_A_2b)))
                .willAnswer(invocation -> {
                    Path input = invocation.getArgument(0);
                    source.set(input);
                    assertArrayEquals(uploaded, Files.readAllBytes(input));
                    return converted;
                });

        var response = mockMvc.perform(multipart("/api/convert")
                        .file(new MockMultipartFile("file", "C:\\docs\\report.docx", null, uploaded))
                        .param("target", "PDF_A_2b"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(converted))
                .andReturn()
                .getResponse();

        String disposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertEquals("report.pdf", ContentDisposition.parse(disposition).getFilename());
        assertNotNull(source.get());
        assertFalse(Files.exists(source.get()));
    }

    @Test
    void deletesTemporaryInputWhenConversionFails() throws Exception {
        AtomicReference<Path> source = new AtomicReference<>();
        given(convertToPdfa.execute(any(Path.class), eq(OutputFormat.PDF_A_1b)))
                .willAnswer(invocation -> {
                    source.set(invocation.getArgument(0));
                    throw AppException.build(ErrorCode.EMPTY_FILE, "Входной файл пуст");
                });

        mockMvc.perform(multipart("/api/convert")
                        .file(new MockMultipartFile("file", "empty.docx", null, new byte[0]))
                        .param("target", "PDF_A_1b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));

        assertNotNull(source.get());
        assertFalse(Files.exists(source.get()));
    }

    @Test
    void returnsVeraPdfReportForFailedValidation() throws Exception {
        String report = "<report><validationReport isCompliant=\"false\"/></report>";
        given(convertToPdfa.execute(any(Path.class), eq(OutputFormat.PDF_A_1b)))
                .willThrow(AppException.build(
                        ErrorCode.PDF_A_VALIDATION_FAILED,
                        "Созданный PDF не соответствует формату PDF_A_1b",
                        Map.of("report", report)
                ));

        mockMvc.perform(multipart("/api/convert")
                        .file(new MockMultipartFile("file", "report.docx", null, "data".getBytes()))
                        .param("target", "PDF_A_1b"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PDF_A_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.report").value(report));
    }

    @Test
    void rejectsMissingOriginalFilename() throws Exception {
        mockMvc.perform(multipart("/api/convert")
                        .file(new MockMultipartFile("file", "", null, "data".getBytes()))
                        .param("target", "PDF_A_1b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_EXTENSION"));

        verifyNoInteractions(convertToPdfa);
    }

    @Test
    void returnsPayloadTooLargeForUploadLimit() throws Exception {
        given(convertToPdfa.execute(any(Path.class), eq(OutputFormat.PDF_A_1b)))
                .willThrow(new MaxUploadSizeExceededException(200L * 1024 * 1024));

        mockMvc.perform(multipart("/api/convert")
                        .file(new MockMultipartFile("file", "large.docx", null, "data".getBytes()))
                        .param("target", "PDF_A_1b"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }
}
