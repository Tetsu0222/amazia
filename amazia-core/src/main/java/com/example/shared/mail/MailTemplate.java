package com.example.shared.mail;

/**
 * フェーズ17 Step 7 / 設計書 §6.1：
 * {@code config/mail/batch_*.yml} から読み込んだバッチ通知メールテンプレート。
 *
 * <p>{@link MailTemplateLoader} が classpath から読み込み、
 * {@link SesMailSender} がプレースホルダ展開のうえ SES 送出に渡す不変オブジェクト。
 *
 * @param id              テンプレートID（例：{@code batch_inventory_inconsistency}）
 * @param level           通知レベル（{@code INFO} / {@code WARN} / {@code ERROR}）
 * @param subscriptionTag 既定の購読タグ（呼び出し側が上書き可）
 * @param subject         件名テンプレ（{@code {{key}}} 形式のプレースホルダを含む）
 * @param body            本文テンプレ（{@code {{key}}} 形式のプレースホルダを含む）
 */
public record MailTemplate(String id,
                           String level,
                           String subscriptionTag,
                           String subject,
                           String body) {
}
