package com.aida.adapter;

import java.util.List;

import com.aida.contract.Issue;
import com.aida.contract.Release;

/** Reads issues and releases from Jira (the mock in the MVP). FROZEN. */
public interface JiraSource {

    /**
     * @param release fixVersion name, or null for every issue in the project
     */
    List<Issue> issues(String project, String release);

    List<Release> releases(String project);
}
