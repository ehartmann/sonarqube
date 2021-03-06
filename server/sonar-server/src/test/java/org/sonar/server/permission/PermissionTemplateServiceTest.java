/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.permission;

import java.util.List;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.MapSettings;
import org.sonar.api.config.Settings;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.utils.internal.AlwaysIncreasingSystem2;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.permission.template.PermissionTemplateDbTester;
import org.sonar.db.permission.template.PermissionTemplateDto;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.permission.index.PermissionIndexer;
import org.sonar.server.permission.ws.template.DefaultTemplatesResolverRule;
import org.sonar.server.tester.UserSessionRule;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.sonar.core.permission.GlobalPermissions.SCAN_EXECUTION;

public class PermissionTemplateServiceTest {

  @Rule
  public ExpectedException throwable = ExpectedException.none();
  @Rule
  public DbTester dbTester = DbTester.create(new AlwaysIncreasingSystem2());
  @Rule
  public DefaultTemplatesResolverRule defaultTemplatesResolver = DefaultTemplatesResolverRule.withGovernance();

  private UserSessionRule userSession = UserSessionRule.standalone();
  private PermissionTemplateDbTester templateDb = dbTester.permissionTemplates();
  private DbSession session = dbTester.getSession();
  private Settings settings = new MapSettings();
  private PermissionIndexer permissionIndexer = mock(PermissionIndexer.class);
  private PermissionTemplateService underTest = new PermissionTemplateService(dbTester.getDbClient(), permissionIndexer, userSession, defaultTemplatesResolver);

  @Test
  public void apply_permission_template() {
    OrganizationDto organization = dbTester.organizations().insert();
    ComponentDto project = dbTester.components().insertProject(organization);
    GroupDto adminGroup = dbTester.users().insertGroup(organization);
    GroupDto userGroup = dbTester.users().insertGroup(organization);
    UserDto user = dbTester.users().insertUser();
    dbTester.users().insertPermissionOnGroup(adminGroup, "admin");
    dbTester.users().insertPermissionOnGroup(userGroup, "user");
    dbTester.users().insertPermissionOnUser(organization, user, "admin");
    PermissionTemplateDto permissionTemplate = dbTester.permissionTemplates().insertTemplate(organization);
    dbTester.permissionTemplates().addGroupToTemplate(permissionTemplate, adminGroup, "admin");
    dbTester.permissionTemplates().addGroupToTemplate(permissionTemplate, adminGroup, "issueadmin");
    dbTester.permissionTemplates().addGroupToTemplate(permissionTemplate, userGroup, "user");
    dbTester.permissionTemplates().addGroupToTemplate(permissionTemplate, userGroup, "codeviewer");
    dbTester.permissionTemplates().addAnyoneToTemplate(permissionTemplate, "user");
    dbTester.permissionTemplates().addAnyoneToTemplate(permissionTemplate, "codeviewer");
    dbTester.permissionTemplates().addUserToTemplate(permissionTemplate, user, "admin");

    assertThat(selectProjectPermissionsOfGroup(organization, adminGroup, project)).isEmpty();
    assertThat(selectProjectPermissionsOfGroup(organization, userGroup, project)).isEmpty();
    assertThat(selectProjectPermissionsOfGroup(organization, null, project)).isEmpty();
    assertThat(selectProjectPermissionsOfUser(user, project)).isEmpty();

    underTest.apply(session, permissionTemplate, singletonList(project));

    assertThat(selectProjectPermissionsOfGroup(organization, adminGroup, project)).containsOnly("admin", "issueadmin");
    assertThat(selectProjectPermissionsOfGroup(organization, userGroup, project)).containsOnly("user", "codeviewer");
    assertThat(selectProjectPermissionsOfGroup(organization, null, project)).containsOnly("user", "codeviewer");
    assertThat(selectProjectPermissionsOfUser(user, project)).containsOnly("admin");

    checkAuthorizationUpdatedAtIsUpdated(project);
  }

