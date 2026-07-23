package com.aivle.backend.controller;

import com.aivle.backend.dto.AnalysisResultDto;
import com.aivle.backend.service.DocumentAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/project")
@CrossOrigin(originPatterns = "*", allowedHeaders = "*")
public class DocumentController {

    private final DocumentAnalysisService analysisService;

    // 생성자 주입
    public DocumentController(DocumentAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 1. .docx 사업기획서 업로드 및 AI 12개 항목 검증 API
     */
    @PostMapping("/upload-guideline")
    public ResponseEntity<?> uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
        try {
            AnalysisResultDto result = analysisService.analyzeDocument(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace(); // IntelliJ 콘솔에 에러 출력
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    /**
     * 2. AI 검증 완료 후 다음 단계 이동 시 데이터 수신 API
     *    (별도 DTO 없이 Map으로 수신 / 추후 Project, Product 엔티티에 분할 저장)
     */
    @PostMapping("/save-analysis")
    public ResponseEntity<?> saveAnalysisResult(@RequestBody Map<String, Object> payload) {
        try {
            String fileName = (String) payload.get("fileName");
            Object analysisData = payload.get("analysisData");

            System.out.println(">>> [수신 완료] 파일명: " + fileName);
            System.out.println(">>> [수신 완료] 분석 결과: " + analysisData);

            // TODO: 추후 기존 ProjectRepository와 ProductRepository를 주입받아
            // analysisData에서 항목을 추출한 뒤 Project / Product 엔티티에 각각 나누어 save() 수행

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "데이터 수신 완료 (Project & Product 엔티티 저장 준비 상태)"
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("데이터 저장 처리 중 오류가 발생했습니다.");
        }
    }
}