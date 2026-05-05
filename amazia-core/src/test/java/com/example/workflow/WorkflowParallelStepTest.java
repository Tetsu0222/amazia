package com.example.workflow;

import com.example.shared.config.TestAwsConfig;
import com.example.workflow.entity.WorkflowRequestDetail;
import com.example.workflow.repository.WorkflowRequestDetailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 設計書 5.3：並列ステップで一人が reject → 親 rejected、
 * 他の pending は waiting に戻して「承認不要」となること。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class WorkflowParallelStepTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkflowRequestDetailRepository detailRepository;

    @Test
    void 並列ステップでsupervisorがrejectすると他のadmin行はwaitingに戻ること() throws Exception {
        long skuId = createSkuForStockChange();

        // stock 変更を申請（step1 並列：supervisor + admin、step2: senior_admin）
        String created = mockMvc.perform(post("/api/workflows")
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetType": "stock",
                      "targetId": %d,
                      "fields": [{"field":"quantity","before":10,"after":20}]
                    }""".formatted(skuId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long workflowId = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));

        // step1 の supervisor が reject
        mockMvc.perform(post("/api/workflows/" + workflowId + "/steps/1/reject")
                .header("X-User-Id", 100L)
                .header("X-User-Role", "supervisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"));

        // 詳細確認
        List<WorkflowRequestDetail> details =
            detailRepository.findByWorkflowRequestsIdOrderByStepNumberAscIdAsc(workflowId);

        WorkflowRequestDetail supervisorRow = details.stream()
            .filter(d -> "supervisor".equals(d.getTargetRole()))
            .findFirst().orElseThrow();
        WorkflowRequestDetail adminRow = details.stream()
            .filter(d -> d.getStepNumber() == 1 && "admin".equals(d.getTargetRole()))
            .findFirst().orElseThrow();
        WorkflowRequestDetail seniorRow = details.stream()
            .filter(d -> d.getStepNumber() == 2)
            .findFirst().orElseThrow();

        assertEquals("rejected", supervisorRow.getStatus(), "rejectした行は rejected");
        assertEquals("waiting",  adminRow.getStatus(),      "並列の他の pending 行は waiting に戻す（承認不要）");
        assertEquals("waiting",  seniorRow.getStatus(),     "step2 は waiting 維持");
    }

    @Test
    void 並列ステップで両者承認すると次ステップが開始されること() throws Exception {
        long skuId = createSkuForStockChange();

        String created = mockMvc.perform(post("/api/workflows")
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetType": "stock",
                      "targetId": %d,
                      "fields": [{"field":"quantity","before":10,"after":20}]
                    }""".formatted(skuId)))
                .andReturn().getResponse().getContentAsString();
        long workflowId = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));

        // step1 supervisor 承認 → workflow はまだ pending
        mockMvc.perform(post("/api/workflows/" + workflowId + "/steps/1/approve")
                .header("X-User-Id", 100L)
                .header("X-User-Role", "supervisor"))
                .andExpect(jsonPath("$.status").value("pending"));

        // step1 admin 承認 → step2 の pending が出始める
        mockMvc.perform(post("/api/workflows/" + workflowId + "/steps/1/approve")
                .header("X-User-Id", 1L)
                .header("X-User-Role", "admin"))
                .andExpect(jsonPath("$.status").value("pending"));

        List<WorkflowRequestDetail> details =
            detailRepository.findByWorkflowRequestsIdOrderByStepNumberAscIdAsc(workflowId);
        WorkflowRequestDetail seniorRow = details.stream()
            .filter(d -> d.getStepNumber() == 2)
            .findFirst().orElseThrow();
        assertEquals("pending", seniorRow.getStatus(), "全 step1 承認後は step2 が pending");
    }

    private long createSkuForStockChange() throws Exception {
        // 商品を作るとデフォルト SKU + 在庫が10で自動生成される
        String created = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"在庫商品\",\"description\":\"d\",\"price\":500,\"stock\":10}"))
                .andReturn().getResponse().getContentAsString();
        long productId = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));

        // products/{id}/skus などで SKU 一覧取れるが、テストでは直接 productId == skuId を仮定せず
        // /api/products/{id}/skus が取れる形なら使う。シンプルに productId を skuId 相当として使えないので
        // この時点で skuId はテストに渡せない。よって stock テーブル側 findById を仮想化するため、
        // ここでは productId をそのまま target_id として渡してテストする。
        // ※ApplyWorkflowService の applyStock は反映時のみ skuId 検索する。
        //    申請・承認段階のテストでは反映前にすべて完結するためこれで通る（最終ステップで反映する場合は
        //    別途 applyTest で扱う）。
        return productId;
    }
}
