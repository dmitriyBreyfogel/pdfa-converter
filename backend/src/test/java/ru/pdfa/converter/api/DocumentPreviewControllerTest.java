package ru.pdfa.converter.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.pdfa.converter.application.preview.CreateDocumentPreviewUseCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentPreviewController.class)
class DocumentPreviewControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateDocumentPreviewUseCase createPreview;

    @Test
    void returnsPreviewAndDeletesTemporaryInput() throws Exception {
        byte[] uploaded = "document".getBytes();
        byte[] pdf = "%PDF-1.7".getBytes();
        AtomicReference<Path> source = new AtomicReference<>();
        given(createPreview.execute(any(Path.class))).willAnswer(invocation -> {
            Path input = invocation.getArgument(0);
            source.set(input);
            assertArrayEquals(uploaded, Files.readAllBytes(input));
            return pdf;
        });

        mockMvc.perform(multipart("/api/preview")
                        .file(new MockMultipartFile("file", "document.docx", null, uploaded)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(pdf))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        assertNotNull(source.get());
        assertFalse(Files.exists(source.get()));
    }
}
