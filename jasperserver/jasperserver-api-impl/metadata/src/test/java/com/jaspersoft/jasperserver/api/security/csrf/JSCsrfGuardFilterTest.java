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

import org.apache.http.protocol.HTTP;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.owasp.csrfguard.CsrfGuard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Anton Fomin
 *
 * jrs.csrfguard.properties:
 * * only POST,PUT,DELETE requests are scanned for CSRF
 */
@RunWith(MockitoJUnitRunner.class)
public class JSCsrfGuardFilterTest {
    private static final String TEST_SESSION_ID = "test-session-id";
    /**
     * CSRFGuard 3 membandingkan token request dengan atribut HttpSession,
     * sehingga nilai apa pun bisa dipasang di kedua sisi. CSRFGuard 4 menyimpan
     * token di TokenHolder yang dikunci sesi logis, jadi tes harus memakai token
     * yang benar-benar dibuat CSRFGuard, bukan konstanta karangan.
     */
    private static String CSRF_TOKEN_VALUE;
    public static final String AJAX_HEADER = "X-Requested-With";
    private static JSCsrfGuardFilter filter = new JSCsrfGuardFilter();

    @Mock
    HttpSession sessionMock;
    @Mock
    HttpServletRequest requestMock;
    @Mock
    HttpServletResponse responseMock;
    @Mock
    FilterChain filterChainMock;

    @BeforeClass
    public static void setup() throws Exception {
        //load CsrfGuard properties
        InputStream is = JSCsrfGuardFilterTest.class.getClassLoader().getResourceAsStream("jrs.csrfguard.test.properties");
        Properties properties = new Properties();
        properties.load(is);
        CsrfGuard.load(properties);

        filter.setProtectedUserAgentRegexs(Arrays.asList("Mozilla/.*","Opera/.*"));
    }

    @Before
    public void setupTest() {
        reset(requestMock);
        // Buat master token lewat API CSRFGuard 4 dan pakai nilainya di request.
        CsrfGuard csrfGuard = CsrfGuard.getInstance();
        csrfGuard.getTokenService().createMasterTokenIfAbsent(TEST_SESSION_ID);
        CSRF_TOKEN_VALUE = csrfGuard.getTokenService().getMasterToken(TEST_SESSION_ID);
        // SessionTokenKeyExtractor memanggil getSession(boolean) -- bukan getSession()
        // tanpa argumen. Tanpa stub yang cocok, extract() mengembalikan null,
        // CsrfGuardFilter masuk ke handleNoSession dan meloloskan request
        // (ValidateWhenNoSessionExists=false), sehingga tes "tanpa token harus
        // ditolak" lulus karena alasan yang salah.
        when(requestMock.getSession(anyBoolean())).thenReturn(sessionMock);
        // CSRFGuard 4 mengambil kunci sesi logis lewat SessionTokenKeyExtractor,
        // yang memakai id HttpSession. Tanpa id, penyimpanan token melempar
        // NullPointerException ("key is null") sebelum filter sempat dijalankan.
        when(sessionMock.getId()).thenReturn(TEST_SESSION_ID);
        when(requestMock.getRequestURL()).thenReturn(new StringBuffer("testCSRF.html"));
        when(requestMock.getRequestURI()).thenReturn("testCSRF.html");
    }

    @Test
    public void testRequestWithoutUserAgentPasses() throws Exception {
//        when(requestMock.getMethod()).thenReturn("POST");

        Method doFilter = JSCsrfGuardFilter.class.getDeclaredMethod("doFilter", ServletRequest.class, ServletResponse.class, FilterChain.class);
        doFilter.invoke(filter, requestMock, responseMock, filterChainMock);

        verify(filterChainMock).doFilter(requestMock, responseMock);
    }

    @Test
    public void testRequestWithUnknownUserAgentPasses() throws Exception {
        when(requestMock.getHeader(HTTP.USER_AGENT)).thenReturn("Unknown/1.2.3");
//        when(requestMock.getMethod()).thenReturn("POST");

        Method doFilter = JSCsrfGuardFilter.class.getDeclaredMethod("doFilter", ServletRequest.class, ServletResponse.class, FilterChain.class);
        doFilter.invoke(filter, requestMock, responseMock, filterChainMock);

        verify(filterChainMock).doFilter(requestMock, responseMock);
    }

