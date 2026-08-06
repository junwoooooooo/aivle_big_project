package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException; import com.aivle.backend.common.exception.ErrorCode;
import java.util.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import tools.jackson.databind.JsonNode;

@Service
public class PersonaJourneyPersistenceService {
    private final PersonaStudyRepository studies; private final PersonaCardRepository cards; private final PersonaCardVersionRepository versions;
    private final PersonaInterviewRepository interviews; private final PersonaInterviewMessageRepository messages;
    private final InterviewSynthesisRunRepository synthesisRuns; private final InterviewSynthesisRepository syntheses;
    public PersonaJourneyPersistenceService(PersonaStudyRepository studies,PersonaCardRepository cards,PersonaCardVersionRepository versions,
            PersonaInterviewRepository interviews,PersonaInterviewMessageRepository messages,
            InterviewSynthesisRunRepository synthesisRuns,InterviewSynthesisRepository syntheses){this.studies=studies;this.cards=cards;this.versions=versions;this.interviews=interviews;this.messages=messages;this.synthesisRuns=synthesisRuns;this.syntheses=syntheses;}

    @Transactional
    public void completeStudy(Long studyId,JsonNode result){PersonaStudy study=studies.findById(studyId).orElseThrow(this::notFound);if(study.getState()==PersonaStudy.State.READY)return;
        if(versions.findCurrentByStudy(studyId).isEmpty()){int order=1;for(JsonNode p:result.get("personas")){PersonaCard card=cards.save(PersonaCard.create(study.getProject(),study.getConceptVersion(),study,order++));versions.save(PersonaCardVersion.create(study.getProject(),study.getConceptVersion(),card,text(p,"name"),text(p,"shortLabel"),p.get("roleAndContext").toString(),p.get("problemAndNeeds").toString(),p.get("behaviorAndDecision").toString(),p.get("interviewFocus").toString()));}}study.succeed(result.toString());}

    @Transactional
    public void selectPersonas(Long studyId,Collection<Long> selectedVersionIds){Set<Long> ids=new HashSet<>(selectedVersionIds);List<PersonaCardVersion> values=versions.findCurrentByStudy(studyId);if(ids.isEmpty()||values.stream().filter(v->ids.contains(v.getId())).count()!=ids.size())throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);for(PersonaCardVersion v:values)v.getPersonaCard().select(ids.contains(v.getId()));}

    @Transactional
    public void completeInterview(Long interviewId,JsonNode result){PersonaInterview interview=interviews.findById(interviewId).orElseThrow(this::notFound);if(interview.getState()==PersonaInterview.State.SUCCEEDED)return;if(messages.findByInterviewIdAndDeletedAtIsNullOrderBySequenceNumber(interviewId).isEmpty()){int sequence=1;for(JsonNode m:result.get("messages"))messages.save(PersonaInterviewMessage.create(interview.getProject(),interview.getConceptVersion(),interview,sequence++,PersonaInterviewMessage.Category.valueOf(text(m,"category")),text(m,"question"),text(m,"answer")));}interview.succeed(result.toString());}

    @Transactional
    public void completeSynthesis(Long runId,JsonNode result){InterviewSynthesisRun run=synthesisRuns.findById(runId).orElseThrow(this::notFound);if(run.getState()==InterviewSynthesisRun.State.SUCCEEDED)return;if(syntheses.findByRunIdAndDeletedAtIsNull(runId).isEmpty())syntheses.save(InterviewSynthesis.create(run.getProject(),run.getConceptVersion(),run.getStudy(),run,result.get("commonThemes").toString(),result.get("conflictingViews").toString(),result.get("criticalNeeds").toString(),result.get("decisionBarriers").toString(),result.get("implications").toString(),result.get("researchNeeds").toString()));run.succeed(result.toString());}

    @Transactional public void failStudy(Long id,String reason){studies.findById(id).ifPresent(v->v.fail(reason));}
    @Transactional public void failInterview(Long id,String reason){interviews.findById(id).ifPresent(v->v.fail(reason));}
    @Transactional public void failSynthesis(Long id,String reason){synthesisRuns.findById(id).ifPresent(v->v.fail(reason));}
    private String text(JsonNode value,String field){JsonNode node=value.get(field);if(node==null||!node.isTextual()||node.asText().isBlank())throw new BusinessException(ErrorCode.AI_RESULT_INVALID);return node.asText();}
    private BusinessException notFound(){return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);}
}
