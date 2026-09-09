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

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/**
 * Multipart resolver berbasis Servlet dengan batas ukuran unggahan yang bisa
 * dikonfigurasi.
 *
 * <p>
 * Sampai Spring 5, batas itu dipegang CommonsMultipartResolver lewat properti
 * <code>maxUploadSize</code>, yang nilainya datang dari
 * <code>file.upload.max.size</code>. Spring 6 menghapus seluruh dukungan Commons
 * FileUpload dan hanya menyisakan {@link StandardServletMultipartResolver}, yang
 * menyerahkan pembatasan ukuran ke kontainer lewat <code>multipart-config</code>
 * di web.xml - sehingga tidak lagi bisa diatur dari berkas properti.
 * </p>
 *
 * <p>
 * Kelas ini mengembalikan kendali itu: batas kontainer di web.xml berlaku
 * sebagai pagar terluar, sedangkan batas di sini adalah yang bisa diperketat
 * lewat konfigurasi, dan diperiksa sebelum badan permintaan dibaca.
 * </p>
 */
public class JSStandardMultipartResolver extends StandardServletMultipartResolver {

    /** -1 berarti tanpa batas di lapisan ini; kontainer tetap membatasi. */
    private long maxUploadSize = -1;

    public long getMaxUploadSize() {
        return maxUploadSize;
    }

    public void setMaxUploadSize(long maxUploadSize) {
        this.maxUploadSize = maxUploadSize;
    }

    @Override
    public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws MultipartException {
        if (maxUploadSize >= 0) {
            long contentLength = request.getContentLengthLong();
            if (contentLength > maxUploadSize) {
                throw new MaxUploadSizeExceededException(maxUploadSize);
            }
        }
        return super.resolveMultipart(request);
    }
}
