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
package org.sonar.server.es;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.HasAggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.missing.Missing;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;

import static org.sonarqube.ws.client.issue.IssuesWsParameters.FACET_MODE_EFFORT;

public class Facets {

  public static final String TOTAL = "total";

  private final LinkedHashMap<String, LinkedHashMap<String, Long>> facetsByName;

  public Facets(LinkedHashMap<String, LinkedHashMap<String, Long>> facetsByName) {
    this.facetsByName = facetsByName;
  }

  public Facets(SearchResponse response) {
    this.facetsByName = new LinkedHashMap<>();
    Aggregations aggregations = response.getAggregations();
    if (aggregations != null) {
      for (Aggregation facet : aggregations) {
        processAggregation(facet);
      }
    }
  }

  private void processAggregation(Aggregation aggregation) {
    if (Missing.class.isAssignableFrom(aggregation.getClass())) {
      processMissingAggregation((Missing) aggregation);
    } else if (Terms.class.isAssignableFrom(aggregation.getClass())) {
      processTermsAggregation((Terms) aggregation);
    } else if (HasAggregations.class.isAssignableFrom(aggregation.getClass())) {
      processSubAggregations((HasAggregations) aggregation);
    } else if (Histogram.class.isAssignableFrom(aggregation.getClass())) {
      processDateHistogram((Histogram) aggregation);
    } else if (Sum.class.isAssignableFrom(aggregation.getClass())) {
      processSum((Sum) aggregation);
    } else if (MultiBucketsAggregation.class.isAssignableFrom(aggregation.getClass())) {
      processMultiBucketAggregation((MultiBucketsAggregation) aggregation);
    } else {
      throw new IllegalArgumentException("Aggregation type not supported yet: " + aggregation.getClass());
    }
  }

  private void processMissingAggregation(Missing aggregation) {
    long docCount = aggregation.getDocCount();
    if (docCount > 0L) {
      LinkedHashMap<String, Long> facet = getOrCreateFacet(aggregation.getName().replace("_missing", ""));
      if (aggregation.getAggregations().getAsMap().containsKey(FACET_MODE_EFFORT)) {
        facet.put("", Math.round(((Sum) aggregation.getAggregations().get(FACET_MODE_EFFORT)).getValue()));
      } else {
        facet.put("", docCount);
      }
    }
  }

  private void processTermsAggregation(Terms aggregation) {
    String facetName = aggregation.getName();
    // TODO document this naming convention
    if (facetName.contains("__") && !facetName.startsWith("__")) {
      facetName = facetName.substring(0, facetName.indexOf("__"));
    }
    facetName = facetName.replace("_selected", "");
    LinkedHashMap<String, Long> facet = getOrCreateFacet(facetName);
    for (Terms.Bucket value : aggregation.getBuckets()) {
      if (value.getAggregations().getAsMap().containsKey(FACET_MODE_EFFORT)) {
        facet.put(value.getKeyAsString(), Math.round(((Sum) value.getAggregations().get(FACET_MODE_EFFORT)).getValue()));
      } else {
        facet.put(value.getKeyAsString(), value.getDocCount());
      }
    }
  }

  private void processSubAggregations(HasAggregations aggregation) {
    for (Aggregation sub : aggregation.getAggregations()) {
      processAggregation(sub);
    }
  }

  private void processDateHistogram(Histogram aggregation) {
    LinkedHashMap<String, Long> facet = getOrCreateFacet(aggregation.getName());
    for (Histogram.Bucket value : aggregation.getBuckets()) {
      if (value.getAggregations().getAsMap().containsKey(FACET_MODE_EFFORT)) {
        facet.put(value.getKeyAsString(), Math.round(((Sum) value.getAggregations().get(FACET_MODE_EFFORT)).getValue()));
      } else {
        facet.put(value.getKeyAsString(), value.getDocCount());
      }
    }
  }

  private void processSum(Sum aggregation) {
    getOrCreateFacet(aggregation.getName()).put(TOTAL, Math.round(aggregation.getValue()));
  }

  private void processMultiBucketAggregation(MultiBucketsAggregation aggregation) {
    LinkedHashMap<String, Long> facet = getOrCreateFacet(aggregation.getName());
    aggregation.getBuckets().forEach(bucket -> facet.put(bucket.getKeyAsString(), bucket.getDocCount()));
  }

  public boolean contains(String facetName) {
    return facetsByName.containsKey(facetName);
  }

  /**
   * The buckets of the given facet. Null if the facet does not exist
   */
  @CheckForNull
  public LinkedHashMap<String, Long> get(String facetName) {
    return facetsByName.get(facetName);
  }

  public Map<String, LinkedHashMap<String, Long>> getAll() {
    return facetsByName;
  }

  public Set<String> getBucketKeys(String facetName) {
    LinkedHashMap<String, Long> facet = facetsByName.get(facetName);
    if (facet != null) {
      return facet.keySet();
    }
    return Collections.emptySet();
  }

  public Set<String> getNames() {
    return facetsByName.keySet();
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SIMPLE_STYLE);
  }

  private LinkedHashMap<String, Long> getOrCreateFacet(String facetName) {
    LinkedHashMap<String, Long> facet = facetsByName.get(facetName);
    if (facet == null) {
      facet = new LinkedHashMap<>();
      facetsByName.put(facetName, facet);
    }
    return facet;
  }
}
