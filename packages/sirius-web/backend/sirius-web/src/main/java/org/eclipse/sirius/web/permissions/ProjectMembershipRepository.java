package org.eclipse.sirius.web.permissions;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository used to manage project memberships.
 *
 * @author Codex
 */
@Repository
public class ProjectMembershipRepository {

    private static final String PROJECT_ID = "projectId";

    private static final String USER_ID = "userId";

    private static final String ROLE = "role";

    private static final RowMapper<ProjectMembershipResponse> MEMBERSHIP_ROW_MAPPER = ProjectMembershipRepository::mapMembership;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProjectMembershipRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ProjectRole> findRole(String projectId, String userId) {
        String sql = """
                SELECT role
                FROM project_membership
                WHERE project_id = :projectId AND user_id = CAST(:userId AS UUID)
                """;
        return this.jdbcTemplate.query(sql, Map.of(PROJECT_ID, projectId, USER_ID, userId), (rs, rowNum) -> ProjectRole.valueOf(rs.getString(ROLE))).stream().findFirst();
    }

    public List<ProjectMembershipResponse> findMemberships(String projectId) {
        String sql = """
                SELECT u.id::text AS user_id, u.username, u.display_name, u.is_admin, pm.role
                FROM project_membership pm
                JOIN app_user u ON u.id = pm.user_id
                WHERE pm.project_id = :projectId AND u.active = true
                ORDER BY CASE pm.role WHEN 'OWNER' THEN 0 WHEN 'EDITOR' THEN 1 ELSE 2 END, lower(u.display_name), lower(u.username)
                """;
        return this.jdbcTemplate.query(sql, Map.of(PROJECT_ID, projectId), MEMBERSHIP_ROW_MAPPER);
    }

    public boolean projectExists(String projectId) {
        String sql = "SELECT COUNT(*) FROM project WHERE id = :projectId";
        Long count = this.jdbcTemplate.queryForObject(sql, Map.of(PROJECT_ID, projectId), Long.class);
        return count != null && count > 0;
    }

    public void upsertMembership(String projectId, String userId, ProjectRole role, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        String sql = """
                INSERT INTO project_membership(project_id, user_id, role, created_on, last_modified_on)
                VALUES (:projectId, CAST(:userId AS UUID), :role, :now, :now)
                ON CONFLICT (project_id, user_id)
                DO UPDATE SET role = EXCLUDED.role, last_modified_on = EXCLUDED.last_modified_on
                """;
        this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue(PROJECT_ID, projectId)
                .addValue(USER_ID, userId)
                .addValue(ROLE, role.name())
                .addValue("now", timestamp));
    }

    public void deleteMembership(String projectId, String userId) {
        String sql = """
                DELETE FROM project_membership
                WHERE project_id = :projectId AND user_id = CAST(:userId AS UUID)
                """;
        this.jdbcTemplate.update(sql, Map.of(PROJECT_ID, projectId, USER_ID, userId));
    }

    private static ProjectMembershipResponse mapMembership(ResultSet resultSet, int rowNum) throws SQLException {
        return new ProjectMembershipResponse(
                resultSet.getString("user_id"),
                resultSet.getString("username"),
                resultSet.getString("display_name"),
                resultSet.getBoolean("is_admin"),
                ProjectRole.valueOf(resultSet.getString(ROLE)));
    }
}
