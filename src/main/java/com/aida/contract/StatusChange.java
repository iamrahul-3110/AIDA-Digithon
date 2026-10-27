package com.aida.contract;

import java.time.Instant;

public record StatusChange(String status, Instant at) {
}
