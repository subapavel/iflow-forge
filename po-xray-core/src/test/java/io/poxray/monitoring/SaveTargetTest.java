package io.poxray.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SaveTarget.check: inside a git work tree only local/** and testdata/** may receive saved messages (spec §7). */
class SaveTargetTest {

    private static final String REFUSAL =
            "refusing to save customer data inside the repository outside local/ or testdata/";

    @TempDir
    Path tmp;
    private Path repo;

    @BeforeEach
    void repo() throws IOException {
        repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        Files.createDirectories(repo.resolve("src").resolve("main"));
        Files.createDirectories(repo.resolve("local"));
    }

    private void assertRefused(Path dir) {
        assertThatThrownBy(() -> SaveTarget.check(dir))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining(REFUSAL);
    }

    @Test
    void outsideWorkTreeAccepted() {
        Path dir = tmp.resolve("elsewhere").resolve("out");

        assertThat(SaveTarget.check(dir)).isEqualTo(dir.toAbsolutePath().normalize());
    }

    @Test
    void localAccepted() {
        Path dir = repo.resolve("local").resolve("customer").resolve("messages"); // does not exist yet

        assertThat(SaveTarget.check(dir)).isEqualTo(dir.toAbsolutePath().normalize());
    }

    @Test
    void testdataAccepted() {
        Path dir = repo.resolve("testdata").resolve("messages");

        assertThat(SaveTarget.check(dir)).isEqualTo(dir.toAbsolutePath().normalize());
    }

    @Test
    void repoRootRefused() {
        assertRefused(repo);
        assertThatThrownBy(() -> SaveTarget.check(repo)).hasMessageContaining(repo.getFileName().toString());
    }

    @Test
    void srcRefused() {
        assertRefused(repo.resolve("src").resolve("main"));
        assertRefused(repo.resolve("src").resolve("main").resolve("new-dir"));
    }

    @Test
    void dotDotEscapeRefused() {
        assertRefused(repo.resolve("local").resolve("..").resolve("src"));
    }

    @Test
    void lookalikeOfLocalRefused() {
        assertRefused(repo.resolve("localx").resolve("out"));
        assertRefused(repo.resolve("docs").resolve("local"));
    }

    @Test
    void gitFileMarksAWorkTreeToo() throws IOException {
        Path worktree = tmp.resolve("worktree");
        Files.createDirectories(worktree.resolve("src"));
        Files.writeString(worktree.resolve(".git"), "gitdir: ../repo/.git/worktrees/wt\n");

        assertRefused(worktree.resolve("src"));
        assertThat(SaveTarget.check(worktree.resolve("local")))
                .isEqualTo(worktree.resolve("local").toAbsolutePath().normalize());
    }
}
