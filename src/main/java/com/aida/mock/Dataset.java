package com.aida.mock;

import java.util.List;
import java.util.Map;

import com.aida.contract.Absence;
import com.aida.contract.Issue;
import com.aida.contract.Person;
import com.aida.contract.Release;

/**
 * The generated demo data behind the mock endpoints.
 *
 * @param versions releases of every project
 * @param team     members per project key
 * @param absences leaves and public holidays for everyone
 */
public record Dataset(List<Issue> issues, List<Release> versions, Map<String, List<Person>> team,
        List<Absence> absences) {
}
