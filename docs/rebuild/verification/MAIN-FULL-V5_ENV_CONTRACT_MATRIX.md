ENV_NAME|USED_BY|CATEGORY|DEFAULT|REQUIRED|ENV_EXAMPLE|E2E_EXAMPLE|DEMO_EXAMPLE|INFRA_EXAMPLE|PASSED_BY_COMPOSE|SECRET
---|---|---|---|---|---|---|---|---|---|---
AI_API_KEY|ai/app/providers/structured.py, ai/app/services/marketing_copy_service.py, ai/app/services/openai_banner_service.py, ai/app/tasks/marketing_visual/service.py, ai/app/twin/runner.py, ai/main.py, backend/src/main/resources/application.yaml, compose.yaml|REQUIRED_RUNTIME||YES|YES|YES|YES|NO|YES|YES
AI_APP_ENVIRONMENT|compose.yaml|OPTIONAL_RUNTIME|LOCAL|NO|YES|NO|NO|NO|YES|NO
AI_ARTIFACT_ALLOWED_ORIGINS|scripts/ai-local-smoke.ps1|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|YES|NO
AI_ARTIFACT_HTTP_TIMEOUT_SECONDS|compose.yaml|OPTIONAL_RUNTIME|10|NO|YES|NO|NO|NO|YES|NO
AI_ARTIFACT_MAX_BYTES|compose.yaml|OPTIONAL_RUNTIME|1048576|NO|YES|NO|NO|NO|YES|NO
AI_BASE_URL|ai/app/providers/structured.py, ai/app/services/marketing_copy_service.py, ai/app/services/openai_banner_service.py, ai/app/twin/runner.py, backend/src/main/resources/application.yaml, compose.yaml|OPTIONAL_RUNTIME||NO|YES|YES|YES|NO|YES|NO
AI_CONCEPT_GENERATION_CONCURRENCY|compose.yaml|OPTIONAL_RUNTIME|1|NO|YES|NO|NO|NO|YES|NO
AI_CONCEPT_TEST_FAILURE_INJECTION|compose.yaml|E2E_TEST_ONLY|false|NO|YES|NO|NO|NO|YES|NO
AI_CONCEPT_TEST_FAILURE_PLAN|compose.yaml|E2E_TEST_ONLY||NO|YES|NO|NO|NO|YES|NO
AI_CONNECT_TIMEOUT|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|5s|NO|NO|NO|NO|NO|NO|NO
AI_E2E_FAULTS_ENABLED|compose.yaml, scripts/docker-failure-e2e.ps1|E2E_TEST_ONLY|false|NO|NO|NO|NO|NO|YES|NO
AI_E2E_FAULT_DELAY_SECONDS|compose.yaml|E2E_TEST_ONLY|5|NO|NO|NO|NO|NO|YES|NO
AI_E2E_FAULT_MODE|compose.yaml, scripts/docker-failure-e2e.ps1|E2E_TEST_ONLY||NO|NO|NO|NO|NO|YES|NO
AI_FIXTURE_MODE|compose.yaml|OPTIONAL_RUNTIME|false|NO|YES|YES|NO|NO|YES|NO
AI_IMAGE_MODEL|ai/app/tasks/marketing_content/marketing_image.py|OPTIONAL_RUNTIME||NO|YES|NO|NO|NO|YES|NO
AI_INTERNAL_SERVICE_TOKEN|ai/app/api/executions.py, ai/app/progress/safe_task_progress.py, ai/app/research/market_ledger_artifact.py, ai/app/tasks/concept_portfolio_v2/progress_sender.py, ai/app/tasks/marketing_content/marketing_image.py, ai/main.py, compose.yaml|REQUIRED_RUNTIME||YES|YES|YES|NO|NO|YES|YES
AI_MAX_INPUT_CHARACTERS|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|200000|NO|NO|NO|NO|NO|NO|NO
AI_MAX_RESPONSE_BYTES|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|1048576|NO|NO|NO|NO|NO|NO|NO
AI_MAX_RETRIES|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|2|NO|NO|NO|NO|NO|NO|NO
AI_MODEL|ai/app/providers/structured.py, ai/app/research/bm/analyze.py, ai/app/twin/runner.py, ai/main.py, backend/src/main/resources/application.yaml, compose.yaml|REQUIRED_RUNTIME||YES|YES|YES|YES|NO|YES|NO
AI_MODEL_CONCEPT_VALIDATION|ai/app/legal/concept_validation.py, compose.yaml|OPTIONAL_RUNTIME||NO|YES|NO|NO|NO|YES|NO
AI_PROVIDER|ai/app/providers/structured.py, ai/app/twin/runner.py, ai/main.py, compose.yaml|REQUIRED_RUNTIME||YES|YES|YES|YES|NO|YES|NO
AI_PROVIDER_TIMEOUT_SECONDS|ai/app/providers/structured.py, compose.yaml|OPTIONAL_RUNTIME|60|NO|YES|NO|YES|NO|YES|NO
AI_READ_TIMEOUT|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|60s|NO|NO|NO|NO|NO|NO|NO
AI_SERVER_BASE_URL|backend/src/main/resources/application.yaml, scripts/ai-local-smoke.ps1|DIRECT_RUN_COMPAT|http://127.0.0.1:8000|NO|YES|NO|NO|NO|YES|NO
AI_SERVER_CONCEPT_PORTFOLIO_READ_TIMEOUT|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|15m|NO|YES|NO|NO|NO|NO|NO
AI_SERVER_CONNECT_TIMEOUT|backend/src/main/resources/application.yaml, compose.yaml|OPTIONAL_RUNTIME|3s|NO|YES|NO|NO|NO|YES|NO
AI_SERVER_INTERNAL_API_KEY|backend/src/main/resources/application.yaml|DIRECT_RUN_COMPAT||NO|YES|NO|NO|NO|YES|YES
AI_SERVER_PORT|compose.e2e.yaml, scripts/docker-e2e-smoke.ps1, scripts/docker-failure-e2e.ps1|INFRA_ONLY|8000|NO|NO|YES|NO|NO|YES|NO
AI_SERVER_READ_TIMEOUT|backend/src/main/resources/application.yaml, compose.yaml, scripts/docker-failure-e2e.ps1|OPTIONAL_RUNTIME|300s|NO|YES|NO|NO|NO|YES|NO
AI_SERVER_TWIN_SURVEY_READ_TIMEOUT|backend/src/main/resources/application.yaml, compose.yaml|OPTIONAL_RUNTIME|14m|NO|YES|NO|NO|NO|YES|NO
APP_E2E_DEFER_ARTIFACT_WAKE|compose.yaml, scripts/docker-failure-e2e.ps1|E2E_TEST_ONLY|false|NO|NO|NO|NO|NO|YES|NO
APP_ENVIRONMENT|backend/src/main/resources/application.yaml, compose.yaml|OPTIONAL_RUNTIME|LOCAL|NO|YES|YES|YES|NO|YES|NO
BACKEND_INTERNAL_BASE_URL|ai/app/progress/safe_task_progress.py, ai/app/research/market_ledger_artifact.py, ai/app/tasks/concept_portfolio_v2/progress_sender.py, ai/app/tasks/marketing_content/marketing_image.py|DIRECT_RUN_COMPAT||NO|YES|NO|NO|NO|YES|NO
BACKEND_PORT|compose.e2e.yaml, scripts/docker-e2e-smoke.ps1, scripts/docker-failure-e2e.ps1|INFRA_ONLY|8080|NO|NO|YES|NO|NO|YES|NO
BM_MODEL|ai/app/research/bm/analyze.py, ai/app/research/research2/tools/bm_rehearsal/nb_llm.py, compose.yaml|OPTIONAL_RUNTIME|${AI_MODEL|NO|YES|NO|NO|NO|YES|NO
BOOTSTRAP_ADMIN_EMAIL|backend/src/main/resources/application.yaml, compose.yaml|OPTIONAL_RUNTIME||NO|YES|NO|YES|NO|YES|NO
BOOTSTRAP_ADMIN_ENABLED|backend/src/main/resources/application.yaml, compose.yaml|OPTIONAL_RUNTIME|false|NO|YES|NO|YES|NO|YES|NO
BOOTSTRAP_ADMIN_PASSWORD|backend/src/main/resources/application.yaml, compose.yaml|OPTIONAL_RUNTIME||NO|YES|NO|YES|NO|YES|YES
BOOTSTRAP_ADMIN_USERNAME|backend/src/main/resources/application.yaml, compose.yaml|OPTIONAL_RUNTIME||NO|YES|NO|YES|NO|YES|NO
CONCEPT_PORTFOLIO_AI_DEADLINE|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|14m|NO|NO|NO|NO|NO|NO|NO
CONCEPT_PORTFOLIO_EXECUTOR_QUEUE_CAPACITY|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|4|NO|NO|NO|NO|NO|NO|NO
CONCEPT_PORTFOLIO_EXECUTOR_THREADS|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|2|NO|NO|NO|NO|NO|NO|NO
CONCEPT_PORTFOLIO_HEARTBEAT_INTERVAL|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|20s|NO|NO|NO|NO|NO|NO|NO
CONCEPT_PORTFOLIO_TASK_LEASE|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|90s|NO|NO|NO|NO|NO|NO|NO
CONCEPT_PORTFOLIO_TASK_TIMEOUT|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|20m|NO|NO|NO|NO|NO|NO|NO
CORS_ALLOWED_ORIGINS|backend/src/main/resources/application.yaml, compose.yaml|OPTIONAL_RUNTIME|http://localhost:3000|NO|YES|YES|NO|NO|YES|NO
CORS_ALLOW_CREDENTIALS|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|false|NO|NO|NO|NO|NO|NO|NO
DART_API_KEY|ai/app/research/research2/adapters/dart.py, ai/app/research/research2/tools/preflight.py, ai/app/tasks/tech_ops_external_evidence/service.py, compose.yaml|OPTIONAL_RUNTIME||NO|YES|NO|NO|NO|YES|YES
DB_CONNECTION_TIMEOUT_MS|backend/src/main/resources/application-postgres.yaml|OPTIONAL_RUNTIME|5000|NO|YES|NO|NO|NO|NO|NO
DB_PASSWORD|backend/src/main/resources/application-postgres.yaml|OPTIONAL_RUNTIME|aivle|NO|YES|NO|NO|NO|YES|YES
DB_POOL_MAX_SIZE|backend/src/main/resources/application-postgres.yaml|OPTIONAL_RUNTIME|10|NO|YES|NO|NO|NO|NO|NO
DB_POOL_MIN_IDLE|backend/src/main/resources/application-postgres.yaml|OPTIONAL_RUNTIME|2|NO|YES|NO|NO|NO|NO|NO
DB_URL|backend/src/main/resources/application-postgres.yaml|OPTIONAL_RUNTIME|jdbc:postgresql://localhost:5432/aivle|NO|YES|NO|NO|NO|YES|NO
DB_USERNAME|backend/src/main/resources/application-postgres.yaml|OPTIONAL_RUNTIME|aivle|NO|YES|NO|NO|NO|YES|NO
DB_VALIDATION_TIMEOUT_MS|backend/src/main/resources/application-postgres.yaml|OPTIONAL_RUNTIME|3000|NO|YES|NO|NO|NO|NO|NO
DEV|frontEnd/src/features/market/MarketResearchPage.jsx, frontEnd/src/features/twin-survey/TwinSurveyPage.jsx|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|NO|NO
DOCUMENT_JOB_EXECUTION_TIMEOUT|scripts/docker-failure-e2e.ps1|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|NO|NO
DOCUMENT_JOB_POLL_INTERVAL|scripts/docker-failure-e2e.ps1|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|NO|NO
DOCUMENT_JOB_RECOVERY_INTERVAL|scripts/docker-failure-e2e.ps1|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|NO|NO
DOCUMENT_JOB_RUNNER_ENABLED|scripts/ai-local-smoke.ps1, scripts/docker-failure-e2e.ps1|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|NO|NO
DOCUMENT_JOB_STALE_TIMEOUT|scripts/docker-failure-e2e.ps1|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|NO|NO
EVIDENCE_ARTIFACT_MAX_SIZE|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|20MB|NO|NO|NO|NO|NO|NO|NO
FILE_STORAGE_ROOT|backend/src/main/resources/application.yaml, scripts/ai-local-smoke.ps1|OPTIONAL_RUNTIME|./data/files|NO|NO|NO|NO|NO|NO|NO
FRONTEND_PORT|compose.e2e.yaml, compose.yaml, scripts/docker-e2e-smoke.ps1, scripts/docker-failure-e2e.ps1|INFRA_ONLY|3000|NO|YES|YES|NO|NO|YES|NO
JWT_ACCESS_TOKEN_TTL|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|15m|NO|YES|NO|NO|NO|NO|NO
JWT_CLOCK_SKEW|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|30s|NO|YES|NO|NO|NO|NO|NO
JWT_ISSUER|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|aivle-business-validation|NO|YES|NO|NO|NO|NO|NO
JWT_REFRESH_TOKEN_TTL|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|7d|NO|YES|NO|NO|NO|NO|NO
JWT_SECRET|backend/src/main/resources/application.yaml, compose.yaml, scripts/ai-local-smoke.ps1, scripts/demo-start.ps1|REQUIRED_RUNTIME||YES|YES|YES|YES|NO|YES|YES
KOSIS_API_KEY|ai/app/research/research2/adapters/kosis.py, ai/app/research/research2/tools/kosis_probe.py, ai/app/research/research2/tools/kosis_probe_series.py, ai/app/research/research2/tools/preflight.py, ai/app/research/research2/tools/slot_dryrun.py, ai/app/tasks/tech_ops_external_evidence/service.py, compose.yaml|OPTIONAL_RUNTIME||NO|YES|NO|NO|NO|YES|YES
LEGAL_PROVIDER_TIMEOUT_SECONDS|ai/app/legal/moleg.py, compose.yaml|OPTIONAL_RUNTIME|30|NO|YES|NO|NO|NO|YES|NO
LEGAL_REGISTRY_VERSION|ai/app/legal/registry.py, ai/app/tools/concept_factory_provider_smoke.py, compose.yaml|OPTIONAL_RUNTIME|legal-registry-v1|NO|YES|NO|NO|NO|YES|NO
LEGAL_SOURCE_CACHE_SECONDS|ai/app/legal/moleg.py, compose.yaml|OPTIONAL_RUNTIME|3600|NO|YES|NO|NO|NO|YES|NO
MARKETING_COPY_MODEL|ai/app/services/marketing_copy_service.py, compose.yaml|OPTIONAL_RUNTIME|gpt-4o-mini|NO|YES|NO|NO|NO|YES|NO
MARKETING_IMAGE_MODEL|ai/app/services/openai_banner_service.py, compose.yaml|OPTIONAL_RUNTIME|gpt-image-2|NO|YES|NO|NO|NO|YES|NO
MARKET_MODULE_INTERNAL_API_KEY|backend/src/main/resources/application.yaml, compose.yaml|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|YES|YES
MARKET_RESEARCH_OPENAI_API_KEY|compose.yaml|OPTIONAL_RUNTIME|${OPENAI_API_KEY:-|NO|YES|NO|NO|NO|YES|YES
MINIO_API_PORT|compose.e2e.yaml, scripts/docker-e2e-smoke.ps1, scripts/docker-failure-e2e.ps1|INFRA_ONLY|9000|NO|NO|YES|NO|NO|YES|NO
MINIO_BUCKET|scripts/ai-artifact-smoke.ps1|INFRA_ONLY||NO|NO|NO|NO|NO|YES|NO
MINIO_CONSOLE_PORT|compose.e2e.yaml, scripts/docker-e2e-smoke.ps1, scripts/docker-failure-e2e.ps1|INFRA_ONLY|9001|NO|NO|YES|NO|NO|YES|NO
MINIO_ROOT_PASSWORD|compose.infrastructure.yaml, compose.yaml, scripts/ai-artifact-smoke.ps1|REQUIRED_RUNTIME||YES|YES|YES|NO|YES|YES|YES
MINIO_ROOT_USER|compose.infrastructure.yaml, compose.yaml, scripts/ai-artifact-smoke.ps1|INFRA_ONLY|aivle-e2e|NO|YES|YES|NO|YES|YES|NO
MODE|frontEnd/src/app/layouts/AdminShell.jsx|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|NO|NO
MOLEG_API_BASE_URL|ai/app/legal/moleg.py, compose.yaml|OPTIONAL_RUNTIME|https://www.law.go.kr/DRF|NO|YES|NO|NO|NO|YES|NO
MOLEG_API_KEY|ai/app/legal/moleg.py, ai/app/tools/concept_factory_provider_smoke.py, ai/main.py, compose.yaml|OPTIONAL_RUNTIME||NO|YES|NO|NO|NO|YES|YES
OBJECT_STORAGE_ACCESS_KEY|backend/src/main/resources/application.yaml, scripts/ai-local-smoke.ps1|OPTIONAL_RUNTIME||NO|YES|NO|NO|NO|YES|YES
OBJECT_STORAGE_ARTIFACT_MAX_SIZE|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|1MB|NO|YES|NO|NO|NO|NO|NO
OBJECT_STORAGE_BUCKET|backend/src/main/resources/application.yaml, compose.infrastructure.yaml, compose.yaml, scripts/ai-local-smoke.ps1|OPTIONAL_RUNTIME|aivle-ai-artifacts|NO|YES|YES|NO|YES|YES|NO
OBJECT_STORAGE_CONNECT_TIMEOUT|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|3s|NO|YES|NO|NO|NO|NO|NO
OBJECT_STORAGE_ENDPOINT|backend/src/main/resources/application.yaml, scripts/ai-local-smoke.ps1|OPTIONAL_RUNTIME|http://127.0.0.1:9000|NO|YES|NO|NO|NO|YES|NO
OBJECT_STORAGE_LOCAL_ROOT|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|./data/objects|NO|YES|NO|NO|NO|NO|NO
OBJECT_STORAGE_PATH_STYLE_ACCESS|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|true|NO|YES|NO|NO|NO|NO|NO
OBJECT_STORAGE_PRESIGNED_GET_EXPIRY|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|5m|NO|YES|NO|NO|NO|NO|NO
OBJECT_STORAGE_PRESIGNED_PUT_EXPIRY|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|5m|NO|YES|NO|NO|NO|NO|NO
OBJECT_STORAGE_PROVIDER|backend/src/main/resources/application-local.yaml, backend/src/main/resources/application.yaml, scripts/ai-local-smoke.ps1|OPTIONAL_RUNTIME|local|NO|YES|NO|NO|NO|YES|NO
OBJECT_STORAGE_PUBLIC_ENDPOINT|backend/src/main/resources/application.yaml, scripts/ai-local-smoke.ps1|OPTIONAL_RUNTIME|http://127.0.0.1:9000|NO|YES|NO|NO|NO|YES|NO
OBJECT_STORAGE_READ_TIMEOUT|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|30s|NO|YES|NO|NO|NO|NO|NO
OBJECT_STORAGE_REGION|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|us-east-1|NO|YES|NO|NO|NO|NO|NO
OBJECT_STORAGE_SECRET_KEY|backend/src/main/resources/application.yaml, scripts/ai-local-smoke.ps1|OPTIONAL_RUNTIME||NO|YES|NO|NO|NO|YES|YES
OPENAI_API_KEY|ai/app/research/bm/analyze.py, ai/app/research/research2/run.py, ai/app/research/research2/service/summary.py, ai/app/research/research2/tests/probe_notfound.py, ai/app/research/research2/tests/test_step6.py, ai/app/research/research2/tools/bm_rehearsal/nb_llm.py, ai/app/research/research2/tools/bm_rehearsal/run_full.py, ai/app/services/marketing_copy_service.py, ai/app/services/openai_banner_service.py, ai/app/tasks/marketing_content/marketing_image.py, ai/app/tasks/marketing_visual/service.py, compose.yaml|DIRECT_RUN_COMPAT||NO|YES|NO|NO|NO|YES|YES
OPENAI_BASE_URL|ai/app/research/bm/analyze.py, compose.yaml|OPTIONAL_RUNTIME|${AI_BASE_URL:-|NO|YES|NO|NO|NO|YES|NO
POSTGRES_DB|compose.infrastructure.yaml, compose.yaml|INFRA_ONLY|aivle|NO|YES|YES|NO|YES|YES|NO
POSTGRES_PASSWORD|compose.infrastructure.yaml, compose.yaml|REQUIRED_RUNTIME||YES|YES|YES|NO|YES|YES|YES
POSTGRES_PORT|compose.e2e.yaml, compose.infrastructure.yaml, scripts/docker-e2e-smoke.ps1, scripts/docker-failure-e2e.ps1|INFRA_ONLY|5432|NO|NO|YES|NO|YES|YES|NO
POSTGRES_USER|compose.infrastructure.yaml, compose.yaml|INFRA_ONLY|aivle|NO|YES|YES|NO|YES|YES|NO
RESEARCH2_GENERATED_RUNS_DIR|ai/app/research/product_runner.py, ai/app/research/research2/runpath.py|INTERNAL_FIXED||NO|NO|NO|NO|NO|NO|NO
RESEARCH2_HOME|ai/app/research/runner.py|INTERNAL_FIXED||NO|NO|NO|NO|NO|NO|NO
RESEARCH2_RUNS_DIR|ai/app/research/product_runner.py, ai/app/research/research2/runpath.py, ai/tests/test_product_integration.py|INTERNAL_FIXED||NO|NO|NO|NO|NO|NO|NO
SERVER_PORT|scripts/ai-local-smoke.ps1|INFRA_ONLY||NO|NO|NO|NO|NO|NO|NO
SPRING_DATASOURCE_URL|scripts/ai-local-smoke.ps1|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|NO|NO
SPRING_PROFILES_ACTIVE|scripts/ai-local-smoke.ps1|INTERNAL_FIXED||NO|NO|NO|NO|NO|YES|NO
STORAGE_RECONCILIATION_BATCH_SIZE|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|100|NO|NO|NO|NO|NO|NO|NO
STORAGE_RECONCILIATION_DRY_RUN|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|true|NO|NO|NO|NO|NO|NO|NO
STORAGE_RECONCILIATION_ENABLED|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|false|NO|NO|NO|NO|NO|NO|NO
STORAGE_RECONCILIATION_MINIMUM_AGE|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|1h|NO|NO|NO|NO|NO|NO|NO
STORAGE_RECONCILIATION_QUARANTINE_RETENTION|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|7d|NO|NO|NO|NO|NO|NO|NO
STORAGE_RECONCILIATION_SCHEDULE|backend/src/main/resources/application.yaml|OPTIONAL_RUNTIME|0 0 3 * * *|NO|NO|NO|NO|NO|NO|NO
TAVILY_API_KEY|ai/app/research/research2/tools/preflight.py, ai/app/tasks/finance_estimate/tavily.py, ai/app/tasks/tech_ops_external_evidence/service.py, compose.yaml|OPTIONAL_RUNTIME||NO|YES|NO|NO|NO|YES|YES
TECH_OPS_ADVISOR_MODEL|ai/app/tasks/tech_ops_advisor/service.py, compose.yaml|OPTIONAL_RUNTIME||NO|YES|NO|NO|NO|YES|NO
TECH_OPS_ENABLE_DART_CORP_LOOKUP|ai/app/tasks/tech_ops_external_evidence/service.py, compose.yaml|OPTIONAL_RUNTIME|false|NO|YES|NO|NO|NO|YES|NO
TWIN_BANK_DIR|ai/app/twin/bank.py|DIRECT_RUN_COMPAT||NO|YES|NO|NO|NO|YES|NO
TWIN_BANK_HOST_DIR|compose.yaml|OPTIONAL_RUNTIME||NO|YES|YES|NO|NO|YES|NO
TWIN_CONCURRENCY|compose.yaml|OPTIONAL_RUNTIME|32|NO|YES|YES|NO|NO|YES|NO
VITE_API_BASE_URL|scripts/demo-start.ps1|OPTIONAL_RUNTIME||NO|NO|NO|YES|NO|NO|NO
VITE_APP_ENVIRONMENT|frontEnd/src/app/layouts/AdminShell.jsx|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|NO|NO
VITE_ENABLE_PIPELINE_DEBUG|compose.yaml, frontEnd/Dockerfile, frontEnd/src/features/concept-factory/components/ConceptTimeline.jsx|OPTIONAL_RUNTIME|false|NO|YES|NO|NO|NO|YES|NO
VITE_MARKET_FIXTURE_MODE|frontEnd/src/features/market/MarketResearchPage.jsx|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|NO|NO
VITE_TWIN_FIXTURE_MODE|frontEnd/src/features/twin-survey/TwinSurveyPage.jsx|OPTIONAL_RUNTIME||NO|NO|NO|NO|NO|NO|NO

UNDECLARED_REQUIRED=0
UNPASSED_REQUIRED=0
UNKNOWN_ENV_USAGE=0
UNDOCUMENTED_DIRECT_RUN=0
NONEMPTY_PLACEHOLDER=0
