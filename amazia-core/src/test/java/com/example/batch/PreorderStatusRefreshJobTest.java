package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.job.PreorderStatusRefreshJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 3-2: PreorderStatusRefreshJob の TDD（設計書 §3.1 ② / 計画書 §4-2）。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class PreorderStatusRefreshJobTest {

    @Autowired private PreorderStatusRefreshJob job;
    @Autowired private BatchExecutionRepository batchExecutionRepository;
    @Autowired private ProductRepository productRepository;

    @Test
    void PRE_1_今日が_releaseDate_の商品が抽出件数に含まれる() {
        Integer beforeMatched = lastTargetCount();
        Product matched = newProduct("PRE-match-" + System.nanoTime());
        matched.setReleaseDate(LocalDate.now());
        productRepository.save(matched);

        Product unrelated = newProduct("PRE-other-" + System.nanoTime());
        unrelated.setReleaseDate(LocalDate.now().plusDays(7));
        productRepository.save(unrelated);

        job.run("scheduler");

        BatchExecution exec = latestExecution();
        assertEquals("SUCCESS", exec.getStatus());
        assertNotNull(exec.getTargetCount());
        if (beforeMatched != null) {
            assertTrue(exec.getTargetCount() >= beforeMatched + 1,
                    "今日該当の商品が 1 件追加された分、抽出件数も 1 件以上増える");
        }
    }

    @Test
    void PRE_2_releaseDate_publishStart_preorderStartDate_いずれかが今日と一致した場合だけ抽出される() {
        // matchesToday() を直接検証する（unit）
        Product onPublish = newProduct("publish-today");
        onPublish.setPublishStart(LocalDateTime.now());
        Product onRelease = newProduct("release-today");
        onRelease.setReleaseDate(LocalDate.now());
        Product onPreorder = newProduct("preorder-today");
        onPreorder.setPreorderStartDate(LocalDate.now());
        Product none = newProduct("none");
        none.setReleaseDate(LocalDate.now().plusDays(1));

        LocalDate today = LocalDate.now();
        assertTrue(PreorderStatusRefreshJob.matchesToday(onPublish, today));
        assertTrue(PreorderStatusRefreshJob.matchesToday(onRelease, today));
        assertTrue(PreorderStatusRefreshJob.matchesToday(onPreorder, today));
        assertFalse(PreorderStatusRefreshJob.matchesToday(none, today));
    }

    private Integer lastTargetCount() {
        return batchExecutionRepository
                .findByJobNameOrderByStartedAtDesc(PreorderStatusRefreshJob.JOB_NAME)
                .stream().findFirst().map(BatchExecution::getTargetCount).orElse(null);
    }

    private BatchExecution latestExecution() {
        return batchExecutionRepository
                .findByJobNameOrderByStartedAtDesc(PreorderStatusRefreshJob.JOB_NAME)
                .get(0);
    }

    private Product newProduct(String name) {
        Product p = new Product();
        p.setName(name);
        p.setStatusCode("ON_SALE");
        return p;
    }
}
