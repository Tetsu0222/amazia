package com.example.batch;

import com.example.batch.service.BankTransferMockClient;
import com.example.batch.service.RandomGeneratorAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * フェーズ17 Step 3-3: BankTransferMockClient の mode 切替検証（設計書 §3.1 ③ R-1 / N-6）。
 */
class BankTransferMockClientTest {

    @Test
    void BTM_1_disabled_モードでは_DISABLED_を返す() {
        BankTransferMockClient client = newClient("disabled", null);
        assertEquals(BankTransferMockClient.Mode.DISABLED, client.mode());
        assertEquals(BankTransferMockClient.Result.DISABLED, client.verify());
    }

    @Test
    void BTM_2_mock_match_モードでは常に_MATCH_を返す() {
        BankTransferMockClient client = newClient("mock-match", null);
        for (int i = 0; i < 5; i++) {
            assertEquals(BankTransferMockClient.Result.MATCH, client.verify());
        }
    }

    @Test
    void BTM_3_mock_mismatch_rate_は乱数が閾値未満なら_MISMATCH_を返す() {
        RandomGeneratorAdapter random = mock(RandomGeneratorAdapter.class);
        when(random.nextDouble()).thenReturn(0.01); // 0.05 未満
        BankTransferMockClient client = newClient("mock-mismatch-rate", random);
        ReflectionTestUtils.setField(client, "mismatchRate", 0.05);

        assertEquals(BankTransferMockClient.Result.MISMATCH, client.verify());
    }

    @Test
    void BTM_4_mock_mismatch_rate_は乱数が閾値以上なら_MATCH_を返す() {
        RandomGeneratorAdapter random = mock(RandomGeneratorAdapter.class);
        when(random.nextDouble()).thenReturn(0.99);
        BankTransferMockClient client = newClient("mock-mismatch-rate", random);
        ReflectionTestUtils.setField(client, "mismatchRate", 0.05);

        assertEquals(BankTransferMockClient.Result.MATCH, client.verify());
    }

    private BankTransferMockClient newClient(String mode, RandomGeneratorAdapter random) {
        BankTransferMockClient client = new BankTransferMockClient(
                random != null ? random : mock(RandomGeneratorAdapter.class));
        ReflectionTestUtils.setField(client, "configuredMode", mode);
        ReflectionTestUtils.setField(client, "mismatchRate", 0.05);
        return client;
    }
}
