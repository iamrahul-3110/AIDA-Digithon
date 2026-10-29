package com.aida.engine;

import com.aida.contract.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SnapshotTest {

    @Test void issueResolvedAfterD_isUnresolvedAtD() {
        var created = LocalDate.of(2026, 9, 1);
        var resolved = LocalDate.of(2026, 10, 15);
        var asOf = LocalDate.of(2026, 10, 10);

        var issue = new Issue("SHOP-1", "SHOP", "Story", "Test", "Done",
            List.of(
                new StatusChange("To Do", created.atStartOfDay().toInstant(ZoneOffset.UTC)),
                new StatusChange("In Progress", created.plusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC)),
                new StatusChange("Done", resolved.atStartOfDay().toInstant(ZoneOffset.UTC))
            ), created, resolved, 3.0, null, null, "3.2", "Arjun");

        var snap = Snapshots.snapshot(List.of(issue), asOf);
        assertThat(snap).hasSize(1);
        assertThat(snap.get(0).resolved()).isNull();
        assertThat(snap.get(0).status()).isEqualTo("In Progress");
    }

    @Test void issueCreatedAfterD_notIncluded() {
        var created = LocalDate.of(2026, 11, 1);
        var asOf = LocalDate.of(2026, 10, 15);
        var issue = new Issue("SHOP-2", "SHOP", "Story", "Future", "To Do",
            List.of(new StatusChange("To Do", created.atStartOfDay().toInstant(ZoneOffset.UTC))),
            created, null, null, null, null, "3.2", "Arjun");
        var snap = Snapshots.snapshot(List.of(issue), asOf);
        assertThat(snap).isEmpty();
    }
}
