/*
 * Copyright (C) 2005-2023. Cloud Software Group, Inc. All Rights Reserved.
 * http://www.jaspersoft.com.
 *
 * Unless you have purchased a commercial license agreement from Jaspersoft,
 * the following license terms apply:
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
import net.sf.ehcache.config.DiskStoreConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EhCacheFactoryBeanTest {

    private EhCacheFactoryBean create(CacheManager manager, String name) {
        EhCacheFactoryBean bean = new EhCacheFactoryBean();
        bean.setCacheManager(manager);
        bean.setCacheName(name);
        bean.afterPropertiesSet();
        return bean;
    }

    /**
     * The import/export tool deliberately runs with an empty ehcache configuration (no
     * defaultCache, bug 35993). CacheManager.addCache(String) refuses that, so the factory
     * has to build the cache from an explicit configuration like Spring's own factory does.
     */
    @Test
    public void cacheAbsentAndNoDefaultCache_isCreatedWithSpringDefaults() {
        Configuration configuration = new Configuration().name("test-empty");
        configuration.addDiskStore(new DiskStoreConfiguration().path(System.getProperty("java.io.tmpdir")));
        CacheManager manager = CacheManager.newInstance(configuration);
        try {
            EhCacheFactoryBean bean = create(manager, "snapshotMetadata");
            assertNotNull(bean.getObject());
            assertEquals(10000, bean.getObject().getCacheConfiguration().getMaxEntriesLocalHeap());
            assertEquals(120, bean.getObject().getCacheConfiguration().getTimeToLiveSeconds());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void explicitTimeouts_overrideTheDefaults() {
        Configuration configuration = new Configuration().name("test-timeouts");
        configuration.addDiskStore(new DiskStoreConfiguration().path(System.getProperty("java.io.tmpdir")));
        CacheManager manager = CacheManager.newInstance(configuration);
        try {
            EhCacheFactoryBean bean = new EhCacheFactoryBean();
            bean.setCacheManager(manager);
            bean.setCacheName("withTimeouts");
            bean.setTimeToLiveSeconds(7200);
            bean.setTimeToIdleSeconds(600);
            bean.afterPropertiesSet();
            assertEquals(7200, bean.getObject().getCacheConfiguration().getTimeToLiveSeconds());
            assertEquals(600, bean.getObject().getCacheConfiguration().getTimeToIdleSeconds());
        } finally {
            manager.shutdown();
        }
    }
}
