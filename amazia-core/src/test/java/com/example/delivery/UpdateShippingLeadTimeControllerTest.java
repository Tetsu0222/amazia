package com.example.delivery;

import com.example.delivery.entity.ShippingLeadTime;
import com.example.delivery.repository.ShippingLeadTimeRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.shared.config.TestAwsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フェーズX-5：PATCH /api/shipping-lead-times/{id} の検証。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UpdateShippingLeadTimeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ShippingLeadTimeRepository repository;
    @Autowired private OperationLogRepository operationLogRepository;

    @Test
    void PATCHでlead_time_daysを更新できoperation_logsに記録される() throws Exception {
        ShippingLeadTime target = repository.findByShippingMethodIdAndPrefecture(1L, "東京都").orElseThrow();
        Long id = target.getId();
        int oldDays = target.getLeadTimeDays();

        Map<String, Object> body = new HashMap<>();
        body.put("leadTimeDays", oldDays + 1);

        mockMvc.perform(patch("/api/shipping-lead-times/" + id)
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadTimeDays").value(oldDays + 1));

        ShippingLeadTime updated = repository.findById(id).orElseThrow();
        assertEquals(oldDays + 1, updated.getLeadTimeDays());

        List<OperationLog> logs = operationLogRepository.findAll().stream()
                .filter(l -> "update_shipping_lead_time".equals(l.getAction())
                          && id.equals(l.getTargetId()))
                .toList();
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).getComment().contains("旧:" + oldDays + "日"));
        assertTrue(logs.get(0).getComment().contains("新:" + (oldDays + 1) + "日"));
    }

    @Test
    void lead_time_daysが0でも許容される_無効化運用() throws Exception {
        ShippingLeadTime target = repository.findByShippingMethodIdAndPrefecture(1L, "大阪府").orElseThrow();

        Map<String, Object> body = new HashMap<>();
        body.put("leadTimeDays", 0);

        mockMvc.perform(patch("/api/shipping-lead-times/" + target.getId())
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadTimeDays").value(0));
    }

    @Test
    void lead_time_daysが負数のリクエストは422を返す() throws Exception {
        ShippingLeadTime target = repository.findByShippingMethodIdAndPrefecture(1L, "京都府").orElseThrow();

        Map<String, Object> body = new HashMap<>();
        body.put("leadTimeDays", -1);

        mockMvc.perform(patch("/api/shipping-lead-times/" + target.getId())
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void lead_time_days欠落で422を返す() throws Exception {
        ShippingLeadTime target = repository.findByShippingMethodIdAndPrefecture(1L, "兵庫県").orElseThrow();

        Map<String, Object> body = new HashMap<>();
        // leadTimeDays 欠落

        mockMvc.perform(patch("/api/shipping-lead-times/" + target.getId())
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 存在しないIDは404を返す() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("leadTimeDays", 5);

        mockMvc.perform(patch("/api/shipping-lead-times/999999")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }
}
