package com.aida.contract;
import java.time.LocalDate;
import java.util.List;

public record Issue(
    String key,
    String project,
    String type,
    String summary,
    String status,
    List<StatusChange> statusHistory,
    LocalDate created,
    LocalDate resolved,
    Double estimate,
    String epic,
    String epicName,
    String fixVersion,
    String assignee
) {}
