package com.aivle.backend.report.repository;
import com.aivle.backend.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReportRepository extends JpaRepository<Report, Long> {}
