package com.aida.adapter;

import com.aida.contract.Issue;
import com.aida.contract.Release;

import java.util.List;

public interface JiraSource {
    List<Issue> issues(String project, String release);
    List<Release> releases(String project);
}
