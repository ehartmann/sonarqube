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
package org.sonar.server.computation.step;

import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.component.SnapshotQuery;
import org.sonar.server.computation.analysis.AnalysisMetadataHolderRule;
import org.sonar.server.computation.batch.TreeRootHolderRule;
import org.sonar.server.computation.component.Component;
import org.sonar.server.computation.component.ViewsComponent;
import org.sonar.server.computation.period.Period;
import org.sonar.server.computation.period.PeriodsHolderRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.core.config.CorePropertyDefinitions.TIMEMACHINE_MODE_DATE;
import static org.sonar.db.component.ComponentTesting.newProjectDto;
import static org.sonar.db.component.ComponentTesting.newSubView;
import static org.sonar.db.component.ComponentTesting.newView;
import static org.sonar.server.computation.component.Component.Type.PROJECT_VIEW;
import static org.sonar.server.computation.component.Component.Type.SUBVIEW;
import static org.sonar.server.computation.component.Component.Type.VIEW;

public class ViewsPersistAnalysisStepTest extends BaseStepTest {

  private static final String ANALYSIS_UUID = "U1";

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);
  @Rule
  public TreeRootHolderRule treeRootHolder = new TreeRootHolderRule();
  @Rule
  public AnalysisMetadataHolderRule analysisMetadataHolder = new AnalysisMetadataHolderRule();
  @Rule
  public PeriodsHolderRule periodsHolder = new PeriodsHolderRule();

  System2 system2 = mock(System2.class);

  DbClient dbClient = dbTester.getDbClient();

  long analysisDate;

  long now;

  PersistAnalysisStep underTest;

  @Before
  public void setup() {
    analysisDate = DateUtils.parseDateQuietly("2015-06-01").getTime();
    analysisMetadataHolder.setUuid(ANALYSIS_UUID);
    analysisMetadataHolder.setAnalysisDate(analysisDate);

    now = DateUtils.parseDateQuietly("2015-06-02").getTime();

    when(system2.now()).thenReturn(now);

    underTest = new PersistAnalysisStep(system2, dbClient, treeRootHolder, analysisMetadataHolder, periodsHolder);

    // initialize PeriodHolder to empty by default
    periodsHolder.setPeriods();
  }

  @Override
  protected ComputationStep step() {
    return underTest;
  }

  @Test
  public void persist_analysis() {
    ComponentDto viewDto = save(newView("UUID_VIEW").setKey("KEY_VIEW"));
    save(newSubView(viewDto, "UUID_SUBVIEW", "KEY_SUBVIEW"));
    save(newProjectDto("proj"));
    dbTester.getSession().commit();

    Component projectView = ViewsComponent.builder(PROJECT_VIEW, "KEY_PROJECT_COPY").setUuid("UUID_PROJECT_COPY").build();
    Component subView = ViewsComponent.builder(SUBVIEW, "KEY_SUBVIEW").setUuid("UUID_SUBVIEW").addChildren(projectView).build();
    Component view = ViewsComponent.builder(VIEW, "KEY_VIEW").setUuid("UUID_VIEW").addChildren(subView).build();
    treeRootHolder.setRoot(view);

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("snapshots")).isEqualTo(1);

    SnapshotDto viewSnapshot = getUnprocessedSnapshot(viewDto.uuid());
    assertThat(viewSnapshot.getUuid()).isEqualTo(ANALYSIS_UUID);
    assertThat(viewSnapshot.getComponentUuid()).isEqualTo(view.getUuid());
    assertThat(viewSnapshot.getVersion()).isNull();
    assertThat(viewSnapshot.getLast()).isFalse();
    assertThat(viewSnapshot.getStatus()).isEqualTo("U");
    assertThat(viewSnapshot.getCreatedAt()).isEqualTo(analysisDate);
    assertThat(viewSnapshot.getBuildDate()).isEqualTo(now);
  }

  @Test
  public void persist_snapshots_with_periods() {
    ComponentDto viewDto = save(newView("UUID_VIEW").setKey("KEY_VIEW"));
    ComponentDto subViewDto = save(newSubView(viewDto, "UUID_SUBVIEW", "KEY_SUBVIEW"));
    dbTester.getSession().commit();

    Component subView = ViewsComponent.builder(SUBVIEW, "KEY_SUBVIEW").setUuid("UUID_SUBVIEW").build();
    Component view = ViewsComponent.builder(VIEW, "KEY_VIEW").setUuid("UUID_VIEW").addChildren(subView).build();
    treeRootHolder.setRoot(view);

    periodsHolder.setPeriods(new Period(1, TIMEMACHINE_MODE_DATE, "2015-01-01", analysisDate, "u1"));

    underTest.execute();

    SnapshotDto viewSnapshot = getUnprocessedSnapshot(viewDto.uuid());
    assertThat(viewSnapshot.getPeriodMode(1)).isEqualTo(TIMEMACHINE_MODE_DATE);
    assertThat(viewSnapshot.getPeriodDate(1)).isEqualTo(analysisDate);
    assertThat(viewSnapshot.getPeriodModeParameter(1)).isNotNull();
  }

  private ComponentDto save(ComponentDto componentDto) {
    dbClient.componentDao().insert(dbTester.getSession(), componentDto);
    return componentDto;
  }

  private SnapshotDto getUnprocessedSnapshot(String componentUuid) {
    List<SnapshotDto> projectSnapshots = dbClient.snapshotDao().selectAnalysesByQuery(dbTester.getSession(),
      new SnapshotQuery().setComponentUuid(componentUuid).setIsLast(false).setStatus(SnapshotDto.STATUS_UNPROCESSED));
    assertThat(projectSnapshots).hasSize(1);
    return projectSnapshots.get(0);
  }

}
