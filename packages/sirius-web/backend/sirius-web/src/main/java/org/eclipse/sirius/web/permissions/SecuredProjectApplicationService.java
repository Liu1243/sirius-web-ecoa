package org.eclipse.sirius.web.permissions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.web.application.project.api.ICreateProjectInput;
import org.eclipse.sirius.web.application.project.dto.DeleteProjectInput;
import org.eclipse.sirius.web.application.project.dto.NatureDTO;
import org.eclipse.sirius.web.application.project.dto.ProjectDTO;
import org.eclipse.sirius.web.application.project.dto.ProjectSortDTO;
import org.eclipse.sirius.web.application.project.dto.ProjectSortDirection;
import org.eclipse.sirius.web.application.project.dto.ProjectSortField;
import org.eclipse.sirius.web.application.project.dto.RenameProjectInput;
import org.eclipse.sirius.web.application.project.services.ProjectApplicationService;
import org.eclipse.sirius.web.application.project.services.api.IProjectApplicationService;
import org.eclipse.sirius.web.auth.AppUser;
import org.eclipse.sirius.web.auth.CurrentUserService;
import org.eclipse.sirius.web.domain.pagination.Window;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Primary
public class SecuredProjectApplicationService implements IProjectApplicationService {

    private final ProjectApplicationService delegate;

    private final CurrentUserService currentUserService;

