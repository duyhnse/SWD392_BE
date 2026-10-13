package swd392.group6.AIVES.ai.mock;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * What the AI node does for {@code INDEX_MATERIAL} (04 §9.1), done locally so the mock indexes real text:
 * PDF page by page ("Page n"), PPTX slide by slide with notes ("Slide n"), DOCX paragraphs under headings
 * ("Section &lt;heading&gt;"), then chunks of about {@code sizeWords} words with {@code overlapWords} overlap that
 * never cross a page / slide / section.
 */
final class MockDocumentText {

    /** A page, slide or section with its location label. */
    record Part(String label, int pageFrom, int pageTo, String text) {
    }

    record Chunk(String content, String label, int pageFrom, int pageTo, int words) {
    }

    /** The file has no extractable text (scanned PDF, empty deck). */
    static final class NoTextException extends Exception {
        NoTextException() {
            super("The file has no text layer");
        }
    }

    private MockDocumentText() {
    }

    static List<Part> parts(byte[] data, String contentType, String fileName) throws IOException {
        String name = fileName == null ? "" : fileName.toLowerCase();
        if (contentType.contains("pdf") || name.endsWith(".pdf")) {
            return pdf(data);
        }
        if (contentType.contains("presentation") || name.endsWith(".pptx")) {
            return pptx(data);
        }
        if (contentType.contains("wordprocessing") || name.endsWith(".docx")) {
            return docx(data);
        }
        throw new IOException("Unsupported file type " + contentType);
    }

    static List<Chunk> chunks(List<Part> parts, int sizeWords, int overlapWords) throws NoTextException {
        int size = Math.max(20, sizeWords);
        int step = Math.max(1, size - Math.clamp(overlapWords, 0, size / 2));
        List<Chunk> result = new ArrayList<>();
        for (Part part : parts) {
            String[] words = part.text().strip().split("\\s+");
            if (words.length == 0 || words[0].isEmpty()) {
                continue;
            }
            for (int start = 0; start < words.length; start += step) {
                int end = Math.min(words.length, start + size);
                result.add(new Chunk(String.join(" ", java.util.Arrays.copyOfRange(words, start, end)), part.label(),
                        part.pageFrom(), part.pageTo(), end - start));
                if (end == words.length) {
                    break;
                }
            }
        }
        if (result.isEmpty()) {
            throw new NoTextException();
        }
        return result;
    }

    private static List<Part> pdf(byte[] data) throws IOException {
        List<Part> parts = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                parts.add(new Part("Page " + page, page, page, stripper.getText(doc)));
            }
        }
        return parts;
    }

    private static List<Part> pptx(byte[] data) throws IOException {
        List<Part> parts = new ArrayList<>();
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(data))) {
            int n = 0;
            for (XSLFSlide slide : show.getSlides()) {
                n++;
                StringBuilder text = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape t) {
                        text.append(t.getText()).append('\n');
                    }
                }
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    for (XSLFShape shape : notes.getShapes()) {
                        if (shape instanceof XSLFTextShape t) {
                            text.append(t.getText()).append('\n');
                        }
                    }
                }
                parts.add(new Part("Slide " + n, n, n, text.toString()));
            }
        }
        return parts;
    }

    private static List<Part> docx(byte[] data) throws IOException {
        List<Part> parts = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            String heading = "Mở đầu";
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String style = p.getStyle();
                boolean isHeading = style != null && style.toLowerCase().startsWith("heading");
                if (isHeading && !p.getText().isBlank()) {
                    if (!text.isEmpty()) {
                        parts.add(new Part(label(heading), 0, 0, text.toString()));
                    }
                    heading = p.getText().strip();
                    text = new StringBuilder();
                } else {
                    text.append(p.getText()).append('\n');
                }
            }
            if (!text.isEmpty()) {
                parts.add(new Part(label(heading), 0, 0, text.toString()));
            }
        }
        return parts;
    }

    private static String label(String heading) {
        String label = "Section " + heading;
        return label.length() > 100 ? label.substring(0, 100) : label;
    }
}
