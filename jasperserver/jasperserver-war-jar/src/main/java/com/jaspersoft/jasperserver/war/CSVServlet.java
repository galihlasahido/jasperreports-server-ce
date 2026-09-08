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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.sql.Statement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.DriverManager;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.tonbeller.jpivot.core.Model;
import com.tonbeller.jpivot.olap.model.OlapModel;
import com.tonbeller.jpivot.olap.model.OlapModelDecorator;
import com.tonbeller.jpivot.mondrian.MondrianModel;
import com.tonbeller.jpivot.mondrian.MondrianDrillThroughTableModel;
import com.tonbeller.wcf.table.EditableTableComponent;


/**
 * @author sbirney
 * @revision $Id: CSVServlet.java 8441 2007-05-30 18:55:04Z sbirney $
 */

public class CSVServlet extends HttpServlet {

    /*
     * BUGFIX (audit BUG-01, BUG-02, JSP-15).
     *
     * The previous implementation called getDrillThroughSQL(req) and
     * getConnection(req), both of which dereference getDrillThroughModel(req)
     * unconditionally. That method returns null on every path that is not a live
     * Mondrian drill-through, so requesting this URL without an active OLAP
     * session threw a NullPointerException, which was then swallowed by
     * catch (Exception) { e.printStackTrace(); } and answered with an empty
     * 200 OK carrying a CSV content type.
     *
     * Now: the model is resolved once, its absence is reported as 409 Conflict,
     * the JDBC resources are closed with try-with-resources, and failures go to
     * the configured logger instead of the container's stdout.
     */
    public void service(HttpServletRequest req,
			HttpServletResponse resp)
	throws ServletException, IOException
    {
	MondrianDrillThroughTableModel model = getDrillThroughModel(req);
	if (model == null) {
	    log.warn("Drill-through CSV requested but no drill-through result is present in the session");
	    resp.sendError(HttpServletResponse.SC_CONFLICT,
			   "No drill-through result is available for this session.");
	    return;
	}

	String sql = model.getSql();
	if (sql == null || sql.trim().isEmpty()) {
	    log.warn("Drill-through model carries no SQL statement");
	    resp.sendError(HttpServletResponse.SC_CONFLICT,
			   "The drill-through result carries no query.");
	    return;
	}

	resp.setContentType(MIME_TYPE);
	resp.setHeader("Pragma", "");
	resp.setHeader("Cache-Control", "no-store");

	try (Connection conn = getConnection(model)) {
	    printQuery(sql, conn, resp.getWriter());
	} catch (SQLException e) {
	    log.error("Drill-through CSV export failed", e);
	    // The failure can also come from Connection.close() after the body has
	    // already gone out; sendError() would then throw IllegalStateException.
	    if (!resp.isCommitted()) {
		resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
			       "Could not export the drill-through result.");
	    }
	}
    }

    private static final Log log = LogFactory.getLog(CSVServlet.class);

    protected static final String MIME_TYPE = "text/comma-separated-values";
    protected static final String HTML_TYPE = "text/html";
    protected static final String SEP = ",";
    protected static final String NULL_VALUE = "";
    protected static final String NEWLINE = "\r\n";

    private MondrianDrillThroughTableModel
	getDrillThroughModel(HttpServletRequest req)
    {
	HttpSession session = req.getSession(false);
	if (session == null) {
	    return null;
	}
	// BUGFIX (BUG-01): the olapModel attribute may be absent, and when present it
	// is not guaranteed to be an OlapModelDecorator. The unguarded cast below used
	// to throw NullPointerException / ClassCastException instead of simply saying
	// "there is nothing to drill through".
	Object olapModel = session.getAttribute("olapModel");
	if (!(olapModel instanceof OlapModelDecorator)) {
	    return null;
	}
	Model mdl = ((OlapModelDecorator) olapModel).getRootModel();
	String currentView = (String) session.getAttribute("currentView");
	// only MondrianModel supports Drillthru
	if (mdl instanceof MondrianModel && currentView != null) {
	    Object et = session.getAttribute(currentView + ".drillthroughtable");
	    if (et instanceof EditableTableComponent) {
		Object tableModel = ((EditableTableComponent) et).getModel();
		if (tableModel instanceof MondrianDrillThroughTableModel) {
		    return (MondrianDrillThroughTableModel) tableModel;
		}
	    }
	}
	return null;
    }

    // BUGFIX (BUG-01): takes the already-resolved model instead of looking it up
    // again and dereferencing a possible null.
    private Connection getConnection(MondrianDrillThroughTableModel model)
	throws SQLException
    {
	String dataSourceName = model.getDataSourceName();
	if (dataSourceName == null) {
	    return DriverManager.getConnection(model.getJdbcUrl(),
					       model.getJdbcUser(),
					       model.getJdbcPassword());
	}
	return getDataSource(dataSourceName).getConnection();
    }

    // BUGFIX (BUG-01, JSP-15): used to log the JNDI failure and return null, so the
    // caller failed later with a NullPointerException that hid the real cause.
    private DataSource getDataSource(String dataSourceName) throws SQLException {
	try {
	    DataSource ds = (DataSource) getJndiContext().lookup(dataSourceName);
	    if (ds == null) {
		throw new SQLException("JNDI name '" + dataSourceName + "' resolved to null");
	    }
	    return ds;
	} catch (NamingException e) {
	    throw new SQLException("Cannot look up drill-through data source '"
				   + dataSourceName + "'", e);
	}
    }

    private Context jndiContext;
    private Context getJndiContext() throws NamingException {
	if (jndiContext == null) {
	    jndiContext = new InitialContext();
	}
	return jndiContext;
    }

    /*
      private Connection getConnectionFromOlapUnit(HttpServletRequest req)
      throws SQLException
      {
      HttpSession session = context.getRequest().getSession();
      OlapUnit olapUnit = (OlapUnit) session.getAttribute("olapUnit");

      }
    */

    /*
     * BUGFIX (BUG-02, JSP-15): the Statement and ResultSet were never closed when
     * createStatement()/executeQuery() threw, and only the Connection was released
     * in the finally block. The Connection is now owned by the caller's
     * try-with-resources, and the Statement/ResultSet by this one. Exceptions
     * propagate to the caller so the response can carry a real status code.
     */
    private void printQuery(String sqlQuery,
			    Connection conn,
			    PrintWriter out)
	throws SQLException
    {
	if (log.isDebugEnabled()) {
	    log.debug("drill-through SQL = " + sqlQuery);
	}
	try (Statement s = conn.createStatement();
	     ResultSet rs = s.executeQuery(sqlQuery)) {
	    printCSV(rs, out);
	} catch (SQLException e) {
	    throw e;
	} catch (Exception e) {
	    throw new SQLException("Failed to write the drill-through result", e);
	}
    }

    private void printCSV(ResultSet rs,
			  PrintWriter out)
	throws Exception
    {
	ResultSetMetaData md = rs.getMetaData();
	int numCols = md.getColumnCount();

	// print column headers
	for (int i=1; i<numCols; i++) {
	    out.write(quoteString(md.getColumnName(i)));
	    out.write(SEP);
	}
	out.write(quoteString(md.getColumnName(numCols)));
	out.write(NEWLINE);

	// print row data
	while (rs.next()) {
	    for (int i=1; i<numCols; i++) {
		out.write(quoteString("" + rs.getObject(i)));
		out.write(SEP);
	    }
	    out.write(quoteString("" + rs.getObject(numCols)));
	    out.write(NEWLINE);
	}
    }

    private String quoteString(String s) {
	s = s.replaceAll("\"", "\"\"");
	s = "\"" + s + "\"";
	return s;
    }

}
