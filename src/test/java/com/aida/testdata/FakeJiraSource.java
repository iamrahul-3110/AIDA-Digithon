package com.aida.testdata;

import java.util.List;

import com.aida.adapter.JiraSource;
import com.aida.contract.Issue;
import com.aida.contract.Release;

/** In-memory JiraSource over a fixed list of issues and releases. */
public class FakeJiraSource implements JiraSource {

    private final List<Issue> issues;
    private final List<Release> releases;

    public FakeJiraSource(List<Issue> issues, List<Release> releases) {
        this.issues = List.copyOf(issues);
        this.releases = List.copyOf(releases);
    }

    @Override
    public List<Issue> issues(String project, String release) {
        return issues.stream()
                .filter(i -> project.equals(i.project()))
                .filter(i -> release == null || release.equals(i.fixVersion()))
                .toList();
    }

    @Override
    public List<Release> releases(String project) {
        return releases.stream().filter(r -> project.equals(r.project())).toList();
    }
}
