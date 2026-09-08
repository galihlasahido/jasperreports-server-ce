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
package com.jaspersoft.jasperserver.api.metadata.olap.util;

import com.jaspersoft.jasperserver.api.JSException;
import org.apache.commons.lang.reflect.ConstructorUtils;
import org.apache.commons.lang.reflect.MethodUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Handler for Olap properties, used to avoid complications with WebSphere and Xerces
 *
 * @author Zakhar Tomchenco
 * @version $Id: $
 */
public class XMLDecoderHandler extends DefaultHandler {

    private static Map<String, Class> classMap;
    private static Map<String, Class> arrayClassMap;

    private Class lastClass;
    private String lastValuePart;
    private Stack<Object> objects = new Stack<Object>();
    private int objectsInConstruction = 0;
    private HashMap<String, Object> idedObjects = new HashMap<String, Object>();

    private class DeferredOperation {
        String className;
        String methodName;
        String id;
        List<Object> parameters = new ArrayList<Object>();

        public DeferredOperation(String className, String methodName, String id) {
            this.className = className;
            this.methodName = methodName;
            this.id = id;
        }
    }

    private class ValueHolder {
        int index;
        Object value;
        public ValueHolder(int index) {
            this.index = index;
        }
    }

    static {
        HashMap<String, Class> map = new HashMap<String, Class>();
        map.put("string", String.class);
        map.put("char", Character.class);
        map.put("byte", Byte.class);
        map.put("short", Short.class);
        map.put("int", Integer.class);
        map.put("long", Long.class);
        map.put("float", Float.class);
        map.put("double", Double.class);
        map.put("boolean", Boolean.class);
        map.put("null", Void.class);
        classMap = Collections.unmodifiableMap(map);

        map = new HashMap<String, Class>();
        map.put("string", String.class);
        map.put("char", int.class);
        map.put("byte", byte.class);
        map.put("short", short.class);
        map.put("int", int.class);
        map.put("long", long.class);
        map.put("float", float.class);
        map.put("double", double.class);
        map.put("boolean", boolean.class);
        arrayClassMap = Collections.unmodifiableMap(map);
    }

    /*
     * SECURITY FIX (OWASP A08 - Software and Data Integrity Failures, CWE-502).
     *
     * This handler reconstructs a java.beans XML document by calling
     * Class.forName(), a constructor / static factory and then arbitrary setters
     * on whatever the document names. The document itself is attacker-controllable
     * data: it arrives as the olapViewOptions blob of an OlapUnit inside an import
     * archive (jasperserver-export-tool OlapUnitBean) and out of the repository
     * (RepoOlapUnit). Without a restriction that is a textbook remote code
     * execution gadget - e.g. an <object class="java.lang.ProcessBuilder"> node.
     *
     * OLAP view options only ever contain JPivot / Mondrian bookmark state plus
     * plain JDK value types, so resolution is restricted to those. Deployments
     * that genuinely need another package can add prefixes through the
     * "jasperserver.olap.viewOptions.allowedPackages" system property
     * (comma separated); that is a deliberate, auditable decision rather than the
     * previous unrestricted default.
     */
    static final String ALLOWED_PACKAGES_PROPERTY = "jasperserver.olap.viewOptions.allowedPackages";

