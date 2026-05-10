package com.example.delivery;

import com.example.delivery.entity.ShippingLeadTime;
import com.example.delivery.repository.ShippingLeadTimeRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * フェーズX-5：{@link ShippingLeadTimeRepository} の派生メソッドおよび UNIQUE 制約検証。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ShippingLeadTimeRepositoryTest {

    @Autowired private ShippingLeadTimeRepository repository;

    @Test
    void findByShippingMethodIdAndPrefectureはヒット時にOptionalで返す() {
        Optional<ShippingLeadTime> hit = repository.findByShippingMethodIdAndPrefecture(1L, "東京都");
        assertTrue(hit.isPresent(), "東京都 home_delivery はマスタに存在");
        assertEquals(3, hit.get().getLeadTimeDays(), "東京都 home_delivery は標準値 3日");
    }

    @Test
    void findByShippingMethodIdAndPrefecture_厳密不一致はOptionalEmpty() {
        Optional<ShippingLeadTime> miss = repository.findByShippingMethodIdAndPrefecture(1L, "東京");
        assertTrue(miss.isEmpty(), "厳密一致のみ。'東京' は '東京都' とマッチしない");
    }

    @Test
    void findByShippingMethodIdOrderByIdAscは47件返す() {
        List<ShippingLeadTime> rows = repository.findByShippingMethodIdOrderByIdAsc(1L);
        assertEquals(47, rows.size(), "shipping_method_id=1 のリードタイムは 47 都道府県分");
    }

    @Test
    void unique制約により同一_method_x_prefecture_の重複insertは失敗する() {
        ShippingLeadTime dup = new ShippingLeadTime();
        dup.setShippingMethodId(1L);
        dup.setPrefecture("東京都"); // 既存と重複
        dup.setLeadTimeDays(99);

        assertThrows(Exception.class, () -> {
            repository.saveAndFlush(dup);
        }, "UNIQUE(shipping_method_id, prefecture) 違反となるはず");
    }
}