    @Test
    public void testGetRequestWithoutUserAgentPasses() throws Exception {
        when(requestMock.getHeader(HTTP.USER_AGENT)).thenReturn("Mozilla/1.2.3");
        when(requestMock.getMethod()).thenReturn("GET");

        Method doFilter = JSCsrfGuardFilter.class.getDeclaredMethod("doFilter", ServletRequest.class, ServletResponse.class, FilterChain.class);
        doFilter.invoke(filter, requestMock, responseMock, filterChainMock);

        // 2nd argument is null for 'any' object invokation.  This is because org.owasp.csrfguard.CsrfGuardFilter.doFilter() wraps response in
        // org.owasp.csrfguard.http.InterceptRedirectResponse
        verify(filterChainMock).doFilter(eq(requestMock), any());
    }

    @Test
    public void testNonAjaxRequestWithoutTokenFails() throws Exception {
        when(requestMock.getHeader(HTTP.USER_AGENT)).thenReturn("Mozilla/1.2.3");
        when(requestMock.getMethod()).thenReturn("POST");

        Method doFilter = JSCsrfGuardFilter.class.getDeclaredMethod("doFilter", ServletRequest.class, ServletResponse.class, FilterChain.class);
        doFilter.invoke(filter, requestMock, responseMock, filterChainMock);

        verify(filterChainMock, never()).doFilter(requestMock, responseMock);
    }

    @Test
    public void testNonAjaxRequestWithTokenPasses() throws Exception {
        when(requestMock.getHeader(HTTP.USER_AGENT)).thenReturn("Mozilla/1.2.3");
        when(requestMock.getMethod()).thenReturn("POST");
        when(requestMock.getParameter(CsrfGuard.getInstance().getTokenName())).thenReturn(CSRF_TOKEN_VALUE);

        Method doFilter = JSCsrfGuardFilter.class.getDeclaredMethod("doFilter", ServletRequest.class, ServletResponse.class, FilterChain.class);
        doFilter.invoke(filter, requestMock, responseMock, filterChainMock);

        // 2nd argument is null for 'any' object invokation.  This is because org.owasp.csrfguard.CsrfGuardFilter.doFilter() wraps response in
        // org.owasp.csrfguard.http.InterceptRedirectResponse
        verify(filterChainMock).doFilter(eq(requestMock), any());
    }

    @Test
    public void testAjaxRequestWithoutTokenFails() throws Exception {
        when(requestMock.getHeader(HTTP.USER_AGENT)).thenReturn("Mozilla/1.2.3");
        when(requestMock.getMethod()).thenReturn("POST");

        Method doFilter = JSCsrfGuardFilter.class.getDeclaredMethod("doFilter", ServletRequest.class, ServletResponse.class, FilterChain.class);
        doFilter.invoke(filter, requestMock, responseMock, filterChainMock);

        verify(filterChainMock, never()).doFilter(requestMock, responseMock);
    }

    @Test
    public void testAjaxRequestWithTokenPasses() throws Exception {
        when(requestMock.getHeader(HTTP.USER_AGENT)).thenReturn("Mozilla/1.2.3");
        when(requestMock.getMethod()).thenReturn("POST");
        when(requestMock.getHeader(CsrfGuard.getInstance().getTokenName())).thenReturn(CSRF_TOKEN_VALUE);
        // CSRFGuard 4 menelusuri daftar nama header saat mencari token, bukan
        // hanya memanggil getHeader dengan nama token. Tanpa getHeaderNames yang
        // memuat nama itu, token di header tidak pernah terlihat dan request
        // ditolak dengan "Required Token is missing from the Request".
        // CSRFGuard 4 mendeteksi Ajax lewat getHeaders() (jamak, Enumeration) dan
        // membandingkan persis dengan "XMLHttpRequest". CSRFGuard 3 memakai
        // getHeader() tunggal, sehingga nilai "XmlHttpRequest" dulu cukup.
        // Kalau request tidak dikenali Ajax, token di header tidak pernah dibaca
        // dan request ditolak dengan "Required Token is missing from the Request".
        when(requestMock.getHeaders(AJAX_HEADER)).thenReturn(
                java.util.Collections.enumeration(
                        java.util.Collections.singletonList("XMLHttpRequest")));

        Method doFilter = JSCsrfGuardFilter.class.getDeclaredMethod("doFilter", ServletRequest.class, ServletResponse.class, FilterChain.class);
        doFilter.invoke(filter, requestMock, responseMock, filterChainMock);

        // 2nd argument is null for 'any' object invokation.  This is because org.owasp.csrfguard.CsrfGuardFilter.doFilter() wraps response in
        // org.owasp.csrfguard.http.InterceptRedirectResponse
        verify(filterChainMock).doFilter(eq(requestMock), any());
    }

}
