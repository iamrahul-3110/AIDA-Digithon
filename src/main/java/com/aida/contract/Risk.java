package com.aida.contract;

/**
 * @param reason aging, client_wait or stale
 * @param side   team or client
 * @param days   days in the current status
 */
public record Risk(String key, String summary, String reason, String side, int days, String assignee) {
}
