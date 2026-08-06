package com.aivle.backend.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class TestDocxFactory {
    private TestDocxFactory() {
    }

    public static byte[] document(String... paragraphs) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : paragraphs) {
                document.createParagraph().createRun().setText(text);
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("test DOCX cannot be created", exception);
        }
    }
}