    private final ProjectPermissionService projectPermissionService;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SecuredProjectApplicationService(ProjectApplicationService delegate, CurrentUserService currentUserService, ProjectPermissionService projectPermissionService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.delegate = delegate;
        this.currentUserService = currentUserService;
        this.projectPermissionService = projectPermissionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ProjectDTO> findById(String id) {
        Optional<AppUser> currentUser = this.currentUserService.getCurrentUser();
        if (!this.projectPermissionService.canAccessProject(currentUser, id)) {
            return Optional.empty();
        }

        String sql = """
                SELECT p.id::text AS id, p.name, p.created_on, p.last_modified_on
                FROM project p
                WHERE p.id = :projectId
                """;
        return this.jdbcTemplate.query(sql, Map.of("projectId", id), (rs, rowNum) -> new ProjectRow(rs.getString("id"), rs.getString("name"), rs.getTimestamp("created_on").toInstant(), rs.getTimestamp("last_modified_on").toInstant()))
                .stream()
                .findFirst()
                .map(row -> new ProjectDTO(row.id(), row.name(), row.createdOn(), row.lastModifiedOn(), this.loadNatures(List.of(row.id())).getOrDefault(row.id(), List.of())));
    }

    @Override
    public Window<ProjectDTO> findAll(KeysetScrollPosition position, int limit, Map<String, Object> filter, ProjectSortDTO sort) {
        Optional<AppUser> currentUser = this.currentUserService.getCurrentUser();
        if (currentUser.isEmpty() || limit <= 0) {
            return new Window<>(List.of(), index -> position, false, false);
        }

        AppUser user = currentUser.get();
        ProjectSortDTO effectiveSort = sort == null ? ProjectSortDTO.defaultSort() : sort;
        boolean backward = position.scrollsBackward();
        String cursorId = position.getKeys().get("id") instanceof String id ? id : null;
        String cursorSortValue = position.getKeys().get("sortValue") instanceof String sortValue ? sortValue : null;
        int requestedLimit = Math.max(limit, 1);

        List<ProjectRow> rows = this.queryProjects(user, cursorId, cursorSortValue, requestedLimit + 1, filter, effectiveSort, backward);
        boolean hasMoreInQuery = rows.size() > requestedLimit;
        if (hasMoreInQuery) {
            rows = new ArrayList<>(rows.subList(0, requestedLimit));
        }

        if (backward) {
            rows = new ArrayList<>(rows);
            rows.sort(this.projectComparator(effectiveSort));
        }

        List<String> projectIds = rows.stream().map(ProjectRow::id).toList();
        Map<String, List<NatureDTO>> natures = this.loadNatures(projectIds);
        List<ProjectDTO> content = rows.stream()
                .map(row -> new ProjectDTO(row.id(), row.name(), row.createdOn(), row.lastModifiedOn(), natures.getOrDefault(row.id(), List.of())))
                .toList();

        boolean hasNext = backward
                ? this.existsAround(user, content.isEmpty() ? null : this.toRow(content.get(content.size() - 1)), filter, effectiveSort, false)
                : hasMoreInQuery;
        boolean hasPrevious = backward
                ? hasMoreInQuery
                : cursorId != null && !content.isEmpty() && this.existsAround(user, this.toRow(content.get(0)), filter, effectiveSort, true);

        return new Window<>(content, index -> position, hasNext, hasPrevious);
    }

    @Override
    public IPayload createProject(ICreateProjectInput input) {
        return this.delegate.createProject(input);
    }

    @Override
    public IPayload renameProject(RenameProjectInput input) {
        return this.delegate.renameProject(input);
    }

    @Override
    public IPayload deleteProject(DeleteProjectInput input) {
        return this.delegate.deleteProject(input);
    }

    private List<ProjectRow> queryProjects(AppUser user, String cursorId, String cursorSortValue, int limit, Map<String, Object> filter, ProjectSortDTO sort, boolean backward) {
        String direction = this.queryDirection(sort, backward);
        StringBuilder sqlBuilder = new StringBuilder("""
                SELECT p.id::text AS id, p.name, p.created_on, p.last_modified_on
                FROM project p
                """);
        if (!user.admin()) {
            sqlBuilder.append(" JOIN project_membership pm ON pm.project_id = p.id");
        }
        sqlBuilder.append(" WHERE 1 = 1");

        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("limit", limit);
        if (!user.admin()) {
            sqlBuilder.append(" AND pm.user_id = CAST(:userId AS UUID)");
            parameters.addValue("userId", user.id());
        }
        if (cursorId != null && cursorSortValue != null) {
            this.appendCursorCondition(sqlBuilder, parameters, sort, cursorId, cursorSortValue, !backward);
        } else if (cursorId != null) {
            sqlBuilder.append(" AND p.id ").append(backward ? "<" : ">").append(" :cursorId");
            parameters.addValue("cursorId", cursorId);
        }

        String contains = this.extractContains(filter);
        if (contains != null && !contains.isBlank()) {
            sqlBuilder.append(" AND lower(p.name) LIKE :contains");
            parameters.addValue("contains", "%" + contains.toLowerCase() + "%");
        }

        String sortExpression = this.sortExpression(sort.field());
        sqlBuilder.append(" ORDER BY ").append(sortExpression).append(' ').append(direction).append(", p.id ").append(direction).append(" LIMIT :limit");

        return this.jdbcTemplate.query(sqlBuilder.toString(), parameters, (rs, rowNum) -> new ProjectRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getTimestamp("created_on").toInstant(),
                rs.getTimestamp("last_modified_on").toInstant()));
    }

    private boolean existsAround(AppUser user, ProjectRow projectRow, Map<String, Object> filter, ProjectSortDTO sort, boolean before) {
        if (projectRow == null) {
            return false;
        }

        StringBuilder sqlBuilder = new StringBuilder("""
                SELECT COUNT(*)
                FROM project p
                """);
        if (!user.admin()) {
            sqlBuilder.append(" JOIN project_membership pm ON pm.project_id = p.id");
        }
        sqlBuilder.append(" WHERE 1 = 1");

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (!user.admin()) {
            sqlBuilder.append(" AND pm.user_id = CAST(:userId AS UUID)");
            parameters.addValue("userId", user.id());
        }
        this.appendCursorCondition(sqlBuilder, parameters, sort, projectRow.id(), this.cursorSortValue(projectRow, sort.field()), !before);

        String contains = this.extractContains(filter);
        if (contains != null && !contains.isBlank()) {
            sqlBuilder.append(" AND lower(p.name) LIKE :contains");
            parameters.addValue("contains", "%" + contains.toLowerCase() + "%");
        }

        Long count = this.jdbcTemplate.queryForObject(sqlBuilder.toString(), parameters, Long.class);
        return count != null && count > 0;
    }

