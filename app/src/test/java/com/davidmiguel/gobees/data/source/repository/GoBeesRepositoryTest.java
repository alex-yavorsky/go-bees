/*
 * GoBees
 * Copyright (c) 2016 - 2017 David Miguel Lozano
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.davidmiguel.gobees.data.source.repository;

import com.davidmiguel.gobees.data.model.Apiary;
import com.davidmiguel.gobees.data.model.mothers.ApiaryMother;
import com.davidmiguel.gobees.data.source.GoBeesDataSource;
import com.davidmiguel.gobees.data.source.GoBeesDataSource.GetApiariesCallback;
import com.davidmiguel.gobees.data.source.GoBeesDataSource.GetApiaryCallback;
import com.davidmiguel.gobees.data.source.GoBeesDataSource.GetHiveCallback;
import com.davidmiguel.gobees.data.source.GoBeesDataSource.TaskCallback;
import com.davidmiguel.gobees.data.source.local.GoBeesLocalDataSource;
import com.davidmiguel.gobees.data.source.network.WeatherDataSource;
import com.google.common.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the implementation of the in-memory repository with cache.
 */
public class GoBeesRepositoryTest {

    private static final long APIARY_ID = 1;

    private static final long HIVE_ID = 1;

    private static List<Apiary> APIARIES;

    private GoBeesRepository goBeesRepository;

    @Mock
    private GoBeesLocalDataSource goBeesLocalDataSource;

    @Mock
    private WeatherDataSource weatherDataSource;

    @Mock
    private GetApiariesCallback getApiariesCallback;

    @Mock
    private GetApiaryCallback getApiaryCallback;

    @Mock
    private GetHiveCallback getHiveCallback;

    @Mock
    private TaskCallback taskCallback;

    @Captor
    private ArgumentCaptor<GetApiariesCallback> apiariesCallbackArgumentCaptor;

    @Captor
    private ArgumentCaptor<GetApiaryCallback> apiaryCallbackArgumentCaptor;

    @Before
    public void setupTasksRepository() {
        // To inject the mocks in the test the initMocks method needs to be called
        MockitoAnnotations.initMocks(this);

        // Get a reference to the class under test
        goBeesRepository = GoBeesRepository.getInstance(goBeesLocalDataSource, weatherDataSource);

        // We start the apiaries to 3
        APIARIES = Lists.newArrayList(
                ApiaryMother.newDefaultApiary(),
                ApiaryMother.newDefaultApiary(),
                ApiaryMother.newDefaultApiary());
    }

    @After
    public void destroyRepositoryInstance() {
        GoBeesRepository.destroyInstance();
    }

    @Test
    public void getApiaries_requestsAllApiariesFromLocalDataSource() {
        // When apiaries are requested from the tasks repository
        goBeesRepository.getApiaries(getApiariesCallback);

        // Then apiaries are loaded from the local data source
        verify(goBeesLocalDataSource).getApiaries(any(GoBeesDataSource.GetApiariesCallback.class));
    }

    @Test
    public void getApiaries_apiariesAreCachedAfterFirstCall() {
        // Given a setup Captor to capture callbacks
        // When two calls are issued to the GoBees repository
        twoGetApiariesCallsToRepository(getApiariesCallback);

        // Then apiaries were only requested once from local data source
        // The second one should be served by cache
        verify(goBeesLocalDataSource).getApiaries(any(GoBeesDataSource.GetApiariesCallback.class));
    }

    @Test
    public void getApiaryWithDataSourceUnavailable_firesOnDataUnavailable() {
        // When calling getApiaries in the repository
        goBeesRepository.getApiaries(getApiariesCallback);

        // And the local data source has no data available
        setApiariesNotAvailable(goBeesLocalDataSource);

        // Verify no data is returned
        verify(getApiariesCallback).onDataNotAvailable();
    }

    @Test
    public void getApiaryWithDataSourcesUnavailable_firesOnDataUnavailable() {
        // When calling getApiary in the repository
        goBeesRepository.getApiary(APIARY_ID, getApiaryCallback);

        // And the local data source has no data available
        setApiaryNotAvailable(goBeesLocalDataSource, APIARY_ID);

        // Verify no data is returned
        verify(getApiaryCallback).onDataNotAvailable();
    }

