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

package com.jaspersoft.jasperserver.api.security.externalAuth;

/**
 * Masks secrets before they reach a log file.
 *
 * <p>SECURITY FIX (audit finding JSP-21). The external-authentication classes used
 * to log raw SSO tickets and pre-authentication tokens at DEBUG level, e.g.
 * {@code logger.debug("SSO Token: " + ticket)}. A ticket is a bearer credential:
 * anyone who can read the log for the few seconds before it is redeemed can replay
 * it. Log files also travel further than the systems that produce them - into
 * aggregators, support bundles and bug reports.</p>
 *
 * <p>The masked form keeps just enough to correlate entries with each other
 * (length plus the first four characters) without carrying anything replayable.</p>
 */
public final class LogMasker {

    private static final int VISIBLE_PREFIX = 4;

    private LogMasker() {
    }

    /**
     * @return {@code "none"} for null, {@code "***"} for a value too short to
     *         partially reveal, otherwise {@code "abcd…(len=37)"}
     */
    public static String mask(String secret) {
        if (secret == null) {
            return "none";
        }
        if (secret.length() <= VISIBLE_PREFIX * 2) {
            return "***(len=" + secret.length() + ")";
        }
        return secret.substring(0, VISIBLE_PREFIX) + "…(len=" + secret.length() + ")";
    }

    /**
     * Convenience overload: callers hold the ticket as Object (CAS) or URI
     * (the validation endpoint), not always as String.
     */
    public static String mask(Object secret) {
        return secret == null ? "none" : mask(String.valueOf(secret));
    }

    /**
     * Masks the query string of a URL, which is where SSO implementations put the
     * ticket. The scheme, host and path stay readable because those are what makes
     * the entry useful for diagnosis.
     */
    public static String maskUrl(Object url) {
        return url == null ? "none" : maskUrl(String.valueOf(url));
    }

    public static String maskUrl(String url) {
        if (url == null) {
            return "none";
        }
        int q = url.indexOf('?');
        if (q < 0) {
            return url;
        }
        return url.substring(0, q) + "?" + mask(url.substring(q + 1));
    }
}
