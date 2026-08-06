package com.aivle.backend.taskrun.domain;

public enum TaskAttemptState { CREATED, CLAIMED, RUNNING, SUCCEEDED, NEEDS_INPUT, FAILED, TIMED_OUT, CANCELLED }
