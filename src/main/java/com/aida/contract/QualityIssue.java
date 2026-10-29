package com.aida.contract;
import java.util.List;

public record QualityIssue(
    String type,
    int count,
    List<String> keys,
    String detail
) {}
