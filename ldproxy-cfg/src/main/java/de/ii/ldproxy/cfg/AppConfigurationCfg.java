/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import de.ii.xtraplatform.base.domain.AppConfiguration;
import de.ii.xtraplatform.base.domain.AuthConfiguration;
import de.ii.xtraplatform.base.domain.BackgroundTasksConfiguration;
import de.ii.xtraplatform.base.domain.ImmutableAuthConfiguration;
import de.ii.xtraplatform.base.domain.ImmutableModulesConfiguration;
import de.ii.xtraplatform.base.domain.JobsConfiguration;
import de.ii.xtraplatform.base.domain.LoggingConfiguration;
import de.ii.xtraplatform.base.domain.MetricsConfiguration;
import de.ii.xtraplatform.base.domain.ModulesConfiguration;
import de.ii.xtraplatform.base.domain.ModulesConfiguration.Startup;
import de.ii.xtraplatform.base.domain.RedisConfiguration;
import de.ii.xtraplatform.base.domain.ServerConfiguration;
import de.ii.xtraplatform.base.domain.StoreConfiguration;
import io.dropwizard.client.HttpClientConfiguration;
import java.util.Map;

class AppConfigurationCfg extends AppConfiguration {

  private final StoreConfiguration storeConfiguration;

  public AppConfigurationCfg(StoreConfiguration storeConfiguration) {
    this.storeConfiguration = storeConfiguration;
  }

  @Override
  public ServerConfiguration getServerFactory() {
    return null;
  }

  @Override
  public LoggingConfiguration getLoggingFactory() {
    return null;
  }

  @Override
  public HttpClientConfiguration getHttpClient() {
    return new HttpClientConfiguration();
  }

  @Override
  public MetricsConfiguration getMetricsFactory() {
    return null;
  }

  @Override
  public StoreConfiguration getStore() {
    return storeConfiguration;
  }

  @Override
  public AuthConfiguration getAuth() {
    return new ImmutableAuthConfiguration.Builder().build();
  }

  @Override
  public ModulesConfiguration getModules() {
    return new ImmutableModulesConfiguration.Builder().startup(Startup.SYNC).build();
  }

  @Override
  public BackgroundTasksConfiguration getBackgroundTasks() {
    return null;
  }

  @Override
  public JobsConfiguration getJobs() {
    return null;
  }

  @Override
  public Map<String, Object> getSubstitutions() {
    return Map.of();
  }

  @Override
  public RedisConfiguration getRedis() {
    return null;
  }
}