  private List<String> selectProjectPermissionsOfGroup(OrganizationDto organizationDto, @Nullable GroupDto groupDto, ComponentDto project) {
    return dbTester.getDbClient().groupPermissionDao().selectProjectPermissionsOfGroup(session,
      organizationDto.getUuid(), groupDto != null ? groupDto.getId() : null, project.getId());
  }

  private List<String> selectProjectPermissionsOfUser(UserDto userDto, ComponentDto project) {
    return dbTester.getDbClient().userPermissionDao().selectProjectPermissionsOfUser(session,
      userDto.getId(), project.getId());
  }

  @Test
  public void would_user_have_permission_with_default_permission_template() {
    OrganizationDto organization = dbTester.organizations().insert();
    UserDto user = dbTester.users().insertUser();
    GroupDto group = dbTester.users().insertGroup(organization);
    dbTester.users().insertMember(group, user);
    PermissionTemplateDto template = templateDb.insertTemplate(organization);
    dbTester.organizations().setDefaultTemplates(organization, template.getUuid(), null);
    templateDb.addProjectCreatorToTemplate(template.getId(), SCAN_EXECUTION);
    templateDb.addUserToTemplate(template.getId(), user.getId(), UserRole.USER);
    templateDb.addGroupToTemplate(template.getId(), group.getId(), UserRole.CODEVIEWER);
    templateDb.addGroupToTemplate(template.getId(), null, UserRole.ISSUE_ADMIN);

    // authenticated user
    checkWouldUserHavePermission(organization, user.getId(), UserRole.ADMIN, false);
    checkWouldUserHavePermission(organization, user.getId(), SCAN_EXECUTION, true);
    checkWouldUserHavePermission(organization, user.getId(), UserRole.USER, true);
    checkWouldUserHavePermission(organization, user.getId(), UserRole.CODEVIEWER, true);
    checkWouldUserHavePermission(organization, user.getId(), UserRole.ISSUE_ADMIN, true);

    // anonymous user
    checkWouldUserHavePermission(organization, null, UserRole.ADMIN, false);
    checkWouldUserHavePermission(organization, null, SCAN_EXECUTION, false);
    checkWouldUserHavePermission(organization, null, UserRole.USER, false);
    checkWouldUserHavePermission(organization, null, UserRole.CODEVIEWER, false);
    checkWouldUserHavePermission(organization, null, UserRole.ISSUE_ADMIN, true);
  }

  @Test
  public void would_user_have_permission_with_unknown_default_permission_template() {
    dbTester.organizations().setDefaultTemplates(dbTester.getDefaultOrganization(), "UNKNOWN_TEMPLATE_UUID", null);

    checkWouldUserHavePermission(dbTester.getDefaultOrganization(), null, UserRole.ADMIN, false);
  }

  @Test
  public void would_user_have_permission_with_empty_template() {
    PermissionTemplateDto template = templateDb.insertTemplate(dbTester.getDefaultOrganization());
    dbTester.organizations().setDefaultTemplates(dbTester.getDefaultOrganization(), template.getUuid(), null);

    checkWouldUserHavePermission(dbTester.getDefaultOrganization(), null, UserRole.ADMIN, false);
  }

  private void checkWouldUserHavePermission(OrganizationDto organization, @Nullable Long userId, String permission, boolean expectedResult) {
    assertThat(underTest.wouldUserHavePermissionWithDefaultTemplate(session, organization.getUuid(), userId, permission, null, "PROJECT_KEY", Qualifiers.PROJECT))
      .isEqualTo(expectedResult);
  }

  private void checkAuthorizationUpdatedAtIsUpdated(ComponentDto project) {
    assertThat(dbTester.getDbClient().componentDao().selectOrFailById(session, project.getId()).getAuthorizationUpdatedAt())
      .isNotNull();
  }

}
