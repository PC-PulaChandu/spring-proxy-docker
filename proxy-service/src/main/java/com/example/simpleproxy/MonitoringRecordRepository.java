package com.example.simpleproxy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MonitoringRecordRepository extends JpaRepository<MonitoringRecord, Long> {
}
