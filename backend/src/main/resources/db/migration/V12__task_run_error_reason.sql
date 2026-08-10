-- Store a safe, stable failure reason for asynchronous module screens.
ALTER TABLE task_runs ADD COLUMN last_error_reason VARCHAR(100);
