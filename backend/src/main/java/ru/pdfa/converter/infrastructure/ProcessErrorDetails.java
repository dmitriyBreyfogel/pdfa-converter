package ru.pdfa.converter.infrastructure;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Короткие выдержки из отчётов внешних программ для HTTP-ответа об ошибке. */
final class ProcessErrorDetails {
    private static final int MAX_BYTES = 16 * 1024;

    private ProcessErrorDetails() {
    }

    static Map<String, Object> fromLog(Path log) {
        return fromFiles(null, log);
    }

    static Map<String, Object> fromValidation(Path report, Path log) {
        return fromFiles(report, log);
    }

    private static Map<String, Object> fromFiles(Path report, Path log) {
        Map<String, Object> details = new HashMap<>();
        if (report != null) {
            String content = tail(report);
            if (!content.isBlank()) {
                details.put("report", content);
            }
        }
        if (log != null) {
            String content = tail(log);
            if (!content.isBlank()) {
                details.put("log", content);
            }
        }
        return Map.copyOf(details);
    }

    private static String tail(Path file) {
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long size = input.length();
            int count = (int) Math.min(size, MAX_BYTES);
            byte[] bytes = new byte[count];
            input.seek(size - count);
            input.readFully(bytes);
            String content = new String(bytes, StandardCharsets.UTF_8).strip();
            return size > count ? "… начало вывода опущено\n" + content : content;
        }
        catch (IOException ignored) {
            return "";
        }
    }
}
