/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.internal.protocol.protobuf.security;

import static org.apache.geode.security.ResourcePermission.ALL;
import static org.apache.geode.security.ResourcePermission.Operation.READ;
import static org.apache.geode.security.ResourcePermission.Operation.WRITE;
import static org.apache.geode.security.ResourcePermission.Resource.DATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.query.SelectResults;
import org.apache.geode.cache.query.Struct;
import org.apache.geode.cache.query.internal.DefaultQuery;
import org.apache.geode.cache.query.internal.InternalQueryService;
import org.apache.geode.cache.query.internal.ResultsBag;
import org.apache.geode.cache.query.internal.StructBag;
import org.apache.geode.cache.query.internal.StructImpl;
import org.apache.geode.cache.query.internal.types.ObjectTypeImpl;
import org.apache.geode.cache.query.internal.types.StructTypeImpl;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.cache.InternalCacheForClientAccess;
import org.apache.geode.security.NotAuthorizedException;
import org.apache.geode.security.ResourcePermission;
import org.apache.geode.test.junit.categories.ClientServerTest;

@Category({ClientServerTest.class})
public class SecureCacheImplTest {

  public static final String REGION = "TestRegion";
  private SecureCacheImpl authorizingCache;
  private InternalCache cache;
  private Security security;
  private Region region;

  @Before
  public void setUp() {
    cache = mock(InternalCacheForClientAccess.class);
    when(cache.getCacheForProcessingClientRequests()).thenReturn(cache);
    region = mock(Region.class);
    when(cache.getRegion(REGION)).thenReturn(region);
    security = mock(Security.class);
    doThrow(NotAuthorizedException.class).when(security).authorize(any());
    doThrow(NotAuthorizedException.class).when(security).authorize(any(), any(), any(), any());
    when(security.postProcess(any(), any(), any())).then(invocation -> invocation.getArgument(2));
    when(security.needsPostProcessing()).thenReturn(true);
    authorizingCache = new SecureCacheImpl(cache, security);
  }

  @Test
  public void getAllSuccesses() throws Exception {
    authorize(DATA, READ, REGION, "a");
    authorize(DATA, READ, REGION, "b");
    Map<Object, Object> okValues = new HashMap<>();
    Map<Object, Exception> exceptionValues = new HashMap<>();

    when(region.get("b")).thenReturn("existing value");

    authorizingCache.getAll(REGION, Arrays.asList("a", "b"), okValues::put, exceptionValues::put);

    verify(region).get("a");
    verify(region).get("b");
    assertThat(okValues).containsOnly(entry("a", null), entry("b", "existing value"));
    assertThat(exceptionValues).isEmpty();
  }

  @Test
  public void getAllWithRegionLevelAuthorizationSucceeds() throws Exception {
    authorize(DATA, READ, REGION, ALL);
    Map<Object, Object> okValues = new HashMap<>();
    Map<Object, Exception> exceptionValues = new HashMap<>();

    when(region.get("b")).thenReturn("existing value");

    authorizingCache.getAll(REGION, Arrays.asList("a", "b"), okValues::put, exceptionValues::put);

    verify(region).get("a");
    verify(region).get("b");
    assertThat(okValues).containsOnly(entry("a", null), entry("b", "existing value"));
    assertThat(exceptionValues).isEmpty();
  }

