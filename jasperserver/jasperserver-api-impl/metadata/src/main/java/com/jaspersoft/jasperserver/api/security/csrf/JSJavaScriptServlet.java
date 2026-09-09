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
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        synchronized (this) {
            // CSRFGuard 4 memindahkan pengelolaan token dari CsrfGuard ke
            // TokenService, dan sesi diwakili LogicalSession (bukan HttpSession
            // langsung). Pasangan getTokenValue/updateToken yang lama —
            // "ambil token, kalau kosong buat" — kini dinyatakan satu metode:
            // createMasterTokenIfAbsent.
            CsrfGuard csrfGuard = CsrfGuard.getInstance();
            LogicalSession logicalSession = csrfGuard.getLogicalSessionExtractor().extract(req);
            if (logicalSession != null) {
                csrfGuard.getTokenService().createMasterTokenIfAbsent(logicalSession.getKey());
            }
        }
        jss.doPost(req, resp);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        jss.init(config);
    }
}
