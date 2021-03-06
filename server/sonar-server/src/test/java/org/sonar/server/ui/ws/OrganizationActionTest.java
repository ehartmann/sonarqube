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
package org.sonar.server.ui.ws;

import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.organization.DefaultOrganizationProvider;
import org.sonar.server.organization.TestDefaultOrganizationProvider;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.TestResponse;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.test.JsonAssert.assertJson;

public class OrganizationActionTest {
  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private DbClient dbClient = dbTester.getDbClient();
  private DefaultOrganizationProvider defaultOrganizationProvider = TestDefaultOrganizationProvider.from(dbTester);

  private WsActionTester underTest = new WsActionTester(new OrganizationAction(dbClient, defaultOrganizationProvider, userSession));

  @Test
  public void verify_definition() {
    WebService.Action def = underTest.getDef();

    assertThat(def.isInternal()).isTrue();
    assertThat(def.description()).isEqualTo("Get information concerning organization navigation for the current user");
    assertThat(def.since()).isEqualTo("6.3");

    assertThat(def.params()).hasSize(1);
    WebService.Param organization = def.param("organization");
    assertThat(organization.description()).isEqualTo("the organization key");
    assertThat(organization.isRequired()).isTrue();
    assertThat(organization.exampleValue()).isEqualTo("my-org");
  }

  @Test
  public void fails_with_IAE_if_parameter_organization_is_not_specified() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The 'organization' parameter is missing");

    executeRequest(null);
  }

  @Test
  public void verify_example() {

    OrganizationDto defaultOrganization = dbTester.getDefaultOrganization();
    userSession.login().addOrganizationPermission(defaultOrganization.getUuid(), "admin");

    TestResponse response = executeRequest(defaultOrganization);

    assertJson(response.getInput())
      .isSimilarTo(underTest.getDef().responseExampleAsString());
  }

  @Test
  public void returns_non_admin_and_default_true_when_user_not_logged_in_and_key_is_the_default_organization() {
    TestResponse response = executeRequest(dbTester.getDefaultOrganization());

    verifyResponse(response, true, false);
  }

  @Test
  public void returns_non_admin_and_default_true_when_user_logged_in_but_not_admin_and_key_is_the_default_organization() {
    userSession.login();

    TestResponse response = executeRequest(dbTester.getDefaultOrganization());

    verifyResponse(response, true, false);
  }

  @Test
  public void returns_admin_and_default_true_when_user_logged_in_and_admin_and_key_is_the_default_organization() {
    OrganizationDto defaultOrganization = dbTester.getDefaultOrganization();
    userSession.login().addOrganizationPermission(defaultOrganization.getUuid(), "admin");

    TestResponse response = executeRequest(defaultOrganization);

    verifyResponse(response, true, true);
  }

  @Test
  public void returns_non_admin_and_default_true_when_user_not_logged_in_and_key_is_not_the_default_organization() {
    OrganizationDto organization = dbTester.organizations().insert();
    TestResponse response = executeRequest(organization);

    verifyResponse(response, false, false);
  }

  @Test
  public void returns_non_admin_and_default_false_when_user_not_logged_in_and_key_is_not_the_default_organization() {
    OrganizationDto organization = dbTester.organizations().insert();

    TestResponse response = executeRequest(organization);

    verifyResponse(response, false, false);
  }

  @Test
  public void returns_non_admin_and_default_false_when_user_logged_in_but_not_admin_and_key_is_not_the_default_organization() {
    OrganizationDto organization = dbTester.organizations().insert();
    userSession.login();

    TestResponse response = executeRequest(organization);

    verifyResponse(response, false, false);
  }

  @Test
  public void returns_admin_and_default_false_when_user_logged_in_and_admin_and_key_is_not_the_default_organization() {
    OrganizationDto organization = dbTester.organizations().insert();
    userSession.login().addOrganizationPermission(organization.getUuid(), "admin");

    TestResponse response = executeRequest(organization);

    verifyResponse(response, false, true);
  }

  private TestResponse executeRequest(@Nullable OrganizationDto organization) {
    TestRequest request = underTest.newRequest();
    if (organization != null) {
      request.setParam("organization", organization.getKey());
    }
    return request.execute();
  }

  private static void verifyResponse(TestResponse response, boolean isDefault, boolean canAdmin) {
    assertJson(response.getInput())
      .isSimilarTo("{" +
        "  \"organization\": {" +
        "    \"isDefault\": " + isDefault + "," +
        "    \"canAdmin\": " + canAdmin +
        "  }" +
        "}");
  }
}