    private Map<String, List<NatureDTO>> loadNatures(List<String> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT project_id::text AS project_id, name
                FROM nature
                WHERE project_id IN (:projectIds)
                ORDER BY name
                """;
        Map<String, List<NatureDTO>> result = new LinkedHashMap<>();
        this.jdbcTemplate.query(sql, new MapSqlParameterSource("projectIds", projectIds), rs -> {
            String projectId = rs.getString("project_id");
            result.computeIfAbsent(projectId, key -> new ArrayList<>()).add(new NatureDTO(rs.getString("name")));
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private String extractContains(Map<String, Object> filter) {
        if (filter == null) {
            return null;
        }
        Object nameFilter = filter.get("name");
        if (nameFilter instanceof Map<?, ?> nameOperations) {
            Object contains = ((Map<String, Object>) nameOperations).get("contains");
            return contains == null ? null : contains.toString();
        }
        return null;
    }

    private Comparator<ProjectRow> projectComparator(ProjectSortDTO sort) {
        Comparator<ProjectRow> comparator = switch (sort.field()) {
            case CREATED_ON -> Comparator.comparing(ProjectRow::createdOn);
            case LAST_MODIFIED_ON -> Comparator.comparing(ProjectRow::lastModifiedOn);
            case NAME -> Comparator.comparing(row -> row.name().toLowerCase());
        };
        comparator = comparator.thenComparing(ProjectRow::id);
        if (sort.direction() == ProjectSortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    private String queryDirection(ProjectSortDTO sort, boolean backward) {
        boolean descending = sort.direction() == ProjectSortDirection.DESC;
        if (backward) {
            descending = !descending;
        }
        return descending ? "DESC" : "ASC";
    }

    private void appendCursorCondition(StringBuilder sqlBuilder, MapSqlParameterSource parameters, ProjectSortDTO sort, String cursorId, String cursorSortValue, boolean movingForward) {
        String sortExpression = this.sortExpression(sort.field());
        String operator = this.comparisonOperator(sort.direction(), movingForward);
        sqlBuilder.append(" AND (")
                .append(sortExpression)
                .append(' ')
                .append(operator)
                .append(" :cursorSortValue OR (")
                .append(sortExpression)
                .append(" = :cursorSortValue AND p.id ")
                .append(operator)
                .append(" :cursorId))");
        parameters.addValue("cursorId", cursorId);
        parameters.addValue("cursorSortValue", this.toSqlSortValue(sort.field(), cursorSortValue));
    }

    private String comparisonOperator(ProjectSortDirection direction, boolean movingForward) {
        if (direction == ProjectSortDirection.ASC) {
            return movingForward ? ">" : "<";
        }
        return movingForward ? "<" : ">";
    }

    private String sortExpression(ProjectSortField field) {
        return switch (field) {
            case CREATED_ON -> "p.created_on";
            case LAST_MODIFIED_ON -> "p.last_modified_on";
            case NAME -> "lower(p.name)";
        };
    }

    private Object toSqlSortValue(ProjectSortField field, String value) {
        return switch (field) {
            case CREATED_ON, LAST_MODIFIED_ON -> Timestamp.from(Instant.parse(value));
            case NAME -> value;
        };
    }

    private String cursorSortValue(ProjectRow row, ProjectSortField field) {
        return switch (field) {
            case CREATED_ON -> row.createdOn().toString();
            case LAST_MODIFIED_ON -> row.lastModifiedOn().toString();
            case NAME -> row.name().toLowerCase();
        };
    }

    private ProjectRow toRow(ProjectDTO projectDTO) {
        return new ProjectRow(projectDTO.id(), projectDTO.name(), projectDTO.createdOn(), projectDTO.lastModifiedOn());
    }

    private record ProjectRow(String id, String name, Instant createdOn, Instant lastModifiedOn) {
    }
}
