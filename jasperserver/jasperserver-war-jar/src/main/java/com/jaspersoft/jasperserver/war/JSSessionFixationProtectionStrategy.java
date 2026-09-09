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
package com.jaspersoft.jasperserver.war;

import com.jaspersoft.jasperserver.api.JasperServerAPI;
import org.owasp.csrfguard.CsrfGuard;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

/**
 * Wrapper class for org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy
 * @author dlitvak
 * @version $Id$
 * @since 6.0
 */
@JasperServerAPI
public class JSSessionFixationProtectionStrategy extends SessionFixationProtectionStrategy {
    /**
     * Remove any attributes from the map that should not be migrated from unauth. session to auth one.
     * * CSRF token
     * * XSS nonce
     *
     * @param session
     * @return
     */
    @Override
    protected Map<String, Object> extractAttributes(HttpSession session) {
        Map<String, Object> attrMap = super.extractAttributes(session);
        // CSRFGuard 3 menyimpan token sebagai atribut HttpSession, dan namanya
        // dibaca lewat CsrfGuard.getSessionKey(); atribut itu harus dibuang agar
        // token pra-otentikasi tidak ikut pindah ke sesi yang sudah terotentikasi.
        // CSRFGuard 4 menghapus metode itu karena tokennya tidak lagi disimpan di
        // atribut sesi melainkan di TokenService, dikunci oleh sesi logis - yaitu
        // id HttpSession. Begitu strategi ini mengganti sesi, id-nya berubah dan
        // token lama tidak lagi bisa ditemukan, jadi perlindungan yang sama
        // didapat tanpa ada yang perlu dibuang di sini.
        attrMap.remove(SessionXssNonceSetterFilter.XSS_NONCE_ATTRIB_NAME);
        return attrMap;
    }
}
