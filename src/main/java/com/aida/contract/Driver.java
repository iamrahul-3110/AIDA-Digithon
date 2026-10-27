package com.aida.contract;

import java.util.List;

/**
 * One cause of a forecast date shift.
 *
 * @param type scope_added, aging, client_wait, leave or throughput_drop
 */
public record Driver(String type, double impactDays, String detail, List<String> keys) {
}
