package com.aida.contract;

import java.time.LocalDate;

public record Release(String project, String name, LocalDate releaseDate, boolean released) {
}
