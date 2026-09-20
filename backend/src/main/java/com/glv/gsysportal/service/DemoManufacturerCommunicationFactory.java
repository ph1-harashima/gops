package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.ManufacturerCommunicationResponse;
import org.springframework.stereotype.Component;

/**
 * Requirements MD 14章 (Phase 0.5 Source Review confirmed: Legacy has no
 * Supplier PO mail feature to reuse - SYS_SEND_MAIL is internal-notification
 * only) / implementation instructions 4章: 9/17 Prototype uses fixed Demo
 * values only.
 *
 * The "to"/"cc" addresses use the {@code .invalid} TLD, reserved by RFC 2606
 * specifically for addresses guaranteed never to resolve or be deliverable -
 * this is not a real Supplier mail address fetched from anywhere, and this
 * class never connects to any mail transport (implementation instructions
 * 0章 external network prohibition; no Demo Send this Step either).
 */
@Component
public class DemoManufacturerCommunicationFactory {

    /** Post-Freeze Visual Walkthrough Findings Fix (Finding #1,
     * docs/gops-visual-walkthrough-findings-fix.md): this manufacturer-facing
     * preview must reference the 正式PO番号 (Official PO No.), never the
     * Portal管理番号 - a real supplier-facing document/email uses G-SYS's own
     * PO number, not G-OPS's internal Order identifier (canonical
     * terminology §3.1/§3.3 of the fix task). Previously this method took
     * {@code prototypePoNoForDisplay} (the Portal管理番号) and fell back to
     * draftNo - both wrong for a Manufacturer-facing value. Now takes the
     * 正式PO番号 directly and shows "未発行" (matching the fix task's own
     * canonical "正式PO発行前" wording) when it has not been assigned yet,
     * rather than silently substituting a different number. */
    public ManufacturerCommunicationResponse build(PortalOrder order, String officialPoNoForDisplay) {
        String to = "demo-supplier+" + order.getSupplierCode().toLowerCase() + "@example.invalid";
        String cc = "demo-purchasing@example.invalid";
        String poRef = officialPoNoForDisplay != null ? officialPoNoForDisplay : "未発行";
        String subject = "【デモ】発注書 " + poRef + " - " + order.getSupplierNameSnapshot();
        // Gulliver UI最終仕上げ #3: この画面(旧PO Preview)は正式PO Excel連携
        // (OfficialPoExcelGenerationService/MailPreviewService)とは無関係な、
        // このLegacy Demo Preview固有の固定表示値。以前は英語+".pdf"だったため
        // 「正式PO Excel(.xlsx)」の実際の生成フローと混同されて見えていた。
        // Business Logicは変えず、Demo専用の表示であることが伝わる自然な日本語
        // 文言に変更し、実ファイルを連想させる拡張子付きファイル名は外した
        // (PDF生成機能を新設したわけではない - 添付ファイルは元々存在しない)。
        String body = "これはG-SYS Online Orderingのデモ環境向け表示です。\n"
                + "実際のメール送信は行われません。\n\n"
                + "正式PO番号: " + poRef + "\n"
                + "メーカー: " + order.getSupplierNameSnapshot() + "\n"
                + "ブランド: " + order.getBrandNameSnapshot() + "\n"
                + "発注日: " + order.getOrderDate() + "\n"
                + "希望納期: " + (order.getRequestedDelivery() != null ? order.getRequestedDelivery() : "-");
        String attachment = "デモ表示のみ（実際の添付ファイルはありません）";
        return new ManufacturerCommunicationResponse(to, cc, subject, body, attachment);
    }
}