    private static final Set<String> ALLOWED_CLASS_NAMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "java.lang.String", "java.lang.Boolean", "java.lang.Character", "java.lang.Byte",
            "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float",
            "java.lang.Double", "java.lang.Number", "java.lang.Object",
            "java.util.ArrayList", "java.util.LinkedList", "java.util.Vector",
            "java.util.HashMap", "java.util.LinkedHashMap", "java.util.TreeMap", "java.util.Hashtable",
            "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet",
            "java.util.Date", "java.util.Locale", "java.math.BigDecimal", "java.math.BigInteger")));

    private static final List<String> DEFAULT_ALLOWED_PACKAGES = Collections.unmodifiableList(Arrays.asList(
            "com.tonbeller.", "mondrian.", "org.olap4j.", "com.jaspersoft."));

    /**
     * Resolves a class name coming from an untrusted java.beans XML document.
     *
     * @throws JSException if the class is not on the allow-list
     */
    static Class resolveAllowedClass(String className) throws ClassNotFoundException {
        if (className == null) {
            throw new JSException("Missing class name in OLAP view options");
        }
        String name = className.trim();
        // arrays are written as e.g. "[Ljava.lang.String;" - check the element type
        String elementName = name;
        while (elementName.startsWith("[")) {
            elementName = elementName.substring(1);
        }
        if (elementName.startsWith("L") && elementName.endsWith(";")) {
            elementName = elementName.substring(1, elementName.length() - 1);
        }
        if (!isAllowedClassName(elementName)) {
            throw new JSException("Class '" + className + "' is not allowed in OLAP view options. "
                    + "Add its package to the '" + ALLOWED_PACKAGES_PROPERTY + "' system property "
                    + "if it is genuinely required.");
        }
        return Class.forName(name);
    }

    private static boolean isAllowedClassName(String name) {
        if (name.length() <= 1) {
            // primitive descriptors (I, J, Z, ...) produced by array class names
            return true;
        }
        if (ALLOWED_CLASS_NAMES.contains(name)) {
            return true;
        }
        for (String prefix : DEFAULT_ALLOWED_PACKAGES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        String extra = System.getProperty(ALLOWED_PACKAGES_PROPERTY);
        if (extra != null) {
            for (String prefix : extra.split(",")) {
                prefix = prefix.trim();
                if (!prefix.isEmpty() && name.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        try {

            Class clazz;
            String idx;
            String methodName;
            String id;

            // objects
            if (qName.equals("object")) {

                // check if it is a reference to the existing object
                if ((id = attributes.getValue("", "idref")) != null) {
                    Object obj = idedObjects.get(id);
                    if (obj == null) {
                        throw new Exception("Cannot find an object referenced by id: " + id);
                    }
                    objects.push(obj);
                } else {
                    // object construction will be deferred because it may need trailing constructor parameters
                    String className = attributes.getValue("", "class");
                    methodName = attributes.getValue("", "method");
                    id = attributes.getValue("", "id");
                    objects.push(new DeferredOperation(className, methodName, id));
                    objectsInConstruction++;
                }

            }

            // arrays
            else if (qName.equals("array")) {
                String className = attributes.getValue("", "class");
                int len = Integer.parseInt(attributes.getValue("", "length"));
                clazz = arrayClassMap.containsKey(className) ? arrayClassMap.get(className) : resolveAllowedClass(className);
                objects.push(Array.newInstance(clazz, len));
            }

            // array elements
            else if (qName.equals("void") && ((idx = attributes.getValue("", "index")) != null)) {
                objects.push(new ValueHolder(new Integer(idx)));
            }

            // instance method calls
            else if (qName.equals("void") && (methodName = attributes.getValue("", "method")) != null) {
                checkAndReplaceConstructedObject();
                //lastMethodName.push(methodName);
                objects.push(new DeferredOperation(null, methodName, null));
            }

            // instance property setters
            else if (qName.equals("void") && (methodName = attributes.getValue("", "property")) != null) {
                checkAndReplaceConstructedObject();
                // update method name
                methodName = "set" + methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
                //lastMethodName.push(methodName);
                objects.push(new DeferredOperation(null, methodName, null));
            }

            // null
            else if (qName.equals("null")) {
                objects.push(null);
            }

            // primitives
            else if ((clazz = classMap.get(qName)) != null) {
                lastClass = clazz;
            }

        } catch (Exception e) {
            throw new JSException(e);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (lastValuePart != null){
            this.characters(lastValuePart.toCharArray(), 0, 0);
        }

        // objects, primitives and arrays
        if (qName.equals("object") || classMap.containsKey(qName) || qName.equals("array")) {
            try {
                checkAndReplaceConstructedObject();
                // check if previous object is being constructed, and this object is a construct parameter
                if (objects.size() > 1) {
                    Object value = objects.pop();
                    Object owner = objects.peek();
                    if (owner instanceof DeferredOperation) {
                        ((DeferredOperation) owner).parameters.add(value);
                    }
                    else if (owner instanceof ValueHolder) {
                        ((ValueHolder) owner).value = value;
                    }
                    else {
                        objects.push(value);
                    }
                }
            } catch (Exception e) {
                throw new JSException("Cannot construct object", e);
            }
        }

        // method calls, array elements and properties
        else if (qName.equals("void")) {

            Object obj = objects.pop();
            // array element
            if (obj instanceof ValueHolder) {
                ValueHolder valueHolder = (ValueHolder) obj;
                Object array = objects.peek();
                Array.set(array, valueHolder.index, valueHolder.value);
            }
            // method call or property
            else if (obj instanceof DeferredOperation) {
                DeferredOperation deferredOperation = (DeferredOperation) obj;
                try {
                    makeCall(deferredOperation, objects.peek());
                } catch (Exception e) {
                    throw new JSException("Cannot call an instance method " + deferredOperation.methodName, e);
                }
            }
            // no luck? put it back then
            else {
                objects.push(obj);
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        String value;

        if (ch.length == start + length){
            lastValuePart = new String(ch, start, length);
        } else {
            if (lastValuePart != null) {
                value = lastValuePart + new String(ch, start, length);
                lastValuePart = null;
            } else {
                value = new String(ch, start, length);
            }

            if (lastClass != null) {
                if (lastClass.equals(String.class)) {
                    objects.push(value);
                } else if (lastClass.equals(Character.class)) {
                    objects.push(new Character(value.charAt(0)));
                } else if (lastClass.equals(Void.class)) {
                    objects.push(null);
                } else if (lastClass.equals(Boolean.class)) {
                    if (value.toLowerCase().equals("true")) {
                        objects.push(Boolean.TRUE);
                    } else if (value.toLowerCase().equals("false")) {
                        objects.push(Boolean.FALSE);
                    } else {
                        throw new JSException("Unknown boolean value: " + value);
                    }
                } else if (Number.class.isAssignableFrom(lastClass)) {
                    try {
                        objects.push(lastClass.getConstructor(String.class).newInstance(value));
                    } catch (Exception ex) {
                        throw new JSException("Cannot parse value " + value + " of type " + lastClass.getName());
                    }
                }

                lastClass = null;
            }
        }
    }

    public Object getResult(){
        return objects.pop();
    }

    private Class[] getParameterTypes(List<Object> parameters) {
        Class[] paramTypes = new Class[parameters.size()];
        int idx = 0;
        for (Object param : parameters) {
            paramTypes[idx++] = (param != null) ? param.getClass() : Object.class;
        }
        return paramTypes;
    }

    private Object constructObject(DeferredOperation objectConstructor) throws Exception {
        Class clazz = resolveAllowedClass(objectConstructor.className);
        Class[] paramTypes = new Class[objectConstructor.parameters.size()];
        int idx = 0;
        Object obj = null;
        for (Object param : objectConstructor.parameters) {
            paramTypes[idx++] = param != null ? param.getClass() : Object.class;
        }
        if (objectConstructor.methodName == null || "new".equals(objectConstructor.methodName)) {
            // calling constructor
            Constructor constructor = ConstructorUtils.getMatchingAccessibleConstructor(clazz, getParameterTypes(objectConstructor.parameters));
            if (constructor == null) {
                // check if parameters are null, i.e. will match any class
                if (objectConstructor.parameters.contains(null)) {
                    for (Constructor c : clazz.getConstructors()) {
                        if (c.getParameterTypes().length == objectConstructor.parameters.size()) {
                            obj = c.newInstance(objectConstructor.parameters.toArray());
                        }
                    }
                }
            } else {
                obj = constructor.newInstance(objectConstructor.parameters.toArray());
            }
        } else {
            Method method = MethodUtils.getMatchingAccessibleMethod(clazz, objectConstructor.methodName, paramTypes);
            // calling static method
            obj = method.invoke(null, objectConstructor.parameters.toArray());
        }
        if (obj == null) {
            throw new Exception("Cannot find a constructor for class " + clazz.getCanonicalName() + " and parameters " + objectConstructor.parameters);
        }
        if (objectConstructor.id != null) {
            idedObjects.put(objectConstructor.id, obj);
        }
        return obj;
    }

    private void makeCall(DeferredOperation methodInfo, Object owner) throws Exception {
        Method method = MethodUtils.getMatchingAccessibleMethod(owner.getClass(), methodInfo.methodName, getParameterTypes(methodInfo.parameters));
        if (method == null) {
            // check if parameters are null, i.e. will match any class
            if (methodInfo.parameters.contains(null)) {
                for (Method m : owner.getClass().getMethods()) {
                    if (m.getName().equals(methodInfo.methodName) && m.getParameterTypes().length == methodInfo.parameters.size()) {
                        m.invoke(owner, methodInfo.parameters.toArray());
                    }
                }
            }
        } else {
            method.invoke(owner, methodInfo.parameters.toArray());
        }
    }

    /**
     * Checks if top of the objects stack is an object under construction, and complete the construction
     */
    private void checkAndReplaceConstructedObject() throws Exception {
        if (objectsInConstruction > 0 && !objects.empty()) {
            Object objectUnderConstruction = objects.peek();
            if (objectUnderConstruction instanceof DeferredOperation) {
                DeferredOperation objectConstructor = (DeferredOperation) objects.pop();
                Object object = constructObject(objectConstructor);
                objects.push(object);
                objectsInConstruction--;
            }
        }
    }

}
