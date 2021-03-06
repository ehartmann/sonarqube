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

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.resources.ResourceType;
import org.sonar.api.resources.ResourceTypes;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.api.web.page.Page;
import org.sonar.api.web.page.Page.Qualifier;
import org.sonar.api.web.page.PageDefinition;
import org.sonar.core.component.DefaultResourceTypes;
import org.sonar.core.platform.PluginRepository;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.property.PropertyDbTester;
import org.sonar.db.property.PropertyDto;
import org.sonar.db.qualitygate.QualityGateDto;
import org.sonar.db.user.UserDbTester;
import org.sonar.db.user.UserDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.qualitygate.QualityGateFinder;
import org.sonar.server.qualityprofile.QPMeasureData;
import org.sonar.server.qualityprofile.QualityProfile;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ui.PageRepository;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.api.measures.CoreMetrics.QUALITY_PROFILES_KEY;
import static org.sonar.api.web.page.Page.Scope.COMPONENT;
import static org.sonar.core.permission.GlobalPermissions.QUALITY_PROFILE_ADMIN;
import static org.sonar.core.permission.GlobalPermissions.SYSTEM_ADMIN;
import static org.sonar.db.component.ComponentTesting.newDirectory;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.component.ComponentTesting.newModuleDto;
import static org.sonar.db.component.ComponentTesting.newProjectDto;
import static org.sonar.db.component.SnapshotTesting.newAnalysis;
import static org.sonar.db.measure.MeasureTesting.newMeasureDto;
import static org.sonar.db.metric.MetricTesting.newMetricDto;
import static org.sonar.test.JsonAssert.assertJson;

public class ComponentActionTest {

