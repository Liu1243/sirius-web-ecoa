package org.eclipse.sirius.web.auth;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.sirius.web.permissions.UserSummaryResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository used to manage application users.
 *
 * @author Codex
 */
@Repository
public class UserAccountRepository implements IUserRepository {

    private static final String ID = "id";

    private static final String USERNAME = "username";

    private static final RowMapper<AppUser> APP_USER_ROW_MAPPER = UserAccountRepository::mapAppUser;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserAccountRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AppUser> findByUsername(String username) {
        String sql = """
                SELECT id::text, username, display_name, password_hash, is_admin, active
                FROM app_user
                WHERE username = :username
                """;
        return this.jdbcTemplate.query(sql, Map.of(USERNAME, username), APP_USER_ROW_MAPPER).stream().findFirst();
    }

    public Optional<AppUser> findById(String userId) {
        String sql = """
                SELECT id::text, username, display_name, password_hash, is_admin, active
                FROM app_user
                WHERE id = CAST(:userId AS UUID)
                """;
        return this.jdbcTemplate.query(sql, Map.of("userId", userId), APP_USER_ROW_MAPPER).stream().findFirst();
    }

    public List<UserSummaryResponse> findAllActiveUsers() {
        String sql = """
                SELECT id::text AS id, username, display_name, is_admin, active
                FROM app_user
                WHERE active = true
                ORDER BY lower(display_name), lower(username)
                """;
        return this.jdbcTemplate.query(sql, (rs, rowNum) -> new UserSummaryResponse(
                rs.getString(ID),
                rs.getString(USERNAME),
                rs.getString("display_name"),
                rs.getBoolean("is_admin"),
                rs.getBoolean("active")));
    }

    public List<UserSummaryResponse> findAllUsers() {
        String sql = """
                SELECT id::text AS id, username, display_name, is_admin, active
                FROM app_user
                ORDER BY active DESC, lower(display_name), lower(username)
                """;
        return this.jdbcTemplate.query(sql, (rs, rowNum) -> new UserSummaryResponse(
                rs.getString(ID),
                rs.getString(USERNAME),
                rs.getString("display_name"),
                rs.getBoolean("is_admin"),
                rs.getBoolean("active")));
    }

    public String createUser(String username, String displayName, String passwordHash, boolean admin, Instant now) {
        String generatedId = java.util.UUID.randomUUID().toString();
        Timestamp timestamp = Timestamp.from(now);
        String sql = """
                INSERT INTO app_user(id, username, display_name, password_hash, is_admin, active, created_on, last_modified_on)
                VALUES (CAST(:id AS UUID), :username, :displayName, :passwordHash, :admin, true, :now, :now)
                """;
        this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue(ID, generatedId)
                .addValue(USERNAME, username)
                .addValue("displayName", displayName)
                .addValue("passwordHash", passwordHash)
                .addValue("admin", admin)
                .addValue("now", timestamp));
        return generatedId;
    }

    public long countAdministrators() {
        String sql = "SELECT COUNT(*) FROM app_user WHERE is_admin = true AND active = true";
        Long result = this.jdbcTemplate.queryForObject(sql, Map.of(), Long.class);
        long count = 0;
        if (result != null) {
            count = result;
        }
        return count;
    }

    public void updateUser(String userId, String username, String displayName, boolean admin, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        String sql = """
                UPDATE app_user
                SET username = :username, display_name = :displayName, is_admin = :admin, last_modified_on = :now
                WHERE id = CAST(:userId AS UUID)
                """;
        this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue(USERNAME, username)
                .addValue("displayName", displayName)
                .addValue("admin", admin)
                .addValue("now", timestamp));
    }

    public void updatePassword(String userId, String passwordHash, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        String sql = """
                UPDATE app_user
                SET password_hash = :passwordHash, last_modified_on = :now
                WHERE id = CAST(:userId AS UUID)
                """;
        this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("passwordHash", passwordHash)
                .addValue("now", timestamp));
    }

    public void deactivateUser(String userId, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        String sql = """
                UPDATE app_user
                SET active = false, last_modified_on = :now
                WHERE id = CAST(:userId AS UUID)
                """;
        this.jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("now", timestamp));
    }

    public void deleteUserPermanently(String userId) {
        String sql = """
                DELETE FROM app_user
                WHERE id = CAST(:userId AS UUID)
                """;
        this.jdbcTemplate.update(sql, Map.of("userId", userId));
    }

    /** Returns true if any user already has the given display name (case-insensitive). */
    public boolean existsByDisplayName(String displayName) {
        String sql = """
                SELECT COUNT(*) FROM app_user
                WHERE lower(display_name) = lower(:displayName)
                """;
        Long count = this.jdbcTemplate.queryForObject(sql, Map.of("displayName", displayName), Long.class);
        return count != null && count > 0;
    }

    /** Returns true if any user OTHER THAN excludeUserId already has the given display name (case-insensitive). */
    public boolean existsByDisplayNameExcluding(String displayName, String excludeUserId) {
        String sql = """
                SELECT COUNT(*) FROM app_user
                WHERE lower(display_name) = lower(:displayName)
                  AND id != CAST(:excludeUserId AS UUID)
                """;
        Long count = this.jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("displayName", displayName)
                .addValue("excludeUserId", excludeUserId), Long.class);
        return count != null && count > 0;
    }

    private static AppUser mapAppUser(ResultSet resultSet, int rowNum) throws SQLException {
        return new AppUser(
                resultSet.getString(ID),
                resultSet.getString(USERNAME),
                resultSet.getString("display_name"),
                resultSet.getString("password_hash"),
                resultSet.getBoolean("is_admin"),
                resultSet.getBoolean("active"));
    }
}
