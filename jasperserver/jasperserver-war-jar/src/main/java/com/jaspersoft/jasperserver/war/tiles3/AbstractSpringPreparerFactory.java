/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Salinan dukungan Apache Tiles 3 milik Spring Framework
 * (org.springframework.web.servlet.view.tiles3), yang DIHAPUS di Spring 6.
 *
 * JasperServer memakai Tiles 3 sebagai kerangka tata letak seluruh UI-nya:
 * setiap halaman JSP dirakit lewat definisi di WEB-INF/tiles.xml, dan
 * TilesConfigurer-lah yang membangun kontainer Tiles dan menaruhnya di
 * ServletContext. Spring 6 membuang integrasi ini seluruhnya tanpa pengganti,
 * jadi kelas-kelasnya disalin ke sini apa adanya dari Spring Framework 5.3.39
 * (Apache License 2.0) dengan dua perubahan:
 *   - paket javax.servlet dan javax.el -> jakarta.servlet dan jakarta.el
 *   - nama paket disesuaikan ke basis kode ini
 * Perilakunya sengaja dibiarkan identik supaya tata letak halaman tidak berubah.
 *
 * Sumber asli:
 *   https://github.com/spring-projects/spring-framework/tree/v5.3.39/
 *     spring-webmvc/src/main/java/org/springframework/web/servlet/view/tiles3
 */

package com.jaspersoft.jasperserver.war.tiles3;

import org.apache.tiles.TilesException;
import org.apache.tiles.preparer.ViewPreparer;
import org.apache.tiles.preparer.factory.PreparerFactory;
import org.apache.tiles.request.Request;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Abstract implementation of the Tiles {@link org.apache.tiles.preparer.factory.PreparerFactory}
 * interface, obtaining the current Spring WebApplicationContext and delegating to
 * {@link #getPreparer(String, org.springframework.web.context.WebApplicationContext)}.
 *
 * @author Juergen Hoeller
 * @since 3.2
 * @see #getPreparer(String, org.springframework.web.context.WebApplicationContext)
 * @see SimpleSpringPreparerFactory
 * @see SpringBeanPreparerFactory
 */
public abstract class AbstractSpringPreparerFactory implements PreparerFactory {

	@Override
	public ViewPreparer getPreparer(String name, Request context) {
		WebApplicationContext webApplicationContext = (WebApplicationContext) context.getContext("request").get(
				DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		if (webApplicationContext == null) {
			webApplicationContext = (WebApplicationContext) context.getContext("application").get(
					WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
			if (webApplicationContext == null) {
				throw new IllegalStateException("No WebApplicationContext found: no ContextLoaderListener registered?");
			}
		}
		return getPreparer(name, webApplicationContext);
	}

	/**
	 * Obtain a preparer instance for the given preparer name,
	 * based on the given Spring WebApplicationContext.
	 * @param name the name of the preparer
	 * @param context the current Spring WebApplicationContext
	 * @return the preparer instance
	 * @throws TilesException in case of failure
	 */
	protected abstract ViewPreparer getPreparer(String name, WebApplicationContext context) throws TilesException;

}
