package com.example.shared.mail;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * フェーズ17 Step 7 / 設計書 §6.1：
 * {@code config/mail/batch_*.yml} を classpath から読み込み、
 * テンプレートID → {@link MailTemplate} の Map として保持する。
 *
 * <p>テンプレートIDのリストは固定（設計書 10-1 で 6 個）。
 * 規約 4-1（環境変数で外出し）対象ではなく、フェーズ17 で確定した DSL ファイル群と一体運用する。
 */
@Component
public class MailTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(MailTemplateLoader.class);

    private static final String BASE_PATH = "config/mail/";
    private static final List<String> TEMPLATE_IDS = List.of(
            "batch_inventory_inconsistency",
            "batch_sales_mismatch",
            "batch_delivery_delay",
            "batch_postal_integrity_failed",
            "batch_job_failed",
            "batch_digest"
    );

    private final Map<String, MailTemplate> templates = new HashMap<>();

    @PostConstruct
    public void load() {
        for (String id : TEMPLATE_IDS) {
            MailTemplate template = readYaml(id);
            templates.put(id, template);
        }
        log.info("Mail templates loaded: {}", templates.keySet());
    }

    /**
     * テンプレートを取得する。存在しない ID を渡した場合は {@link IllegalArgumentException}。
     */
    public MailTemplate get(String id) {
        MailTemplate t = templates.get(id);
        if (t == null) {
            throw new IllegalArgumentException("Unknown mail template id: " + id);
        }
        return t;
    }

    /**
     * テンプレ文字列の {@code {{key}}} を {@code values} の値で逐次置換する。
     * 値が NULL ならば空文字に置き換える（テンプレ生成側がキー欠落で例外にならないように）。
     */
    public String render(String template, Map<String, String> values) {
        if (template == null) return "";
        String rendered = template;
        if (values != null) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                String placeholder = "{{" + e.getKey() + "}}";
                String value = e.getValue() != null ? e.getValue() : "";
                rendered = rendered.replace(placeholder, value);
            }
        }
        return rendered;
    }

    private MailTemplate readYaml(String id) {
        Resource resource = new ClassPathResource(BASE_PATH + id + ".yml");
        if (!resource.exists()) {
            throw new IllegalStateException("Mail template yaml not found: " + BASE_PATH + id + ".yml");
        }
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(resource);
        Properties props = yaml.getObject();
        if (props == null) {
            throw new IllegalStateException("Mail template yaml empty: " + id);
        }
        String yamlId = props.getProperty("template.id");
        String level = props.getProperty("template.level");
        String tag = props.getProperty("template.subscription-tag");
        String subject = props.getProperty("template.subject");
        String body = props.getProperty("template.body");
        if (yamlId == null || level == null || subject == null || body == null) {
            throw new IllegalStateException(
                    "Mail template yaml is missing required fields: " + id);
        }
        return new MailTemplate(yamlId, level, tag, subject, body);
    }
}
