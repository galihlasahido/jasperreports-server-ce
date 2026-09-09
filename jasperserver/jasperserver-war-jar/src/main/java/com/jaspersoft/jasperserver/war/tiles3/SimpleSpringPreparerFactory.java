/*
 * Copyright 2002-2018 the original author or authors.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tiles.TilesException;
import org.apache.tiles.preparer.PreparerException;
import org.apache.tiles.preparer.ViewPreparer;
import org.apache.tiles.preparer.factory.NoSuchPreparerException;

import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;

/**
 * Tiles {@link org.apache.tiles.preparer.factory.PreparerFactory} implementation
 * that expects preparer class names and builds preparer instances for those,
 * creating them through the Spring ApplicationContext in order to apply
 * Spring container callbacks and configured Spring BeanPostProcessors.
 *
 * @author Juergen Hoeller
 * @since 3.2
 * @see SpringBeanPreparerFactory
 */
public class SimpleSpringPreparerFactory extends AbstractSpringPreparerFactory {

	/** Cache of shared ViewPreparer instances: bean name -> bean instance. */
	private final Map<String, ViewPreparer> sharedPreparers = new ConcurrentHashMap<>(16);


	@Override
	protected ViewPreparer getPreparer(String name, WebApplicationContext context) throws TilesException {
		// Quick check on the concurrent map first, with minimal locking.
		ViewPreparer preparer = this.sharedPreparers.get(name);
		if (preparer == null) {
			synchronized (this.sharedPreparers) {
				preparer = this.sharedPreparers.get(name);
				if (preparer == null) {
					try {
						Class<?> beanClass = ClassUtils.forName(name, context.getClassLoader());
						if (!ViewPreparer.class.isAssignableFrom(beanClass)) {
							throw new PreparerException(
									"Invalid preparer class [" + name + "]: does not implement ViewPreparer interface");
						}
						preparer = (ViewPreparer) context.getAutowireCapableBeanFactory().createBean(beanClass);
						this.sharedPreparers.put(name, preparer);
					}
					catch (ClassNotFoundException ex) {
						throw new NoSuchPreparerException("Preparer class [" + name + "] not found", ex);
					}
				}
			}
		}
		return preparer;
	}

}
