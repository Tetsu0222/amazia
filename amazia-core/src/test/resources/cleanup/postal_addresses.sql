-- phaseX-9 Step 4: PostalAddressIntegrityCheckJobTest 用 cleanup
-- 自テストで投入した postal_addresses (100-0001 / 530-0001 等) は @Transactional でロールバック
-- されるが、他テスト残置の影響を受けないよう各テスト前に当該テーブルを TRUNCATE する。
-- 関連 console_notifications は @Transactional ロールバックで自然に消えるため対象外。
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE postal_addresses;
SET REFERENTIAL_INTEGRITY TRUE;