  @Test
  public void getAllWithFailure() throws Exception {
    authorize(DATA, READ, REGION, "b");
    Map<Object, Object> okValues = new HashMap<>();
    Map<Object, Exception> exceptionValues = new HashMap<>();

    when(region.get("b")).thenReturn("existing value");

    authorizingCache.getAll(REGION, Arrays.asList("a", "b"), okValues::put, exceptionValues::put);

    verify(region).get("b");
    verifyNoMoreInteractions(region);
    assertThat(okValues).containsOnly(entry("b", "existing value"));
    assertThat(exceptionValues).containsOnlyKeys("a");
    assertThat(exceptionValues.values().iterator().next())
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  public void getAllIsPostProcessed() throws Exception {
    authorize(DATA, READ, REGION, "a");
    authorize(DATA, READ, REGION, "b");
    when(security.postProcess(any(), any(), any())).thenReturn("spam");
    Map<Object, Object> okValues = new HashMap<>();
    Map<Object, Exception> exceptionValues = new HashMap<>();

    when(region.get("b")).thenReturn("existing value");

    authorizingCache.getAll(REGION, Arrays.asList("a", "b"), okValues::put, exceptionValues::put);

    verify(region).get("a");
    verify(region).get("b");
    assertThat(okValues).containsOnly(entry("a", "spam"), entry("b", "spam"));
    assertThat(exceptionValues).isEmpty();
  }

  private void authorize(ResourcePermission.Resource resource,
      ResourcePermission.Operation operation, String region, String key) {
    doNothing().when(security).authorize(resource, operation, region, key);
  }

  @Test
  public void get() throws Exception {
    authorize(DATA, READ, REGION, "a");
    when(region.get("a")).thenReturn("value");
    assertEquals("value", authorizingCache.get(REGION, "a"));
  }

  @Test
  public void getWithFailure() throws Exception {
    assertThatThrownBy(() -> authorizingCache.get(REGION, "a"))
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  public void getIsPostProcessed() throws Exception {
    authorize(DATA, READ, REGION, "a");
    when(security.postProcess(REGION, "a", "value")).thenReturn("spam");
    when(region.get("a")).thenReturn("value");
    assertEquals("spam", authorizingCache.get(REGION, "a"));
  }

  @Test
  public void put() throws Exception {
    authorize(DATA, WRITE, REGION, "a");
    authorizingCache.put(REGION, "a", "value");
    verify(region).put("a", "value");
  }

  @Test
  public void putWithFailure() throws Exception {
    assertThatThrownBy(() -> authorizingCache.put(REGION, "a", "value"))
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  public void putAll() throws Exception {
    authorize(DATA, WRITE, REGION, "a");
    authorize(DATA, WRITE, REGION, "c");
    Map<Object, Object> entries = new HashMap<>();
    entries.put("a", "b");
    entries.put("c", "d");

    Map<Object, Exception> exceptionValues = new HashMap<>();

    authorizingCache.putAll(REGION, entries, exceptionValues::put);

    verify(region).put("a", "b");
    verify(region).put("c", "d");
    assertThat(exceptionValues).isEmpty();
  }

  @Test
  public void putAllWithRegionLevelAuthorizationSucceeds() throws Exception {
    authorize(DATA, WRITE, REGION, ALL);
    Map<Object, Object> entries = new HashMap<>();
    entries.put("a", "b");
    entries.put("c", "d");

    Map<Object, Exception> exceptionValues = new HashMap<>();

    authorizingCache.putAll(REGION, entries, exceptionValues::put);

    verify(region).put("a", "b");
    verify(region).put("c", "d");
    assertThat(exceptionValues).isEmpty();
  }

  @Test
  public void putAllWithFailure() throws Exception {
    authorize(DATA, WRITE, REGION, "a");
    Map<Object, Object> entries = new HashMap<>();
    entries.put("a", "b");
    entries.put("c", "d");

    Map<Object, Exception> exceptionValues = new HashMap<>();

    authorizingCache.putAll(REGION, entries, exceptionValues::put);

    verify(security).authorize(DATA, WRITE, REGION, "a");
    verify(security).authorize(DATA, WRITE, REGION, "c");
    verify(region).put("a", "b");
    verifyNoMoreInteractions(region);
    assertThat(exceptionValues).containsOnlyKeys("c");
  }

  @Test
  public void remove() throws Exception {
    authorize(DATA, WRITE, REGION, "a");
    authorizingCache.remove(REGION, "a");
    verify(region).remove("a");
  }

  @Test
  public void removeWithoutAuthorization() throws Exception {
    assertThatThrownBy(() -> authorizingCache.remove(REGION, "a"))
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  public void removeIsPostProcessed() throws Exception {
    authorize(DATA, WRITE, REGION, "a");
    when(region.remove("a")).thenReturn("value");
    when(security.postProcess(REGION, "a", "value")).thenReturn("spam");
    Object value = authorizingCache.remove(REGION, "a");
    verify(region).remove("a");
    assertEquals("spam", value);
  }

  @Test
  public void getRegionNames() throws Exception {
    authorize(DATA, READ, ALL, ALL);
    Set<Region<?, ?>> regions = new HashSet<>();
    regions.add(region);
    when(cache.rootRegions()).thenReturn(regions);

    Set subregions = new HashSet<>();
    Region region2 = mock(Region.class);
    subregions.add(region2);
    Region region3 = mock(Region.class);
    subregions.add(region3);
    when(region.getFullPath()).thenReturn("region1");
    when(region2.getFullPath()).thenReturn("region2");
    when(region3.getFullPath()).thenReturn("region3");
    when(region.subregions(true)).thenReturn(subregions);
    Collection<String> regionNames = authorizingCache.getRegionNames();
    assertThat(regionNames).containsExactly("region1", "region2", "region3");

    verify(cache).rootRegions();
  }

  @Test
  public void getRegionNamesWithoutAuthorization() throws Exception {
    assertThatThrownBy(() -> authorizingCache.getRegionNames())
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  public void getSize() throws Exception {
    authorize(DATA, READ, REGION, ALL);
    authorizingCache.getSize(REGION);
    verify(region).size();
  }

  @Test
  public void getSizeWithoutAuthorization() throws Exception {
    assertThatThrownBy(() -> authorizingCache.getSize(REGION))
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  public void keySet() throws Exception {
    authorize(DATA, READ, REGION, ALL);
    authorizingCache.keySet(REGION);
    verify(region).keySet();
  }

  @Test
  public void keySetWithoutAuthorization() throws Exception {
    assertThatThrownBy(() -> authorizingCache.keySet(REGION))
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  public void clear() throws Exception {
    authorize(DATA, WRITE, REGION, ALL);
    authorizingCache.clear(REGION);
    verify(region).clear();
  }

  @Test
  public void clearWithoutAuthorization() throws Exception {
    assertThatThrownBy(() -> authorizingCache.clear(REGION))
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  public void putIfAbsent() throws Exception {
    authorize(DATA, WRITE, REGION, "a");
    String oldValue = authorizingCache.putIfAbsent(REGION, "a", "b");
    verify(region).putIfAbsent("a", "b");
  }

  @Test
  public void putIfAbsentIsPostProcessed() throws Exception {
    authorize(DATA, WRITE, REGION, "a");
    when(region.putIfAbsent(any(), any())).thenReturn("value");
    when(security.postProcess(REGION, "a", "value")).thenReturn("spam");
    String oldValue = authorizingCache.putIfAbsent(REGION, "a", "b");
    verify(region).putIfAbsent("a", "b");
    assertEquals("spam", oldValue);
  }

  @Test
  public void putIfAbsentWithoutAuthorization() throws Exception {
    assertThatThrownBy(() -> authorizingCache.putIfAbsent(REGION, "a", "b"))
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  public void query() throws Exception {
    authorize(DATA, READ, REGION, ALL);
    mockQuery();
    String queryString = "select * from /region";
    Object[] bindParameters = {"a"};
    authorizingCache.query(queryString, bindParameters);
  }

  @Test
  public void queryIsPostProcessedWithSingleResultValue() throws Exception {
    authorize(DATA, READ, REGION, ALL);
    when(security.postProcess(any(), any(), any())).thenReturn("spam");
    DefaultQuery query = mockQuery();
    String queryString = "select * from /region";
    Object[] bindParameters = {"a"};
    when(query.execute(bindParameters)).thenReturn("value");
    Object result = authorizingCache.query(queryString, bindParameters);
    assertEquals("spam", result);
  }

  @Test
  public void queryIsPostProcessedWithListOfObjectValues() throws Exception {
    authorize(DATA, READ, REGION, ALL);
    when(security.postProcess(any(), any(), any())).thenReturn("spam");
    DefaultQuery query = mockQuery();
    String queryString = "select * from /region";
    Object[] bindParameters = {"a"};

    SelectResults results = new ResultsBag();
    results.setElementType(new ObjectTypeImpl(Object.class));
    results.add("value1");
    results.add("value2");
    when(query.execute((Object[]) any())).thenReturn(results);
    SelectResults<String> result =
        (SelectResults<String>) authorizingCache.query(queryString, bindParameters);
    assertEquals(Arrays.asList("spam", "spam"), result.asList());
  }

  @Test
  public void queryIsPostProcessedWithListOfStructValues() throws Exception {
    authorize(DATA, READ, REGION, ALL);
    when(security.postProcess(any(), any(), any())).thenReturn("spam");

    SelectResults<Struct> results = buildListOfStructs("value1", "value2");
    DefaultQuery query = mockQuery();
    String queryString = "select * from /region";
    Object[] bindParameters = {"a"};
    when(query.execute((Object[]) any())).thenReturn(results);
    SelectResults<String> result =
        (SelectResults<String>) authorizingCache.query(queryString, bindParameters);
    assertEquals(buildListOfStructs("spam", "spam").asList(), result.asList());
  }

  private SelectResults<Struct> buildListOfStructs(String... values) {
    SelectResults<Struct> results = new StructBag();
    StructTypeImpl elementType = new StructTypeImpl(new String[] {"field1"});
    results.setElementType(elementType);
    for (String value : values) {
      results.add(new StructImpl(elementType, new Object[] {value}));
    }
    return results;
  }

  private DefaultQuery mockQuery() {
    InternalQueryService queryService = mock(InternalQueryService.class);
    when(cache.getQueryService()).thenReturn(queryService);
    DefaultQuery query = mock(DefaultQuery.class);
    when(queryService.newQuery(any())).thenReturn(query);
    when(query.getRegionsInQuery(any())).thenReturn(Collections.singleton(REGION));
    return query;
  }

}
