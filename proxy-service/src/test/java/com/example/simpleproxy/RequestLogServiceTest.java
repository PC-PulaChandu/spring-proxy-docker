package com.example.simpleproxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestLogServiceTest {

    @Mock
    private MonitoringRecordRepository repository;

    private RequestLogService service;

    @BeforeEach
    void setUp() {
        service = new RequestLogService(repository, 1, 1, 10);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void savesTheRecordOnAnotherThread() {
        MonitoringRecord record = new MonitoringRecord();
        record.setRequestPath("/backend1/hello");

        service.log(record);

        verify(repository, timeout(2000)).save(record);
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(service.stats().get("saved")).isEqualTo(1L));
    }

    @Test
    void countsFailuresWithoutThrowingToTheCaller() {
        when(repository.save(any(MonitoringRecord.class)))
                .thenThrow(new RuntimeException("db down"));

        service.log(new MonitoringRecord());

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(service.stats().get("failed")).isEqualTo(1L));
        assertThat(service.stats().get("saved")).isEqualTo(0L);
    }
}