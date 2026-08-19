package org.eclipse.sirius.web.application.services.componentcode;

import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeHistoryDTO;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeVersionDTO;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class ComponentCodeVersionServiceTests {

    @Autowired
    private IComponentCodeVersionService versionService;

    @Test
    @DisplayName("Given valid input, when createComponentCodeVersion, then version is created")
    public void givenValidInput_whenCreateVersion_thenVersionCreated() {
        UUID projectId = UUID.randomUUID();

        ComponentCodeVersionDTO version = versionService.createComponentCodeVersion(
            projectId,
            "comp-1",
            "Component1",
            "v1.0.0",
            "public class Test {}",
            "Initial version",
            "test-user",
            null
        );

        assertThat(version).isNotNull();
        assertThat(version.versionName()).isEqualTo("v1.0.0");
        assertThat(version.componentId()).isEqualTo("comp-1");
        assertThat(version.componentName()).isEqualTo("Component1");
        assertThat(version.author()).isEqualTo("test-user");
    }

    @Test
    @DisplayName("Given duplicate version name, when createComponentCodeVersion, then throw exception")
    public void givenDuplicateVersionName_whenCreateVersion_thenThrowException() {
        UUID projectId = UUID.randomUUID();

        versionService.createComponentCodeVersion(
            projectId,
            "comp-1",
            "Component1",
            "v1.0.0",
            "public class Test {}",
            "Initial version",
            "test-user",
            null
        );

        assertThatThrownBy(() -> versionService.createComponentCodeVersion(
            projectId,
            "comp-1",
            "Component1",
            "v1.0.0",
            "public class Test2 {}",
            "Duplicate version",
            "test-user",
            null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Version name already exists");
    }

    @Test
    @DisplayName("Given existing versions, when getComponentCodeHistory, then return grouped history")
    public void givenExistingVersions_whenGetHistory_thenReturnGroupedHistory() {
        UUID projectId = UUID.randomUUID();

        // Create versions for two components
        versionService.createComponentCodeVersion(
            projectId, "comp-1", "ComponentA", "v1.0", "code1", "msg1", "user1", null
        );
        versionService.createComponentCodeVersion(
            projectId, "comp-1", "ComponentA", "v2.0", "code2", "msg2", "user1", null
        );
        versionService.createComponentCodeVersion(
            projectId, "comp-2", "ComponentB", "v1.0", "code3", "msg3", "user1", null
        );

        ComponentCodeHistoryDTO history = versionService.getComponentCodeHistory(projectId);

        assertThat(history).isNotNull();
        assertThat(history.components()).hasSize(2);

        // ComponentA should have 2 versions
        assertThat(history.components().get(0).versions()).hasSize(2);

        // ComponentB should have 1 version
        assertThat(history.components().get(1).versions()).hasSize(1);
    }

    @Test
    @DisplayName("Given existing version, when getComponentCodeVersion, then return version")
    public void givenExistingVersion_whenGetVersion_thenReturnVersion() {
        UUID projectId = UUID.randomUUID();

        ComponentCodeVersionDTO created = versionService.createComponentCodeVersion(
            projectId,
            "comp-1",
            "Component1",
            "v1.0.0",
            "public class Test {}",
            "Test version",
            "test-user",
            "model-v1"
        );

        ComponentCodeVersionDTO retrieved = versionService.getComponentCodeVersion(created.id())
            .orElse(null);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.versionName()).isEqualTo("v1.0.0");
        assertThat(retrieved.modelVersionId()).isEqualTo("model-v1");
    }

    @Test
    @DisplayName("Given non-existing version, when getComponentCodeVersion, then return empty")
    public void givenNonExistingVersion_whenGetVersion_thenReturnEmpty() {
        var result = versionService.getComponentCodeVersion(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Given existing version, when deleteComponentCodeVersion, then version is deleted")
    public void givenExistingVersion_whenDeleteVersion_thenVersionDeleted() {
        UUID projectId = UUID.randomUUID();

        ComponentCodeVersionDTO created = versionService.createComponentCodeVersion(
            projectId,
            "comp-1",
            "Component1",
            "v1.0.0",
            "public class Test {}",
            "Test version",
            "test-user",
            null
        );

        versionService.deleteComponentCodeVersion(created.id());

        var result = versionService.getComponentCodeVersion(created.id());
        assertThat(result).isEmpty();
    }
}
