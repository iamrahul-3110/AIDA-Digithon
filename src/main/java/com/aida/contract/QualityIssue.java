package com.aida.contract;

import java.util.List;

/**
 * @param type stale_status, missing_estimate or bulk_close
 */
public record QualityIssue(String type, int count, List<String> keys, String detail) {
}
