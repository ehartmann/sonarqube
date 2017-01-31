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
package org.sonar.server.measure.custom.ws;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.RowNotFoundException;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentTesting;
import org.sonar.db.measure.custom.CustomMeasureDto;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.db.measure.custom.CustomMeasureTesting.newCustomMeasureDto;
import static org.sonar.server.measure.custom.ws.DeleteAction.PARAM_ID;


public class DeleteActionTest {

  public static final String ACTION = "delete";

  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.standalone();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  DbClient dbClient = db.getDbClient();
  final DbSession dbSession = db.getSession();

  WsTester ws;

  @Before
  public void setUp() {
    ws = new WsTester(new CustomMeasuresWs(new DeleteAction(dbClient, userSessionRule)));
    userSessionRule.setGlobalPermissions(GlobalPermissions.SYSTEM_ADMIN);
  }

  @Test
  public void delete_in_db() throws Exception {
    long id = insertCustomMeasure(newCustomMeasureDto());
    long anotherId = insertCustomMeasure(newCustomMeasureDto());

    WsTester.Result response = newRequest().setParam(PARAM_ID, String.valueOf(id)).execute();

    assertThat(dbClient.customMeasureDao().selectById(dbSession, id)).isNull();
    assertThat(dbClient.customMeasureDao().selectById(dbSession, anotherId)).isNotNull();
    response.assertNoContent();
  }

  @Test
  public void delete_in_db_when_admin_on_project() throws Exception {
    ComponentDto project = ComponentTesting.newProjectDto(db.getDefaultOrganization(), "project-uuid");
    dbClient.componentDao().insert(dbSession, project);
    userSessionRule.log_in("login").addProjectUuidPermissions(UserRole.ADMIN, "project-uuid");
    long id = insertCustomMeasure(newCustomMeasureDto().setComponentUuid("project-uuid"));

    newRequest().setParam(PARAM_ID, String.valueOf(id)).execute();

    assertThat(dbClient.customMeasureDao().selectById(dbSession, id)).isNull();
  }

  @Test
  public void fail_when_not_found_in_db() throws Exception {
    expectedException.expect(RowNotFoundException.class);

    newRequest().setParam(PARAM_ID, "42").execute();
  }

  @Test
  public void fail_when_insufficient_permissions() throws Exception {
    expectedException.expect(ForbiddenException.class);
    userSessionRule.log_in("login");
    ComponentDto project = ComponentTesting.newProjectDto(db.organizations().insert(), "any-uuid");
    dbClient.componentDao().insert(dbSession, project);
    long id = insertCustomMeasure(newCustomMeasureDto().setComponentUuid("any-uuid"));

    newRequest().setParam(PARAM_ID, String.valueOf(id)).execute();
  }

  private long insertCustomMeasure(CustomMeasureDto customMeasure) {
    dbClient.customMeasureDao().insert(dbSession, customMeasure);
    dbSession.commit();
    return customMeasure.getId();
  }

  private WsTester.TestRequest newRequest() {
    return ws.newPostRequest(CustomMeasuresWs.ENDPOINT, ACTION);
  }
}
