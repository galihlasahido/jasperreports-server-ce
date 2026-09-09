/*
 * Copyright (C) 2005-2023. Cloud Software Group, Inc. All Rights Reserved.
 * http://www.jaspersoft.com.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jaspersoft.jasperserver.api.common.cache;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Pengganti com.jaspersoft.jasperserver.api.common.cache.EhCacheManagerFactoryBean, yang
 * dihapus di Spring 6 bersama seluruh dukungan Ehcache 2.
 *
 * Hanya properti yang benar-benar dipakai konfigurasi Spring di sini yang
 * disediakan: configLocation, shared, acceptExisting dan cacheManagerName.
 * Perilakunya sengaja dibuat sama dengan milik Spring supaya definisi bean
 * yang ada tidak perlu diubah selain nama kelasnya.
 */
public class EhCacheManagerFactoryBean
        implements FactoryBean<CacheManager>, InitializingBean, DisposableBean {

    private Resource configLocation;
    private String cacheManagerName;
    private boolean shared = false;
    private boolean acceptExisting = false;

    private CacheManager cacheManager;
    /** Hanya CacheManager yang kita buat sendiri yang boleh kita matikan. */
    private boolean locallyManaged = true;

    public void setConfigLocation(Resource configLocation) {
        this.configLocation = configLocation;
    }

    public void setCacheManagerName(String cacheManagerName) {
        this.cacheManagerName = cacheManagerName;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public void setAcceptExisting(boolean acceptExisting) {
        this.acceptExisting = acceptExisting;
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        Configuration configuration;
        if (configLocation != null) {
            try (InputStream is = configLocation.getInputStream()) {
                configuration = ConfigurationFactory.parseConfiguration(is);
            }
        } else {
            configuration = ConfigurationFactory.parseConfiguration();
        }
        if (cacheManagerName != null) {
            configuration.setName(cacheManagerName);
        }

        if (shared || acceptExisting) {
            String name = configuration.getName();
            CacheManager existing = (name != null) ? CacheManager.getCacheManager(name) : null;
            if (existing != null) {
                this.cacheManager = existing;
                this.locallyManaged = false;
                return;
            }
        }
        this.cacheManager = shared
                ? CacheManager.create(configuration)
                : new CacheManager(configuration);
    }

    @Override
    public CacheManager getObject() {
        return this.cacheManager;
    }

    @Override
    public Class<?> getObjectType() {
        return (this.cacheManager != null ? this.cacheManager.getClass() : CacheManager.class);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void destroy() {
        if (this.cacheManager != null && this.locallyManaged) {
            this.cacheManager.shutdown();
        }
    }
}
