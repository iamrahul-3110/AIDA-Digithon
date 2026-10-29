package com.aida.contract;
import java.util.List;

public record Driver(
    String type,
    double impactDays,
    String detail,
    List<String> keys
) {}
