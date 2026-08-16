package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.*;

import com.aivle.backend.taskrun.contract.MarketInterviewContract;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class MarketInterviewContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void qualitativeSyntheticContractPasses() {
        assertThatCode(() -> MarketInterviewContract.validate(valid())).doesNotThrowAnyException();
    }

    @Test void syntheticFalseIsRejected() {
        ObjectNode value = valid(); value.put("synthetic", false);
        assertThatThrownBy(() -> MarketInterviewContract.validate(value)).isInstanceOf(ExecutionFailure.class);
    }

    @Test void populationPercentageIsRejected() {
        ObjectNode value = valid();
        ((ObjectNode) value.path("themes").path(0)).put("description", "고객의 80%가 구매한다");
        assertThatThrownBy(() -> MarketInterviewContract.validate(value)).isInstanceOf(ExecutionFailure.class);
    }

    private ObjectNode valid() {
        return (ObjectNode) mapper.readTree("""
          {"contract":"market-interview-result-v1","schemaVersion":"1.0","synthetic":true,
           "participants":[
             {"participantId":"P1","label":"가상 참여자 A","profile":"소규모 매장 운영자","context":"발주 전 검토","needs":["간단한 도입"]},
             {"participantId":"P2","label":"가상 참여자 B","profile":"초기 사용자","context":"처음 비교","needs":["명확한 가격"]}],
           "interviews":[
             {"participantId":"P1","questions":[{"question":"현재 어떻게 해결하나요?","answer":"수기로 확인합니다.","uncertainty":"실제 빈도 확인 필요"},{"question":"무엇이 걱정되나요?","answer":"도입 시간이 걱정됩니다.","uncertainty":"현장 확인 필요"},{"question":"언제 쓸까요?","answer":"반복 업무 때 고려합니다.","uncertainty":"사용 맥락 확인 필요"}],"concerns":["도입 시간"],"purchaseTriggers":["간단한 설정"],"objections":[],"unmetNeeds":["교육"]},
             {"participantId":"P2","questions":[{"question":"첫 인상은 어떤가요?","answer":"설명이 더 필요합니다.","uncertainty":"표현 확인 필요"},{"question":"무엇이 걸리나요?","answer":"가격 기준이 궁금합니다.","uncertainty":"가격 민감도 확인 필요"},{"question":"무엇을 확인할까요?","answer":"지원 범위를 묻겠습니다.","uncertainty":"실제 지원 요구 확인 필요"}],"concerns":["가격"],"purchaseTriggers":[],"objections":["지원 범위"],"unmetNeeds":[]}],
           "themes":[{"title":"도입 부담","description":"설정과 지원 범위를 먼저 확인하려는 관점","participantIds":["P1","P2"]}],
           "objections":["도입 부담"],"unmetNeeds":["초기 교육"],"purchaseTriggers":["쉬운 설정"],
           "followUpQuestions":["현재 해결 방식은 무엇인가요?","도입 전에 무엇을 확인하나요?","어떤 지원이 필요한가요?"],
           "limitations":["실제 고객 조사 결과가 아닙니다.","통계적 대표성이 없으며 실제 인터뷰로 확인해야 합니다."]}
          """);
    }
}
