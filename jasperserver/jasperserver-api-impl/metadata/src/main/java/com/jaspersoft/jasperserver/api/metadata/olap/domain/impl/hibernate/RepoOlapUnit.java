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
package com.jaspersoft.jasperserver.api.metadata.olap.domain.impl.hibernate;

import com.jaspersoft.jasperserver.api.JSException;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;

import com.jaspersoft.jasperserver.api.metadata.olap.util.XMLDecoderHandler;

import com.jaspersoft.jasperserver.api.metadata.common.domain.Resource;
import com.jaspersoft.jasperserver.api.metadata.common.domain.ResourceReference;
import com.jaspersoft.jasperserver.api.metadata.common.service.ResourceFactory;
import com.jaspersoft.jasperserver.api.metadata.common.service.impl.hibernate.ReferenceResolver;
import com.jaspersoft.jasperserver.api.metadata.common.service.impl.hibernate.persistent.RepoResource;
import com.jaspersoft.jasperserver.api.metadata.olap.domain.OlapUnit;
import com.jaspersoft.jasperserver.api.metadata.olap.domain.client.OlapUnitImpl;

import javax.sql.rowset.serial.SerialBlob;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;

/**
 * @author sbirney
 *
 * @hibernate.joined-subclass table="OlapUnit"
 * @hibernate.joined-subclass-key column="id"
 */
public class RepoOlapUnit extends RepoResource {

    private RepoOlapClientConnection olapClientConn = null;
    private String mdxQuery;
    private Blob olapViewOptions;

    /**
     * @hibernate.many-to-one
     *              column="olapClientConnection"
     *
     */
    public RepoOlapClientConnection getOlapClientConnection() {
	return olapClientConn;
    }

    /**
     *
     */
    public void setOlapClientConnection(RepoOlapClientConnection r) {
	olapClientConn = r;
    }

    /**
     * @hibernate.property column="mdx_query" type="string" length="2000" not-null="true"
     */

    public String getMdxQuery() {
	return mdxQuery;
    }

    public void setMdxQuery(String s) {
	mdxQuery = s;
    }

    public Blob getOlapViewOptions() {
        return olapViewOptions;
    }

    public void setOlapViewOptions(Blob sb) {
    	olapViewOptions = sb;
    }

    protected void copyTo(Resource clientRes, ResourceFactory resourceFactory) {
        super.copyTo(clientRes, resourceFactory);

        OlapUnit view = (OlapUnit) clientRes;
        view.setOlapClientConnection(getClientReference(getOlapClientConnection(), resourceFactory));
        view.setMdxQuery(getMdxQuery());
        // do not do de-serialization here due to spring network would try to passing thing around.  The de-serialization
        // is done when it's needed (OlapModelController)
        
        // Hmmm the problem with pushing the deserialization to later is that some DBMSs and JDBC drivers, like Ingres
        // want to read a blob in a transaction, and leaving the deserialization to later will break things.
        
        // Let's try deserializing now. This will make things slower if you are reading and not really wanting to 
        // access the options ,like in repo browsing
        
        if (getOlapViewOptions() != null) {
            Object state = null;
            InputStream stream = null;

            try{
                stream = new BufferedInputStream(getOlapViewOptions().getBinaryStream());
            } catch (SQLException e) {
                throw new JSException(e);
            }

            /*
             * SECURITY FIX (OWASP A08 - Software and Data Integrity Failures, CWE-502):
             * java.beans.XMLDecoder executes whatever the document asks for
             * (constructors, static factories, arbitrary method calls), which turns
             * this blob into a remote-code-execution sink. It has been removed; the
             * SAX handler - which now resolves classes through an allow-list, see
             * XMLDecoderHandler.resolveAllowedClass - is the only decoder used.
             * The parser is also configured to reject DOCTYPE/external entities
             * (OWASP A05 - XXE).
             */
            XMLDecoderHandler handler = new XMLDecoderHandler();
            try {
                newSecureSaxParserFactory().newSAXParser().parse(stream, handler);
                state = handler.getResult();
            } catch (JSException e) {
                throw e;
            } catch (Exception e1) {
                throw new JSException("Cannot parse file state of Olap Unit", e1);
            }
            view.setOlapViewOptions(state);
        }
    }

    /**
     * SECURITY FIX (OWASP A05 - XML External Entity injection): the OLAP view
     * options blob used to be parsed with a stock SAXParserFactory, i.e. DOCTYPE
     * declarations and external entities were resolved, giving any user who can
     * store an OLAP view a local-file-read / SSRF primitive.
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

    protected void copyFrom(Resource clientRes, ReferenceResolver referenceResolver) {
        super.copyFrom(clientRes, referenceResolver);
        OlapUnit view = (OlapUnit) clientRes;
        copyOlapClientConnection(referenceResolver, view);
        setMdxQuery( view.getMdxQuery() );
        copyOlapViewOptions(view);

    }


    private void copyOlapClientConnection(ReferenceResolver referenceResolver, OlapUnit view) {
	ResourceReference conn = view.getOlapClientConnection();
	RepoOlapClientConnection repoMC
	    = (RepoOlapClientConnection) getReference(conn, RepoOlapClientConnection.class, referenceResolver);
	setOlapClientConnection(repoMC);
    }

    private void copyOlapViewOptions(OlapUnit view) {
        // if it is an instance of SerializableBlob, then no need to perform the serialization: this is for the repo admin flow
        // otherwise, serialize it to XML and set the blob
        if (view.getOlapViewOptions() != null && !(view.getOlapViewOptions() instanceof SerialBlob)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            XMLEncoder e = new XMLEncoder(new BufferedOutputStream(baos));
            e.writeObject(view.getOlapViewOptions());
            e.flush();
            e.close();
            try {
                setOlapViewOptions(new SerialBlob(baos.toByteArray()));
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
        }
    }

    protected Class getClientItf() {
	return OlapUnit.class;
    }



}
