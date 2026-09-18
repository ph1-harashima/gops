package com.glv.gsysportal.service.excel;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
 * "G-OPS Standard Official PO PDF" - a Portal-defined reference Layout, NOT
 * a confirmed Gulliver-specified document (WORKING ASSUMPTION, explicitly
 * labelled as such on the document itself so nobody mistakes it for a
 * finalized Customer format). Renders only fields the caller supplies
 * ({@link OfficialPoPdfInput}, itself wrapping the SAME {@link OfficialPoExcelInput}
 * the Excel Generator consumes) - no field here is invented or looked up
 * independently, so this can never show data the Excel/Order itself doesn't
 * already carry.
 *
 * <p>Deliberately a plain, low-dependency PDFBox content stream (no template
 * engine, no external font/resource files) so the "future正式Layout差し替え"
 * requirement (7章) is just "replace this one class's drawing code" - the
 * {@link OfficialPoPdfInput} contract it consumes does not change.
 */
@Component
public class OfficialPoPdfGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 16f;
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();

    // Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
    // Supplier/Brand Names (order.getSupplierNameSnapshot() /
    // OfficialPoPreflightReadRepository.findBrandName) are real Legacy
    // Master data and routinely contain Japanese text - the Base-14
    // Helvetica font PDFBox ships (WinAnsiEncoding only) cannot encode a
    // single CJK glyph and throws IllegalArgumentException outright, so a
    // real embedded Unicode font is not optional here. Noto Sans JP (SIL
    // Open Font License, bundled under src/main/resources/fonts/) covers
    // both Japanese and the Latin/ASCII fields (PO No., dates, etc.) with
    // one single font - no separate Latin/CJK font-switching logic needed.
    private static final String FONT_RESOURCE = "fonts/NotoSansJP-VF.ttf";

    public byte[] generate(OfficialPoPdfInput input) {
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadFont(document);
            // PDType0Font (embedded TrueType) has no Base-14 Bold sibling to
            // pair it with - this Foundation Layout uses font SIZE (not
            // weight) for emphasis instead (see writeHeader/writeFields'
            // size arguments), acceptable for a "G-OPS Standard Format"
            // Working Assumption that is explicitly built to be replaced.
            RenderState state = new RenderState(document, font, font);
            state.newPage();
            writeHeader(state, input);
            writeFields(state, input);
            writeLineTable(state, input);
            state.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate Official PO PDF", e);
        }
    }

    private static PDFont loadFont(PDDocument document) throws IOException {
        try (InputStream in = new ClassPathResource(FONT_RESOURCE).getInputStream()) {
            return PDType0Font.load(document, in);
        }
    }

    private void writeHeader(RenderState state, OfficialPoPdfInput input) throws IOException {
        state.text(bold(state), 18, "OFFICIAL PURCHASE ORDER");
        state.gap(4);
        // Explicit "not a confirmed Layout" marker - always visible, never
        // omitted regardless of how this template evolves (7章's requirement
        // that this be identifiable as a G-OPS-defined format).
        state.text(regular(state), 9, "G-OPS Standard Format (Working Assumption - not an official Gulliver-specified Layout)");
        state.gap(14);
    }

    private void writeFields(RenderState state, OfficialPoPdfInput input) throws IOException {
        OfficialPoExcelInput d = input.data();
        state.field("PO Number", d.officialPoNo());
        state.field("Revision", String.format("%03d", input.revisionNo()));
        state.field("Supplier", displayCodeAndName(d.supplierCode(), input.supplierName()));
        state.field("Brand", displayCodeAndName(d.brandCode(), d.brandName()));
        state.field("Order Date", d.orderDate() == null ? null : d.orderDate().format(DATE));
        state.field("Delivery Week", d.deliveryWeek());
        state.field("Delivery Date", d.deliveryDate());
        state.field("Ship Via", d.shipVia());
        state.field("Ship Term", d.shipTerm());
        state.field("Payment Term", d.paymentTerm());
        state.gap(10);
    }

    private void writeLineTable(RenderState state, OfficialPoPdfInput input) throws IOException {
        List<OfficialPoExcelInput.Line> lines = input.data().lines();
        String currency = lines.isEmpty() ? "" : firstNonNull(lines);

        // Absolute offsets from the left margin (see RenderState.tableHeader's
        // Javadoc for why these are positions, not per-column widths).
        float[] columnX = {0, 85, 285, 330, 415};
        state.tableHeader(new String[]{"SKU", "Product Name", "Qty", "Unit Price", "Amount"}, columnX);

        BigDecimal total = BigDecimal.ZERO;
        for (OfficialPoExcelInput.Line line : lines) {
            BigDecimal unitPrice = line.unitPrice() == null ? BigDecimal.ZERO : line.unitPrice();
            BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(line.qty()));
            total = total.add(amount);
            state.tableRow(new String[]{
                    nullToDash(line.itemCode()),
                    nullToDash(line.description()),
                    String.valueOf(line.qty()),
                    line.unitPrice() == null ? "-" : currency + unitPrice.toPlainString(),
                    currency + amount.toPlainString(),
            }, columnX);
        }

        state.gap(6);
        state.text(bold(state), 11, "Total: " + currency + total.toPlainString());
    }

    private static String firstNonNull(List<OfficialPoExcelInput.Line> lines) {
        return lines.stream().map(OfficialPoExcelInput.Line::currencySymbol).filter(s -> s != null && !s.isBlank())
                .findFirst().orElse("");
    }

    private static String displayCodeAndName(String code, String name) {
        if (name == null || name.isBlank()) {
            return nullToDash(code);
        }
        return nullToDash(code) + " - " + name;
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private PDFont regular(RenderState state) {
        return state.regular;
    }

    private PDFont bold(RenderState state) {
        return state.bold;
    }

    /** Small mutable cursor over an in-progress {@link PDDocument} - kept
     * private/inner since no other class needs page-break/content-stream
     * bookkeeping. Adds a new page automatically once the cursor runs past
     * the bottom margin, so an Order with many lines never silently loses
     * rows off the bottom of a single page. */
    private static final class RenderState {
        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;
        private PDPageContentStream stream;
        private float cursorY;

        RenderState(PDDocument document, PDFont regular, PDFont bold) {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
        }

        void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            cursorY = PAGE_HEIGHT - MARGIN;
        }

        void ensureSpace(float needed) throws IOException {
            if (cursorY - needed < MARGIN) {
                newPage();
            }
        }

        void gap(float amount) {
            cursorY -= amount;
        }

        void text(PDFont font, float size, String value) throws IOException {
            ensureSpace(LINE_HEIGHT);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, cursorY);
            stream.showText(value == null ? "" : value);
            stream.endText();
            cursorY -= LINE_HEIGHT;
        }

        void field(String label, String value) throws IOException {
            if (value == null || value.isBlank()) {
                return; // Gap Analysis C-1: only render confirmed data, never a blank placeholder row.
            }
            ensureSpace(LINE_HEIGHT);
            stream.beginText();
            stream.setFont(bold, 10);
            stream.newLineAtOffset(MARGIN, cursorY);
            stream.showText(label + ":");
            stream.endText();
            stream.beginText();
            stream.setFont(regular, 10);
            stream.newLineAtOffset(MARGIN + 130, cursorY);
            stream.showText(value);
            stream.endText();
            cursorY -= LINE_HEIGHT;
        }

        /** {@code columnX} are ABSOLUTE offsets from the left margin (not
         * per-column widths) - each cell gets its own beginText/endText pair
         * positioned at {@code MARGIN + columnX[i]}, deliberately avoiding
         * PDFBox's newLineAtOffset relative-chaining semantics (a common,
         * easy-to-misread source of columns silently overlapping or
         * accumulating drift with each additional column). */
        void tableHeader(String[] cols, float[] columnX) throws IOException {
            ensureSpace(LINE_HEIGHT + 4);
            for (int i = 0; i < cols.length; i++) {
                stream.beginText();
                stream.setFont(bold, 9);
                stream.newLineAtOffset(MARGIN + columnX[i], cursorY);
                stream.showText(cols[i]);
                stream.endText();
            }
            cursorY -= 4;
            stream.moveTo(MARGIN, cursorY);
            stream.lineTo(PAGE_WIDTH - MARGIN, cursorY);
            stream.stroke();
            cursorY -= LINE_HEIGHT;
        }

        void tableRow(String[] cols, float[] columnX) throws IOException {
            ensureSpace(LINE_HEIGHT);
            for (int i = 0; i < cols.length; i++) {
                stream.beginText();
                stream.setFont(regular, 9);
                stream.newLineAtOffset(MARGIN + columnX[i], cursorY);
                stream.showText(cols[i] == null ? "" : cols[i]);
                stream.endText();
            }
            cursorY -= LINE_HEIGHT;
        }

        void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
