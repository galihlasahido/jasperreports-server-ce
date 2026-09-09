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
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.config.CacheConfiguration;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Pengganti com.jaspersoft.jasperserver.api.common.cache.EhCacheFactoryBean, yang dihapus
 * di Spring 6 bersama seluruh dukungan Ehcache 2.
 *
 * Konfigurasi di sini memakai cacheManager, cacheName, dan untuk satu cache
 * (inputControlCache) juga timeToIdleSeconds/timeToLiveSeconds. Cache yang sudah
 * dideklarasikan di ehcache.xml dipakai apa adanya; yang belum dibuat di sini.
 * Kalau cacheName tidak diset, nama bean yang dipakai — sama seperti perilaku
 * Spring.
 */
public class EhCacheFactoryBean implements FactoryBean<Ehcache>, InitializingBean, BeanNameAware {

    private CacheManager cacheManager;
    private String cacheName;
    private String beanName;
    private Long timeToIdleSeconds;
    private Long timeToLiveSeconds;

    private Ehcache cache;

    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    /** Berapa lama entri boleh menganggur sebelum kedaluwarsa, dalam detik. */
    public void setTimeToIdleSeconds(long timeToIdleSeconds) {
        this.timeToIdleSeconds = timeToIdleSeconds;
    }

    /** Umur maksimum sebuah entri, dalam detik. */
    public void setTimeToLiveSeconds(long timeToLiveSeconds) {
        this.timeToLiveSeconds = timeToLiveSeconds;
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    public void afterPropertiesSet() {
        if (this.cacheManager == null) {
            throw new IllegalStateException("Properti 'cacheManager' wajib diisi");
        }
        String name = (this.cacheName != null ? this.cacheName : this.beanName);
        if (!this.cacheManager.cacheExists(name)) {
            // Sama seperti Spring: kalau cache belum ada di ehcache.xml, buat
            // dari template default milik CacheManager.
            this.cacheManager.addCache(name);
        }
        this.cache = this.cacheManager.getEhcache(name);

        // Ehcache mengizinkan kedua nilai ini diubah saat berjalan, jadi
        // penerapannya sama baik cache-nya baru dibuat di atas maupun sudah
        // dideklarasikan di ehcache.xml.
        if (this.timeToIdleSeconds != null || this.timeToLiveSeconds != null) {
            CacheConfiguration config = this.cache.getCacheConfiguration();
            if (this.timeToIdleSeconds != null) {
                config.setTimeToIdleSeconds(this.timeToIdleSeconds);
            }
            if (this.timeToLiveSeconds != null) {
                config.setTimeToLiveSeconds(this.timeToLiveSeconds);
            }
        }
    }

    @Override
    public Ehcache getObject() {
        return this.cache;
    }

    @Override
    public Class<?> getObjectType() {
        return (this.cache != null ? this.cache.getClass() : Ehcache.class);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
