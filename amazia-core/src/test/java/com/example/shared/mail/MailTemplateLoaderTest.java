package com.example.shared.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 7 / 設計書 §6.1：
 * 6 個の YAML テンプレが classpath から読めること、{@code {{key}}} 置換が正しく動くことを検証する。
 */
class MailTemplateLoaderTest {

    private MailTemplateLoader loader;

    @BeforeEach
    void setUp() {
        loader = new MailTemplateLoader();
        loader.load();
    }

    @Test
    void MTL_1_設計書_10_1_の_6_テンプレが全て読み込める() {
        for (String id : new String[]{
                "batch_inventory_inconsistency",
                "batch_sales_mismatch",
                "batch_delivery_delay",
                "batch_postal_integrity_failed",
                "batch_job_failed",
                "batch_digest"}) {
            MailTemplate t = loader.get(id);
            assertNotNull(t, "template not loaded: " + id);
            assertEquals(id, t.id());
            assertNotNull(t.level());
            assertNotNull(t.subject());
            assertNotNull(t.body());
        }
    }

    @Test
    void MTL_2_未知_ID_は_IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> loader.get("unknown_template"));
    }

    @Test
    void MTL_3_プレースホルダ_を差込値で置換する() {
        String rendered = loader.render(
                "商品ID={{productId}} / 差分={{diffQty}}",
                Map.of("productId", "42", "diffQty", "-3"));
        assertEquals("商品ID=42 / 差分=-3", rendered);
    }

    @Test
    void MTL_4_NULL_値は空文字に置換される() {
        java.util.Map<String, String> values = new java.util.HashMap<>();
        values.put("productId", null);
        String rendered = loader.render("ID={{productId}}/end", values);
        assertEquals("ID=/end", rendered);
    }

    @Test
    void MTL_5_差込値が無いプレースホルダはそのまま残る() {
        String rendered = loader.render("ID={{productId}}/{{warehouse}}", Map.of("productId", "1"));
        assertEquals("ID=1/{{warehouse}}", rendered);
    }

    @Test
    void MTL_6_INFO_は無く_全テンプレが_WARN_または_ERROR_である() {
        // 設計書 §6.2.2：SES 送信は WARN/ERROR のみ。バッチ通知テンプレに INFO は不要。
        for (String id : new String[]{
                "batch_inventory_inconsistency",
                "batch_sales_mismatch",
                "batch_delivery_delay",
                "batch_postal_integrity_failed",
                "batch_job_failed",
                "batch_digest"}) {
            MailTemplate t = loader.get(id);
            assertTrue("WARN".equals(t.level()) || "ERROR".equals(t.level()),
                    "template " + id + " level=" + t.level());
        }
    }
}
