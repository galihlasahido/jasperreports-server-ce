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
package com.jaspersoft.jasperserver.api.security.externalAuth.wrappers.spring;

import com.jaspersoft.jasperserver.api.JasperServerAPI;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.cas.authentication.CasAuthenticationToken;
import org.springframework.security.cas.authentication.StatelessTicketCache;
import org.springframework.util.Assert;

/**
 * Cache tiket CAS stateless yang didukung Ehcache 2.10.
 *
 * Sampai Spring Security 5.7 kelas ini hanya membungkus
 * org.springframework.security.cas.authentication.EhCacheBasedTicketCache.
 * Spring Security 6 menghapus kelas itu (bersama seluruh integrasi Ehcache 2)
 * dan hanya menyisakan SpringCacheBasedTicketCache. Karena konfigurasi CAS
 * contoh di samples/externalAuth-sample-config menyuntikkan bean Ehcache lewat
 * properti "cache", implementasinya ditulis ulang di sini dengan perilaku dan
 * nama properti yang sama, sehingga konfigurasi pengguna tidak perlu berubah.
 *
 * @author dlitvak
 * @version $Id$
 * @since 6.0
 */
@JasperServerAPI
public class JSEhCacheBasedTicketCache implements StatelessTicketCache, InitializingBean {

    private Ehcache cache;

    @Override
    public CasAuthenticationToken getByTicketId(final String serviceTicket) {
        final Element element = (serviceTicket != null) ? this.cache.get(serviceTicket) : null;
        return (element != null) ? (CasAuthenticationToken) element.getObjectValue() : null;
    }

    @Override
    public void putTicketInCache(final CasAuthenticationToken token) {
        this.cache.put(new Element(token.getCredentials().toString(), token));
    }

    @Override
    public void removeTicketFromCache(final CasAuthenticationToken token) {
        removeTicketFromCache(token.getCredentials().toString());
    }

    @Override
    public void removeTicketFromCache(final String serviceTicket) {
        this.cache.remove(serviceTicket);
    }

    public void setCache(final Ehcache cache) {
        this.cache = cache;
    }

    @Override
    public void afterPropertiesSet() {
        Assert.notNull(this.cache, "cache mandatory");
    }
}
