-- Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
-- Official PO PDF ("G-OPS Standard Format", WORKING ASSUMPTION - real
-- Gulliver Layout unconfirmed). Mirrors the existing generated_file_key/
-- generated_at columns' shape for the Excel artifact - a second, independent
-- artifact slot on the SAME Integration Request row, not a new state machine
-- (PDF generation never drives the Integration Status axis: PENDING/
-- GENERATED/SUBMITTED/CONFIRMED/FAILED stays exclusively about the Excel ->
-- Import Folder -> G-SYS hand-off, which the PDF is never part of).
ALTER TABLE official_po_integration_request
    ADD COLUMN pdf_file_key VARCHAR(255),
    ADD COLUMN pdf_generated_at TIMESTAMPTZ;