    @Test
    public void saveApiary_savesApiaryToLocalDataSource() {
        // Given a stub apiary
        Apiary apiary = ApiaryMother.newDefaultApiary();

        // When an apiary is saved to the GoBees repository
        goBeesRepository.saveApiary(apiary, taskCallback);

        // Then the persistent repository are called and the cache is updated
        verify(goBeesLocalDataSource).saveApiary(apiary, taskCallback);
        assertThat(goBeesRepository.cachedApiaries.size(), is(1));
    }

    @Test
    public void getApiary_requestsSingleApiaryFromLocalDataSource() {
        // When an apiary is requested from the GoBees repository
        goBeesRepository.getApiary(APIARY_ID, getApiaryCallback);

        // Then the apiary is loaded from the database
        verify(goBeesLocalDataSource).getApiary(eq(APIARY_ID), any(GetApiaryCallback.class));
    }

    @Test
    public void deleteApiary_deleteApiaryFromDbAndCache() {
        // Given an apiary in the repository
        Apiary apiary = ApiaryMother.newDefaultApiary();
        goBeesRepository.saveApiary(apiary, taskCallback);
        assertThat(goBeesRepository.cachedApiaries.containsKey(apiary.getId()), is(true));

        // When deleted
        goBeesRepository.deleteApiary(apiary.getId(), taskCallback);

        // Verify the data source were called
        verify(goBeesLocalDataSource).deleteApiary(apiary.getId(), taskCallback);
    }

    @Test
    public void deleteAllApiaries_deleteAllApiariesFromDbAndCache() {
        // Given 3 stub apiaries
        Apiary apiary1 = ApiaryMother.newDefaultApiary();
        Apiary apiary2 = ApiaryMother.newDefaultApiary();
        Apiary apiary3 = ApiaryMother.newDefaultApiary();
        goBeesRepository.saveApiary(apiary1, taskCallback);
        goBeesRepository.saveApiary(apiary2, taskCallback);
        goBeesRepository.saveApiary(apiary3, taskCallback);

        // When all apiaries are deleted to the GoBees repository
        goBeesRepository.deleteAllApiaries(taskCallback);

        // Verify the data source were called
        verify(goBeesLocalDataSource).deleteAllApiaries(taskCallback);

        assertThat(goBeesRepository.cachedApiaries.size(), is(0));
    }

    @Test
    public void getHiveWithRecordings_requestsAllHivesWithRecordingsFromLocalDataSource() {
        // When apiaries are requested from the tasks repository
        goBeesRepository.getHiveWithRecordings(HIVE_ID, getHiveCallback);

        // Then apiaries are loaded from the local data source
        verify(goBeesLocalDataSource).getHiveWithRecordings(anyLong(), any(GetHiveCallback.class));
    }

    /**
     * Convenience method that issues two calls to the apiaries repository.
     * So that data is cached.
     */
    private void twoGetApiariesCallsToRepository(GetApiariesCallback callback) {
        // When apiaries are requested from repository
        goBeesRepository.getApiaries(callback); // First call to API

        // Cache is empty, so LocalDataSource is called
        verify(goBeesLocalDataSource).getApiaries(apiariesCallbackArgumentCaptor.capture());

        // Trigger callback so apiaries are cached
        apiariesCallbackArgumentCaptor.getValue().onApiariesLoaded(APIARIES);

        // The second call is served from cache
        goBeesRepository.getApiaries(callback); // Second call to API
    }

    private void setApiariesNotAvailable(GoBeesDataSource dataSource) {
        verify(dataSource).getApiaries(apiariesCallbackArgumentCaptor.capture());
        apiariesCallbackArgumentCaptor.getValue().onDataNotAvailable();
    }

    private void setApiaryNotAvailable(GoBeesDataSource dataSource, long apiaryId) {
        verify(dataSource).getApiary(eq(apiaryId), apiaryCallbackArgumentCaptor.capture());
        apiaryCallbackArgumentCaptor.getValue().onDataNotAvailable();
    }
}