package com.example.batch;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchJobLockRegistry;
import com.example.batch.config.BatchResult;
import com.example.batch.entity.BatchExecution;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 2: BatchJobLockRegistry / AbstractBatchJob の多重起動防止
 * （LOCK-1 / LOCK-2）。
 */
@SpringBootTest
@Import({TestAwsConfig.class, BatchJobLockRegistryTest.JobBeans.class})
@ActiveProfiles("test")
class BatchJobLockRegistryTest {

    @Autowired private BlockingJob blockingJob;
    @Autowired private ThrowingJob throwingJob;
    @Autowired private BatchExecutionRepository repository;
    @Autowired private BatchJobLockRegistry lockRegistry;

    @Test
    void LOCK_1_2スレッドが同時に起動しても_RUNNING_は_1件しか作られない() throws Exception {
        int beforeCount = repository.findByJobNameOrderByStartedAtDesc("BlockingJob").size();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch insideExecute = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        blockingJob.gateBeforeReturn = finish;
        blockingJob.signalOnEntry = insideExecute;

        Thread t1 = new Thread(() -> {
            try { start.await(); } catch (InterruptedException ignored) {}
            blockingJob.run("scheduler");
        });
        Thread t2 = new Thread(() -> {
            try { start.await(); } catch (InterruptedException ignored) {}
            blockingJob.run("scheduler");
        });
        t1.start();
        t2.start();
        start.countDown();

        // 先行スレッドが execute() に入るまで待つ
        assertTrue(insideExecute.await(2, TimeUnit.SECONDS),
                "先行スレッドが execute() に到達するはず");
        // この時点では先行のみ RUNNING・後発はロック取得失敗で何も書かない
        Thread.sleep(200);  // 後発スレッドの run() がロック取得失敗で抜けるのを待つ
        long activeAfterEntry = repository.findByStatus("RUNNING").stream()
                .filter(e -> "BlockingJob".equals(e.getJobName())).count();
        assertEquals(1, activeAfterEntry, "新規 RUNNING は 1 件のみ");

        // 先行を解放
        finish.countDown();
        t1.join(2000);
        t2.join(2000);

        List<BatchExecution> rows = repository.findByJobNameOrderByStartedAtDesc("BlockingJob");
        assertEquals(beforeCount + 1, rows.size(),
                "後発スレッドはロック取得失敗で batch_executions を作らない");
        assertEquals("SUCCESS", rows.get(0).getStatus());
    }

    @Test
    void LOCK_2_例外を投げてもロックは解放され後続が成立する() {
        throwingJob.run("scheduler");

        // 後続呼び出しもロックが解放されているので進行できる
        assertTrue(lockRegistry.tryAcquire("ThrowingJob"),
                "finally でロックが解放されているはず");
        lockRegistry.release("ThrowingJob");

        BatchExecution latest = repository
                .findByJobNameOrderByStartedAtDesc("ThrowingJob").get(0);
        assertEquals("FAILED", latest.getStatus());
    }

    // ---- テスト専用ジョブ ----

    @TestConfiguration
    static class JobBeans {
        @Bean BlockingJob blockingJob() { return new BlockingJob(); }
        @Bean ThrowingJob throwingJob() { return new ThrowingJob(); }
    }

    /** 外部の CountDownLatch で実行を待たせ、多重起動の有無を観察できるテスト用ジョブ。 */
    static class BlockingJob extends AbstractBatchJob {
        volatile CountDownLatch gateBeforeReturn;
        volatile CountDownLatch signalOnEntry;
        final AtomicInteger entered = new AtomicInteger();

        @Override protected String jobName() { return "BlockingJob"; }
        @Override protected BatchResult execute() throws Exception {
            entered.incrementAndGet();
            if (signalOnEntry != null) signalOnEntry.countDown();
            if (gateBeforeReturn != null) gateBeforeReturn.await(5, TimeUnit.SECONDS);
            return BatchResult.of(1, 1, 0);
        }
    }

    static class ThrowingJob extends AbstractBatchJob {
        @Override protected String jobName() { return "ThrowingJob"; }
        @Override protected BatchResult execute() {
            throw new IllegalStateException("boom");
        }
    }
}
