const base = (projectId) => `/api/v2/projects/${encodeURIComponent(projectId)}`;

export function createJourneyApi(client, projectId) {
  const root = base(projectId);
  return {
    async currentIdea() { return (await client.get(`${root}/ideas/current`)).data; },
    async saveText(input) { return (await client.post(`${root}/ideas`, input)).data; },
    async saveFile(title, file) {
      const form = new FormData();
      if (title) form.append('title', title);
      form.append('file', file);
      return (await client.upload(`${root}/ideas`, form)).data;
    },
    async currentInterpretation() { return (await client.get(`${root}/idea-interpretations/current`)).data; },
    async interpret() { return (await client.post(`${root}/idea-interpretations`, undefined, { timeoutMs: 90000 })).data; },
    async currentIdeaOrigin() { return (await client.get(`${root}/idea-origin`)).data; },
    async answerIdeaOriginQuestion(questionId, input) { return (await client.put(`${root}/idea-origin/questions/${encodeURIComponent(questionId)}`, input)).data; },
    async applyIdeaOrigin(draftVersionId) { return (await client.post(`${root}/idea-origin/apply`, { draftVersionId })).data; },
    async currentLegalPrecheck() { return (await client.get(`${root}/legal-prechecks/current`)).data; },
    async startLegalPrecheck() { return (await client.post(`${root}/legal-prechecks`)).data; },
    async refreshLegalPrecheckSources() { return (await client.post(`${root}/legal-prechecks/refresh`)).data; },
    async retryTaskRun(taskRunId) { return (await client.post(`${root}/task-runs/${encodeURIComponent(taskRunId)}/retry`, undefined, { headers: { 'Idempotency-Key': `journey-retry-${taskRunId}` } })).data; },
    async applyLegalAnswers(ideaOriginVersionId) { return (await client.post(`${root}/legal-prechecks/answers/apply`, { ideaOriginVersionId })).data; },
    async applyLegalAnswersAndRestart(ideaOriginVersionId) { return (await client.post(`${root}/legal-prechecks/answers/apply-and-restart`, { ideaOriginVersionId })).data; },
    async acceptLegalRevision(versionId, index) { return (await client.post(`${root}/legal-prechecks/versions/${encodeURIComponent(versionId)}/revision-suggestions/${encodeURIComponent(index)}/accept`)).data; },
    async acceptLegalRevisionsAndRestart(versionId, indexes) { return (await client.post(`${root}/legal-prechecks/versions/${encodeURIComponent(versionId)}/revision-suggestions/accept`, { indexes })).data; },
    async concepts() { return (await client.get(`${root}/concepts`)).data; },
    async currentConceptGeneration() { return (await client.get(`${root}/concept-generations/current`)).data; },
    async generateConcepts() { return (await client.post(`${root}/concept-generations`, undefined, { timeoutMs: 120000 })).data; },
    async currentQuick() { return (await client.get(`${root}/quick-assessments/current`)).data; },
    async quickAssessment() { return (await client.post(`${root}/quick-assessments`, undefined, { timeoutMs: 120000 })).data; },
    async currentShortlist() { return (await client.get(`${root}/shortlist`)).data; },
    async saveShortlist(input) { return (await client.put(`${root}/shortlist`, input)).data; },
    async currentDetailed() { return (await client.get(`${root}/detailed-analyses/current`)).data; },
    async detailedAnalysis(input) { return (await client.post(`${root}/detailed-analyses`, input, { timeoutMs: 120000 })).data; },
    async currentSelection() { return (await client.get(`${root}/concept-selection`)).data; },
    async selectConcept(input) { return (await client.put(`${root}/concept-selection`, input)).data; },
    async createPersonaStudy() { return (await client.post(`${root}/persona-studies`)).data; },
    async currentPersonaStudy() { return (await client.get(`${root}/persona-studies/current`)).data; },
    async generatePersonas() { return (await client.post(`${root}/persona-cards/generate`, undefined, { timeoutMs: 120000 })).data; },
    async personaCards() { return (await client.get(`${root}/persona-cards`)).data; },
    async runPersonaInterviews(input) { return (await client.post(`${root}/persona-interviews`, input, { timeoutMs: 180000 })).data; },
    async personaInterviews() { return (await client.get(`${root}/persona-interviews`)).data; },
    async synthesizeInterviews() { return (await client.post(`${root}/interview-syntheses`, undefined, { timeoutMs: 120000 })).data; },
    async currentInterviewSynthesis() { return (await client.get(`${root}/interview-syntheses/current`)).data; },
    async generateMarketing() { return (await client.post(`${root}/marketing-generations`, undefined, { timeoutMs: 150000 })).data; },
    async marketingWorkspace() { return (await client.get(`${root}/marketing-workspace`)).data; },
    async selectMarketingAsset(assetId) { return (await client.put(`${root}/marketing-assets/${encodeURIComponent(assetId)}/select`)).data; },
    async compareMarketing() { return (await client.post(`${root}/marketing-comparisons`, undefined, { timeoutMs: 150000 })).data; },
    async currentMarketingComparison() { return (await client.get(`${root}/marketing-comparisons/current`)).data; },
    async generateFinalReport() { return (await client.post(`${root}/final-reports`, undefined, { timeoutMs: 150000 })).data; },
    async currentFinalReport() { return (await client.get(`${root}/final-reports/current`)).data; },
    async decideFinalReport(reportId, input) { return (await client.put(`${root}/final-reports/${encodeURIComponent(reportId)}/decision`, input)).data; },
  };
}
