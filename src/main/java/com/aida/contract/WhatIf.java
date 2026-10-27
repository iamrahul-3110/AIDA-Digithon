package com.aida.contract;

import java.time.LocalDate;
import java.util.List;

public record WhatIf(List<String> dropEpics, List<Person> addPeople, List<String> removePeople, LocalDate targetDate) {
}
