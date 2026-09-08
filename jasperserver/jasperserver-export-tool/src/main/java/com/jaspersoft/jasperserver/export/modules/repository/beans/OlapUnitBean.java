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
package com.jaspersoft.jasperserver.export.modules.repository.beans;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.jaspersoft.jasperserver.api.JSException;
import com.jaspersoft.jasperserver.api.metadata.common.domain.Resource;
import com.jaspersoft.jasperserver.api.metadata.olap.domain.OlapUnit;
import com.jaspersoft.jasperserver.export.modules.repository.ResourceExportHandler;
import com.jaspersoft.jasperserver.export.modules.repository.ResourceImportHandler;
import com.jaspersoft.jasperserver.api.metadata.olap.util.XMLDecoderHandler;

import javax.xml.parsers.SAXParserFactory;

/**
 * @author tkavanagh
 * @version $Id$
 */
public class OlapUnitBean extends ResourceBean {
	
	public static final String DATA_PROVIDER_VIEW_OPTIONS = "olapUnitViewOptions";
	
	private String mdxQuery;
	private ResourceReferenceBean olapClientConnection;
	private String olapViewOptionsDataFile;
	
	protected void additionalCopyFrom(Resource res, ResourceExportHandler exportHandler) {
		OlapUnit unit = (OlapUnit) res;
		setMdxQuery(unit.getMdxQuery());
		setOlapClientConnection(exportHandler.handleReference(unit.getOlapClientConnection()));
		setOlapViewOptionsDataFile(exportHandler.handleData(unit, DATA_PROVIDER_VIEW_OPTIONS));
	}

	protected void additionalCopyTo(Resource res, ResourceImportHandler importHandler) {
		OlapUnit unit = (OlapUnit) res;
		unit.setMdxQuery(getMdxQuery());
		unit.setOlapClientConnection(importHandler.handleReference(getOlapClientConnection()));
		unit.setOlapViewOptions(constructViewOptions(importHandler));
	}

	protected Object constructViewOptions(ResourceImportHandler importHandler) {
		Object options;
		if (olapViewOptionsDataFile == null) {
			options = null;
		} else {
			byte[] viewOptionsData = importHandler.handleData(this, 
					olapViewOptionsDataFile, DATA_PROVIDER_VIEW_OPTIONS);
            InputStream stream = new ByteArrayInputStream(viewOptionsData);
            /*
             * SECURITY FIX (OWASP A08 - Software and Data Integrity Failures, CWE-502):
             * this data comes straight out of an uploaded import archive. Decoding it
             * with java.beans.XMLDecoder let the archive execute arbitrary constructors
             * and methods - remote code execution for anyone who can run an import.
             * XMLDecoder is gone; the SAX handler resolves classes through an
             * allow-list (XMLDecoderHandler.resolveAllowedClass) and the parser now
             * rejects DOCTYPE/external entities (OWASP A05 - XXE).
             */
            XMLDecoderHandler handler = new XMLDecoderHandler();
            try {
                newSecureSaxParserFactory().newSAXParser().parse(stream, handler);
                options = handler.getResult();
            } catch (JSException e) {
                throw e;
            } catch (Exception e1) {
                throw new JSException("Cannot parse file " + olapViewOptionsDataFile, e1);
            }
		}
		return options;
	}

	/**
	 * SECURITY FIX (OWASP A05 - XML External Entity injection): harden the parser
	 * used for the untrusted olapViewOptions document that ships inside an
	 * import archive.
	 */
	private static SAXParserFactory newSecureSaxParserFactory() throws javax.xml.parsers.ParserConfigurationException,
			org.xml.sax.SAXException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setXIncludeAware(false);
		return factory;
	}

	public String getMdxQuery() {
		return mdxQuery;
	}
	
	public void setMdxQuery(String mdxQuery) {
		this.mdxQuery = mdxQuery;
	}
	
	public ResourceReferenceBean getOlapClientConnection() {
		return olapClientConnection;
	}
	
	public void setOlapClientConnection(ResourceReferenceBean olapClientConnection) {
		this.olapClientConnection = olapClientConnection;
	}

	public String getOlapViewOptionsDataFile() {
		return olapViewOptionsDataFile;
	}

	public void setOlapViewOptionsDataFile(String olapViewOptionsDataFile) {
		this.olapViewOptionsDataFile = olapViewOptionsDataFile;
	}
}
