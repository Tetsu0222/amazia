package com.example.salesreturn;

import com.example.salesreturn.entity.SalesReturn;
import com.example.salesreturn.repository.SalesReturnRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14 Step A: SalesReturn Entity の永続化検証。
 * Step A で追加した quantity カラムを含めて読み書きできることを確認。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class SalesReturnEntityTest {

    @Autowired
    private SalesReturnRepository salesReturnRepository;

    @Test
    void SalesReturn_は_quantity_を含めて保存と取得ができる() {
        SalesReturn sr = new SalesReturn();
        sr.setSalesId(1L);
        sr.setStatus("REQUESTED");
        sr.setReason("色違いが届いた");
        sr.setQuantity(1);

        SalesReturn saved = salesReturnRepository.save(sr);
        assertNotNull(saved.getId());

        SalesReturn loaded = salesReturnRepository.findById(saved.getId()).orElseThrow();
        assertEquals("REQUESTED", loaded.getStatus());
        assertEquals(1, loaded.getQuantity());
        assertEquals("色違いが届いた", loaded.getReason());
        assertFalse(loaded.isNotifiedUser());
        assertFalse(loaded.isNotifiedAdmin());
    }
}
