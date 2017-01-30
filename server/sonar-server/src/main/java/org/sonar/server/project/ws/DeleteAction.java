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
package org.sonar.server.project.ws;

import javax.annotation.Nullable;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.web.UserRole;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.server.component.ComponentCleanerService;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.user.UserSession;

import static java.util.Arrays.asList;
import static org.sonar.server.component.ComponentFinder.ParamNames.ID_AND_KEY;
import static org.sonar.server.ws.KeyExamples.KEY_PROJECT_EXAMPLE_001;

public class DeleteAction implements ProjectsWsAction {
  private static final String ACTION = "delete";

  public static final String PARAM_ID = "id";
  public static final String PARAM_KEY = "key";

  private final ComponentCleanerService componentCleanerService;
  private final ComponentFinder componentFinder;
  private final DbClient dbClient;
  private final UserSession userSession;

  public DeleteAction(ComponentCleanerService componentCleanerService, ComponentFinder componentFinder, DbClient dbClient, UserSession userSession) {
    this.componentCleanerService = componentCleanerService;
    this.componentFinder = componentFinder;
    this.dbClient = dbClient;
    this.userSession = userSession;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context
      .createAction(ACTION)
      .setPost(true)
      .setDescription("Delete a project.<br /> Requires 'Administer System' permission or 'Administer' permission on the project.")
      .setSince("5.2")
      .setHandler(this);

    action
      .createParam(PARAM_ID)
      .setDescription("Project id")
      .setExampleValue("ce4c03d6-430f-40a9-b777-ad877c00aa4d");

    action
      .createParam(PARAM_KEY)
      .setDescription("Project key")
      .setExampleValue(KEY_PROJECT_EXAMPLE_001);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    String uuid = request.param(PARAM_ID);
    String key = request.param(PARAM_KEY);
    checkPermissions(uuid, key);

    try (DbSession dbSession = dbClient.openSession(false)) {
      ComponentDto project = componentFinder.getByUuidOrKey(dbSession, uuid, key, ID_AND_KEY);
      componentCleanerService.delete(dbSession, asList(project));
    }

    response.noContent();
  }

  private void checkPermissions(@Nullable String uuid, @Nullable String key) {
    if (missPermissionsBasedOnUuid(uuid) || missPermissionsBasedOnKey(key)) {
      userSession.checkLoggedIn().checkPermission(GlobalPermissions.SYSTEM_ADMIN);
    }
  }

  private boolean missPermissionsBasedOnKey(@Nullable String key) {
    return key != null && !userSession.hasComponentPermission(UserRole.ADMIN, key) && !userSession.hasPermission(GlobalPermissions.SYSTEM_ADMIN);
  }

  private boolean missPermissionsBasedOnUuid(@Nullable String uuid) {
    return uuid != null && !userSession.hasComponentUuidPermission(UserRole.ADMIN, uuid) && !userSession.hasPermission(GlobalPermissions.SYSTEM_ADMIN);
  }

}
