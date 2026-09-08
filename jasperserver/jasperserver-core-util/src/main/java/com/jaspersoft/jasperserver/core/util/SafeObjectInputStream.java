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

package com.jaspersoft.jasperserver.core.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An {@link ObjectInputStream} that refuses to resolve classes belonging to the
 * well-known Java deserialisation gadget chains.
 *
 * <p>SECURITY FIX (audit finding JSP-19). JasperReports Server deserialises Java
 * objects in several places - report data snapshots, cached session state, Quartz
 * job data. All of that data is written by the server itself, so there is no direct
 * external entry point; this class is defence in depth, for the case where one of
 * those stores is reachable another way (a compromised database, a restored backup,
 * a crafted import).</p>
 *
 * <p>It deliberately implements a <em>deny</em>-list rather than an allow-list.
 * The payloads involved are open-ended - a data snapshot legitimately contains
 * whatever types a report's query produced - so an allow-list would break real
 * data. A deny-list cannot make that promise, and is not a substitute for the
 * JVM-wide {@code -Djdk.serialFilter} that this deployment also sets; it closes the
 * published gadget entry points and nothing more.</p>
 *
 * <p>Written against Java 8: {@code java.io.ObjectInputFilter} only exists from
 * Java 9 onwards, and this codebase compiles to 1.8 bytecode.</p>
 */
public class SafeObjectInputStream extends ObjectInputStream {

    private static final Log log = LogFactory.getLog(SafeObjectInputStream.class);

    /**
     * Class-name prefixes that no legitimate JasperReports Server payload contains,
     * and that every published gadget chain needs.
     */
    private static final List<String> BLOCKED_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            // process execution
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.ProcessImpl",
            "java.lang.UNIXProcess",
            // JNDI / RMI lookup gadgets
            "javax.naming.InitialContext",
            "javax.naming.spi.",
            "com.sun.jndi.",
            "java.rmi.registry.",
            "java.rmi.server.",
            "com.sun.rowset.JdbcRowSetImpl",
            // reflection / method-handle gadgets
            "sun.reflect.annotation.AnnotationInvocationHandler",
            "java.beans.EventHandler",
            "com.sun.org.apache.xalan.",
            "org.apache.xalan.",
            "javax.management.BadAttributeValueExpException",
            "javax.swing.UIDefaults",
            "javax.swing.MultiUIDefaults",
            // commons-collections functor chains
            "org.apache.commons.collections.functors.",
            "org.apache.commons.collections4.functors.",
            "org.apache.commons.beanutils.BeanComparator",
            // scripting engines
            "org.codehaus.groovy.runtime.",
            "groovy.util.Expando",
            "bsh.",
            "org.python.core.",
            "clojure.",
            "javassist.",
            "org.apache.commons.fileupload.disk.DiskFileItem",
            // connection pools with deserialisation side effects
            "com.mchange.v2.c3p0.",
            "org.apache.commons.dbcp.datasources.",
            "com.sun.org.apache.bcel."
    ));

    public SafeObjectInputStream(InputStream in) throws IOException {
        super(in);
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        reject(desc.getName());
        return super.resolveClass(desc);
    }

    @Override
    protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
        // A dynamic proxy is how several chains reach an InvocationHandler.
        if (interfaces != null) {
            for (String name : interfaces) {
                reject(name);
            }
        }
        return super.resolveProxyClass(interfaces);
    }

    /**
     * @return true when the class name belongs to a known gadget chain
     */
    public static boolean isBlocked(String className) {
        if (className == null) {
            return false;
        }
        String name = className;
        // array descriptors: [Ljava.lang.Runtime;
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }
        for (String prefix : BLOCKED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void reject(String className) throws InvalidClassException {
        if (isBlocked(className)) {
            log.error("Blocked deserialisation of class '" + className
                    + "': it belongs to a known Java deserialisation gadget chain. "
                    + "If this is legitimate application data, the payload should be "
                    + "reviewed rather than the block removed.");
            throw new InvalidClassException(className,
                    "class is not allowed to be deserialised by JasperReports Server");
        }
    }
}
