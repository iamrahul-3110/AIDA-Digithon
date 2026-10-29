package com.aida.contract;

public record Risk(
    String key,
    String summary,
    String reason,
    String side,
    int days,
    String assignee
) {}
