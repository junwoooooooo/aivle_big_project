package com.aivle.backend.postgres;

import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("postgres")
@SpringBootTest
@ActiveProfiles("test")
class PostgreSqlRepositoryTests extends PostgreSqlIntegrationTestSupport {
    private static final String HASH = "a".repeat(64);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private StoredFileRepository storedFileRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
            "truncate table users, stored_files restart identity cascade"
        );
    }

    @Test
    void ownerScopeAndLogicalDeleteAreAppliedOnPostgreSql() {
        User firstOwner = user("first");
        User secondOwner = user("second");
        Project firstProject = projectRepository.saveAndFlush(
            Project.create(firstOwner, "first", null, "AI")
        );
        projectRepository.saveAndFlush(
            Project.create(secondOwner, "second", null, "AI")
        );

        assertThat(projectRepository.findAllByOwnerIdAndDeletedAtIsNull(
            firstOwner.getId()
        )).extracting(Project::getId).containsExactly(firstProject.getId());

        firstProject.softDelete();
        projectRepository.saveAndFlush(firstProject);

        assertThat(projectRepository.findAllByOwnerIdAndDeletedAtIsNull(
            firstOwner.getId()
        )).isEmpty();
    }

    @Test
    void varcharSha256RoundTripsWithoutCharPadding() {
        StoredFile file = storedFileRepository.saveAndFlush(StoredFile.available(
            "test/key",
            "safe.bin",
            "stored.bin",
            "bin",
            "application/octet-stream",
            1,
            HASH
        ));

        String stored = storedFileRepository.findById(file.getId())
            .orElseThrow()
            .getChecksumSha256();

        assertThat(stored).isEqualTo(HASH).hasSize(64);
        assertThat(jdbcTemplate.queryForObject(
            "select char_length(checksum_sha256) from stored_files where id = ?",
            Integer.class,
            file.getId()
        )).isEqualTo(64);
    }

    @Test
    void staleEntityVersionIsRejected() {
        User owner = user("optimistic");
        Project stale = projectRepository.saveAndFlush(
            Project.create(owner, "original", null, "AI")
        );
        jdbcTemplate.update(
            "update projects set version = version + 1 where id = ?",
            stale.getId()
        );
        stale.updateBasicInfo("stale update", null, "AI");

        assertThatThrownBy(() -> projectRepository.saveAndFlush(stale))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private User user(String prefix) {
        return userRepository.saveAndFlush(User.create(
            prefix + "-" + UUID.randomUUID() + "@example.com",
            "hashed",
            prefix
        ));
    }
}
