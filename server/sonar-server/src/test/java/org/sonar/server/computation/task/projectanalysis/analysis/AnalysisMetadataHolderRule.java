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
package org.sonar.server.computation.task.projectanalysis.analysis;

import java.util.Date;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.junit.rules.ExternalResource;
import org.sonar.server.computation.task.projectanalysis.qualityprofile.QualityProfile;
import org.sonar.server.computation.util.InitializedProperty;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class AnalysisMetadataHolderRule extends ExternalResource implements MutableAnalysisMetadataHolder {

  private final InitializedProperty<String> uuid = new InitializedProperty<>();

  private final InitializedProperty<Long> analysisDate = new InitializedProperty<>();

  private final InitializedProperty<Analysis> baseProjectSnapshot = new InitializedProperty<>();

  private final InitializedProperty<Boolean> crossProjectDuplicationEnabled = new InitializedProperty<>();

  private final InitializedProperty<String> branch = new InitializedProperty<>();

  private final InitializedProperty<Integer> rootComponentRef = new InitializedProperty<>();

  private final InitializedProperty<Map<String, QualityProfile>> qProfilesPerLanguage = new InitializedProperty<>();

  @Override
  public String getUuid() {
    checkState(uuid.isInitialized(), "Analysis UUID has not been set");
    return this.uuid.getProperty();
  }

  @Override
  public AnalysisMetadataHolderRule setUuid(String s) {
    checkNotNull(s, "UUID must not be null");
    this.uuid.setProperty(s);
    return this;
  }

  public AnalysisMetadataHolderRule setAnalysisDate(Date date) {
    checkNotNull(date, "Date must not be null");
    this.analysisDate.setProperty(date.getTime());
    return this;
  }

  @Override
  public AnalysisMetadataHolderRule setAnalysisDate(long date) {
    checkNotNull(date, "Date must not be null");
    this.analysisDate.setProperty(date);
    return this;
  }

  @Override
  public long getAnalysisDate() {
    checkState(analysisDate.isInitialized(), "Analysis date has not been set");
    return this.analysisDate.getProperty();
  }

  @Override
  public boolean isFirstAnalysis() {
    return getBaseProjectSnapshot() == null;
  }

  @Override
  public AnalysisMetadataHolderRule setBaseProjectSnapshot(@Nullable Analysis baseProjectAnalysis) {
    this.baseProjectSnapshot.setProperty(baseProjectAnalysis);
    return this;
  }

  @Override
  @CheckForNull
  public Analysis getBaseProjectSnapshot() {
    checkState(baseProjectSnapshot.isInitialized(), "Base project snapshot has not been set");
    return baseProjectSnapshot.getProperty();
  }

  @Override
  public AnalysisMetadataHolderRule setCrossProjectDuplicationEnabled(boolean isCrossProjectDuplicationEnabled) {
    this.crossProjectDuplicationEnabled.setProperty(isCrossProjectDuplicationEnabled);
    return this;
  }

  @Override
  public boolean isCrossProjectDuplicationEnabled() {
    checkState(crossProjectDuplicationEnabled.isInitialized(), "Cross project duplication flag has not been set");
    return crossProjectDuplicationEnabled.getProperty();
  }

  @Override
  public AnalysisMetadataHolderRule setBranch(@Nullable String branch) {
    this.branch.setProperty(branch);
    return this;
  }

  @Override
  public String getBranch() {
    checkState(branch.isInitialized(), "Branch has not been set");
    return branch.getProperty();
  }

  @Override
  public AnalysisMetadataHolderRule setRootComponentRef(int rootComponentRef) {
    this.rootComponentRef.setProperty(rootComponentRef);
    return this;
  }

  @Override
  public int getRootComponentRef() {
    checkState(rootComponentRef.isInitialized(), "Root component ref has not been set");
    return rootComponentRef.getProperty();
  }

  @Override
  public AnalysisMetadataHolderRule setQProfilesByLanguage(Map<String, QualityProfile> qProfilesPerLanguage) {
    this.qProfilesPerLanguage.setProperty(qProfilesPerLanguage);
    return this;
  }

  @Override
  public Map<String, QualityProfile> getQProfilesByLanguage() {
    checkState(qProfilesPerLanguage.isInitialized(), "QProfile per language has not been set");
    return qProfilesPerLanguage.getProperty();
  }
}