  private static final String PROJECT_KEY = "polop";

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);
  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.standalone();

  private DbClient dbClient = dbTester.getDbClient();
  private ComponentDbTester componentDbTester = dbTester.components();
  private UserDbTester userDbTester = dbTester.users();
  private PropertyDbTester propertyDbTester = new PropertyDbTester(dbTester);

  private ResourceTypes resourceTypes = mock(ResourceTypes.class);

  private ComponentDto project;
  private WsActionTester ws;

  private static QualityProfile createQProfile(String qpKey, String qpName, String languageKey) {
    return new QualityProfile(qpKey, qpName, languageKey, new Date());
  }

  private static String qualityProfilesToJson(QualityProfile... qps) {
    List<QualityProfile> qualityProfiles = Arrays.asList(qps);
    return QPMeasureData.toJson(new QPMeasureData(qualityProfiles));
  }

  @Before
  public void before() {
    OrganizationDto organization = dbTester.organizations().insertForKey("my-org");
    project =  newProjectDto(organization, "abcd")
        .setKey(PROJECT_KEY)
        .setName("Polop")
        .setDescription("test project")
        .setLanguage("xoo");
  }

  @Test
  public void fail_on_missing_parameters() throws Exception {
    init();

    expectedException.expect(IllegalArgumentException.class);
    ws.newRequest().execute();
  }

  @Test
  public void fail_on_unknown_component_key() throws Exception {
    init();

    expectedException.expect(NotFoundException.class);
    execute(project.key());
  }

  @Test
  public void fail_on_missing_permission() throws Exception {
    init();
    componentDbTester.insertComponent(project);

    expectedException.expect(ForbiddenException.class);
    execute(project.key());
  }

  @Test
  public void return_component_info_when_anonymous_no_snapshot() throws Exception {
    init();
    componentDbTester.insertComponent(project);
    userSessionRule.addProjectUuidPermissions(UserRole.USER, project.uuid());

    executeAndVerify(project.key(), "return_component_info_when_anonymous_no_snapshot.json");
  }

  @Test
  public void return_component_info_with_favourite() throws Exception {
    init();
    UserDto user = userDbTester.insertUser("obiwan");
    componentDbTester.insertComponent(project);
    propertyDbTester.insertProperty(new PropertyDto().setKey("favourite").setResourceId(project.getId()).setUserId(user.getId()));
    userSessionRule.login(user).addProjectUuidPermissions(UserRole.USER, project.uuid());

    executeAndVerify(project.key(), "return_component_info_with_favourite.json");
  }

  @Test
  public void return_component_info_when_snapshot() throws Exception {
    init();
    componentDbTester.insertComponent(project);
    componentDbTester.insertSnapshot(newAnalysis(project)
      .setCreatedAt(DateUtils.parseDateTime("2015-04-22T11:44:00+0200").getTime())
      .setVersion("3.14")
      .setLast(true));
    userSessionRule.addProjectUuidPermissions(UserRole.USER, project.uuid());

    executeAndVerify(project.key(), "return_component_info_when_snapshot.json");
  }

  @Test
  public void return_quality_profiles() throws Exception {
    init();
    componentDbTester.insertComponent(project);
    SnapshotDto analysis = componentDbTester.insertSnapshot(newAnalysis(project));
    addQualityProfiles(project, analysis,
      createQProfile("qp1", "Sonar Way Java", "java"),
      createQProfile("qp2", "Sonar Way Xoo", "xoo"));
    userSessionRule.addProjectUuidPermissions(UserRole.USER, project.uuid());

    executeAndVerify(project.key(), "return_quality_profiles.json");
  }

  @Test
  public void return_empty_quality_profiles_when_no_measure() throws Exception {
    init();
    componentDbTester.insertComponent(project);
    userSessionRule.addProjectUuidPermissions(UserRole.USER, project.uuid());

    executeAndVerify(project.key(), "return_empty_quality_profiles_when_no_measure.json");
  }

  @Test
  public void return_quality_gate_defined_on_project() throws Exception {
    init();
    componentDbTester.insertComponent(project);
    QualityGateDto qualityGateDto = dbTester.qualityGates().insertQualityGate("Sonar way");
    dbTester.qualityGates().associateProjectToQualityGate(project, qualityGateDto);
    userSessionRule.addProjectUuidPermissions(UserRole.USER, project.uuid());

    executeAndVerify(project.key(), "return_quality_gate.json");
  }

  @Test
  public void return_default_quality_gate() throws Exception {
    init();
    componentDbTester.insertComponent(project);
    dbTester.qualityGates().createDefaultQualityGate("Sonar way");
    userSessionRule.addProjectUuidPermissions(UserRole.USER, project.uuid());

    executeAndVerify(project.key(), "return_default_quality_gate.json");
  }

  @Test
  public void return_no_quality_gate_when_not_defined_on_project_and_no_default_one() throws Exception {
    init();
    componentDbTester.insertComponent(project);
    userSessionRule.addProjectUuidPermissions(UserRole.USER, project.uuid());

    String json = execute(project.key());
    assertThat(json).doesNotContain("qualityGate");
  }

  @Test
  public void return_extensions() throws Exception {
    init(createPages());
    componentDbTester.insertProjectAndSnapshot(project);
    userSessionRule.anonymous().addProjectUuidPermissions(UserRole.USER, project.uuid());

    executeAndVerify(project.key(), "return_extensions.json");
  }

  @Test
  public void return_extensions_for_admin() throws Exception {
    init(createPages());
    componentDbTester.insertProjectAndSnapshot(project);
    userSessionRule.anonymous()
      .addProjectUuidPermissions(UserRole.USER, project.uuid())
      .addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    executeAndVerify(project.key(), "return_extensions_for_admin.json");
  }

  @Test
  public void return_configuration_for_admin() throws Exception {
    UserDto user = userDbTester.insertUser();
    componentDbTester.insertComponent(project);
    userSessionRule.login(user)
      .addProjectUuidPermissions(UserRole.USER, "abcd")
      .addProjectUuidPermissions(UserRole.ADMIN, "abcd");

    Page page1 = Page.builder("my_plugin/first_page")
      .setName("First Page")
      .setAdmin(true)
      .setScope(COMPONENT)
      .setComponentQualifiers(Qualifier.PROJECT)
      .build();
    Page page2 = Page.builder("my_plugin/second_page")
      .setName("Second Page")
      .setAdmin(true)
      .setScope(COMPONENT)
      .setComponentQualifiers(Qualifier.PROJECT)
      .build();

    init(page1, page2);
    executeAndVerify(project.key(), "return_configuration_for_admin.json");
  }

  @Test
  public void return_configuration_with_all_properties() throws Exception {
    init();
    componentDbTester.insertComponent(project);
    userSessionRule.anonymous()
      .addProjectUuidPermissions(UserRole.USER, "abcd")
      .addProjectUuidPermissions(UserRole.ADMIN, "abcd");

    ResourceType projectResourceType = ResourceType.builder(project.qualifier())
      .setProperty("comparable", true)
      .setProperty("configurable", true)
      .setProperty("hasRolePolicy", true)
      .setProperty("modifiable_history", true)
      .setProperty("updatable_key", true)
      .setProperty("deletable", true)
      .build();
    when(resourceTypes.get(project.qualifier()))
      .thenReturn(projectResourceType);

    executeAndVerify(project.key(), "return_configuration_with_all_properties.json");
  }

  @Test
  public void return_breadcrumbs_on_module() throws Exception {
    init();
    ComponentDto project = componentDbTester.insertComponent(this.project);
    ComponentDto module = componentDbTester.insertComponent(newModuleDto("bcde", project).setKey("palap").setName("Palap"));
    userSessionRule.anonymous()
      .addProjectUuidPermissions(UserRole.USER, "abcd")
      .addProjectUuidPermissions(UserRole.ADMIN, "abcd");

    executeAndVerify(module.key(), "return_breadcrumbs_on_module.json");
  }

  @Test
  public void return_configuration_for_quality_profile_admin() throws Exception {
    init();
    componentDbTester.insertComponent(project);
    userSessionRule.anonymous()
      .addProjectUuidPermissions(UserRole.USER, "abcd")
      .setGlobalPermissions(QUALITY_PROFILE_ADMIN);

    executeAndVerify(project.key(), "return_configuration_for_quality_profile_admin.json");
  }

  @Test
  public void return_bread_crumbs_on_several_levels() throws Exception {
    init();
    ComponentDto project = componentDbTester.insertComponent(this.project);
    ComponentDto module = componentDbTester.insertComponent(newModuleDto("bcde", project).setKey("palap").setName("Palap"));
    ComponentDto directory = componentDbTester.insertComponent(newDirectory(module, "src/main/xoo"));
    ComponentDto file = componentDbTester.insertComponent(newFileDto(directory, directory, "cdef").setName("Source.xoo")
      .setKey("palap:src/main/xoo/Source.xoo")
      .setPath(directory.path()));
    userSessionRule.addProjectUuidPermissions(UserRole.USER, project.uuid());

    executeAndVerify(file.key(), "return_bread_crumbs_on_several_levels.json");
  }

  @Test
  public void work_with_only_system_admin() throws Exception {
    init(createPages());
    componentDbTester.insertProjectAndSnapshot(project);
    userSessionRule.setGlobalPermissions(SYSTEM_ADMIN);

    execute(project.key());
  }

  @Test
  public void test_example_response() throws Exception {
    init(createPages());
    OrganizationDto organizationDto = dbTester.organizations().insertForKey("my-org-1");
    ComponentDto project = newProjectDto(organizationDto, "ABCD")
      .setKey("org.codehaus.sonar:sonar")
      .setName("Sonarqube")
      .setDescription("Open source platform for continuous inspection of code quality");
    componentDbTester.insertComponent(project);
    SnapshotDto analysis = newAnalysis(project)
      .setCreatedAt(DateUtils.parseDateTime("2016-12-06T11:44:00+0200").getTime())
      .setVersion("6.3")
      .setLast(true);
    componentDbTester.insertSnapshot(analysis);
    when(resourceTypes.get(project.qualifier())).thenReturn(DefaultResourceTypes.get().getRootType());
    UserDto user = userDbTester.insertUser("obiwan");
    propertyDbTester.insertProperty(new PropertyDto().setKey("favourite").setResourceId(project.getId()).setUserId(user.getId()));
    addQualityProfiles(project, analysis,
      createQProfile("qp1", "Sonar Way Java", "java"),
      createQProfile("qp2", "Sonar Way Xoo", "xoo"));
    QualityGateDto qualityGateDto = dbTester.qualityGates().insertQualityGate("Sonar way");
    dbTester.qualityGates().associateProjectToQualityGate(project, qualityGateDto);
    userSessionRule.login(user)
      .addProjectUuidPermissions(UserRole.USER, project.uuid())
      .addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    String result = execute(project.key());
    assertJson(result).ignoreFields("snapshotDate", "key", "qualityGate.key").isSimilarTo(ws.getDef().responseExampleAsString());
  }

  private void init(Page... pages) {
    PluginRepository pluginRepository = mock(PluginRepository.class);
    when(pluginRepository.hasPlugin(anyString())).thenReturn(true);
    PageRepository pageRepository = new PageRepository(pluginRepository, new PageDefinition[] {context -> {
      for (Page page : pages) {
        context.addPage(page);
      }
    }});
    pageRepository.start();
    ws = new WsActionTester(
      new ComponentAction(dbClient, pageRepository, resourceTypes, userSessionRule, new ComponentFinder(dbClient),
        new QualityGateFinder(dbClient)));
  }

  private String execute(String componentKey) {
    return ws.newRequest().setParam("componentKey", componentKey).execute().getInput();
  }

  private void verify(String json, String expectedJson) {
    assertJson(json).isSimilarTo(getClass().getResource(ComponentActionTest.class.getSimpleName() + "/" + expectedJson));
  }

  private void executeAndVerify(String componentKey, String expectedJson) {
    verify(execute(componentKey), expectedJson);
  }

  private void addQualityProfiles(ComponentDto project, SnapshotDto analysis, QualityProfile... qps) {
    MetricDto metricDto = newMetricDto().setKey(QUALITY_PROFILES_KEY);
    dbClient.metricDao().insert(dbTester.getSession(), metricDto);
    dbClient.measureDao().insert(dbTester.getSession(),
      newMeasureDto(metricDto, project, analysis)
        .setData(qualityProfilesToJson(qps)));
    dbTester.commit();
  }

  private Page[] createPages() {
    Page page1 = Page.builder("my_plugin/first_page")
      .setName("First Page")
      .setScope(COMPONENT)
      .setComponentQualifiers(Qualifier.PROJECT)
      .build();
    Page page2 = Page.builder("my_plugin/second_page")
      .setName("Second Page")
      .setScope(COMPONENT)
      .setComponentQualifiers(Qualifier.PROJECT)
      .build();
    Page adminPage = Page.builder("my_plugin/admin_page")
      .setName("Admin Page")
      .setScope(COMPONENT)
      .setComponentQualifiers(Qualifier.PROJECT)
      .setAdmin(true)
      .build();

    return new Page[] {page1, page2, adminPage};
  }
}
