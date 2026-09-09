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

package com.jaspersoft.jasperserver.api.security.csrf;

import org.owasp.csrfguard.CsrfGuard;
import org.owasp.csrfguard.session.LogicalSession;
import org.owasp.csrfguard.servlet.JavaScriptServlet;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * This class delegates to org.owasp.csrfguard.servlet.JavaScriptServlet.
 * This is introduced as a workaround CsrfGuardHttpSessionListener in CSRFGuard lib
 * breaking the server clustering.
 *
 * @author dlitvak
 * @version $Id$
 */
public class JSJavaScriptServlet extends HttpServlet {
    private static final JavaScriptServlet jss = new JavaScriptServlet();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        jss.doGet(req, resp);
    }

    /*
     * Method generating and returning CSRF Token from session.
     *
     * jrs.csrfguard.js sengaja TIDAK menanamkan token di dalam berkas JavaScript
     * melainkan memintanya lewat POST ke servlet ini, lalu membaca jawaban
     * berbentuk "namaToken:nilaiToken". Itu keputusan JasperServer: berkas JS-nya
     * di-cache lama (org.owasp.csrfguard.JavascriptServlet.cacheControl setahun),
     * sehingga token yang ikut tertanam akan basi dan bocor lintas sesi.
     *
     * CSRFGuard 3 menyediakan perilaku itu lewat header FETCH-CSRF-TOKEN.
     * CSRFGuard 4 menghapusnya: doPost miliknya kini semata-mata endpoint
     * token-per-page yang menjawab JSON dan menolak permintaan kalau
     * TokenPerPage mati - dan di sini TokenPerPage memang false. Token master
     * pada CSRFGuard 4 dikirim dengan menyulih %TOKEN_NAME% / %TOKEN_VALUE% ke
     * dalam JS saat GET, yang justru bertentangan dengan alasan di atas.
     *
     * Karena itu jawabannya ditulis sendiri di sini, dalam format yang sama
     * seperti dulu, tanpa mendelegasikan ke JavaScriptServlet.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final CsrfGuard csrfGuard = CsrfGuard.getInstance();
        final LogicalSession logicalSession = csrfGuard.getLogicalSessionExtractor().extract(req);

        if (logicalSession == null) {
            // Tanpa sesi logis tidak ada yang bisa dijadikan kunci token.
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        final String sessionKey = logicalSession.getKey();
        final String tokenValue;
        synchronized (this) {
            csrfGuard.getTokenService().createMasterTokenIfAbsent(sessionKey);
            tokenValue = csrfGuard.getTokenService().getMasterToken(sessionKey);
        }

        if (tokenValue == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Jawaban ini membawa token, jadi tidak boleh ikut ter-cache seperti
        // berkas JavaScript-nya.
        resp.setContentType("text/plain; charset=utf-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Pragma", "no-cache");
        resp.getWriter().write(csrfGuard.getTokenName() + ":" + tokenValue);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        jss.init(config);
    }
}
