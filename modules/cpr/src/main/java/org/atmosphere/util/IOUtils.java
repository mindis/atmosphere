/*
 * Copyright 2014 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.util;

import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.cpr.MeteorServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletRegistration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IOUtils {
    private final static Logger logger = LoggerFactory.getLogger(IOUtils.class);
    private final static List<String> knownClasses;
    private final static  Pattern SERVLET_PATH_PATTERN = Pattern.compile("([\\/]?[\\w-[.]]+|[\\/]\\*\\*)+");

    static {
        knownClasses = new ArrayList<String>() {
            {
                add(AtmosphereServlet.class.getName());
                add(MeteorServlet.class.getName());
                add("com.vaadin.server.VaadinServlet");
                add("org.primefaces.push.PushServlet");
            }
        };
    }

    public static StringBuilder readEntirely(AtmosphereResource r) {
        final StringBuilder stringBuilder = new StringBuilder();
        AtmosphereRequest request = r.getRequest();
        if (request.body().isEmpty()) {
            BufferedReader bufferedReader = null;
            try {
                try {
                    InputStream inputStream = request.getInputStream();
                    if (inputStream != null) {
                        bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    }
                } catch (IllegalStateException ex) {
                    logger.trace("", ex);
                    Reader reader = request.getReader();
                    if (reader != null) {
                        bufferedReader = new BufferedReader(reader);
                    }
                }

                if (bufferedReader != null) {
                    char[] charBuffer = new char[8192];
                    int bytesRead = -1;
                    while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                        stringBuilder.append(charBuffer, 0, bytesRead);
                    }
                } else {
                    stringBuilder.append("");
                }
            } catch (IOException ex) {
                logger.warn("", ex);
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException ex) {
                        logger.warn("", ex);
                    }
                }
            }
        } else {
            AtmosphereRequest.Body body = request.body();
            try {
                stringBuilder.append(body.hasString() ? body.asString() : new String(body.asBytes(), body.byteOffset(), body.byteLength(), request.getCharacterEncoding()));
            } catch (UnsupportedEncodingException e) {
                logger.error("", e);
            }
        }
        return stringBuilder;
    }


    public static String guestServletPath(AtmosphereFramework framework, String exclude) {
        String servletPath = "";
        try {
            Map<String, ? extends ServletRegistration> m = framework.getServletContext().getServletRegistrations();
            for (Map.Entry<String, ? extends ServletRegistration> e : m.entrySet()) {
                Class<?> classToScan = loadClass(framework.getClass(), e.getValue().getClassName());

                if (scanForAtmosphereFramework(classToScan)) {
                    servletPath = e.getValue().getMappings().iterator().next();
                    servletPath = getCleanedServletPath(servletPath);

                    // We already found one.
                    if (!servletPath.equalsIgnoreCase(exclude)) {
                        break;
                    } else {
                        logger.trace("Already guessed {}", exclude);
                    }
                }
            }
        } catch (Exception ex) {
            logger.trace("", ex);
        }
        return servletPath;
    }

    public static String guestServletPath(AtmosphereFramework framework) {
        return guestServletPath(framework, "");
    }

    /**
     * Used to remove trailing slash and wildcard from a servlet path.<br/><br/>
     * Examples :<br/>
     *  - "/foo/" becomes "/foo"<br/>
     *  - "foo/bar" becomes "/foo/bar"<br/>
     * @param fullServletPath : Servlet mapping
     * @return Servlet mapping without trailing slash and wildcard
     */
    public static String getCleanedServletPath(String fullServletPath) {
        Matcher matcher = SERVLET_PATH_PATTERN.matcher(fullServletPath);

        // It should not happen if the servlet path is valid
        if (!matcher.find()) return fullServletPath;

        String servletPath = matcher.group(0);
        if (!servletPath.startsWith("/")) {
            servletPath = "/" + servletPath;
        }

        return servletPath;
    }

    private static boolean scanForAtmosphereFramework(Class<?> classToScan) {
        if (classToScan == null) return false;

        logger.trace("Scanning {}", classToScan.getName());

        // Before doing the Siberian traversal, look locally
        if (knownClasses.contains(classToScan.getName())) {
            return true;
        }

        try {
            Field[] fields = classToScan.getDeclaredFields();
            for (Field f : fields) {
                f.setAccessible(true);
                if (AtmosphereFramework.class.isAssignableFrom(f.getType())) {
                    return true;
                }
            }
        } catch (Exception ex) {
            logger.trace("", ex);
        }

        // Now try with parent
        if (scanForAtmosphereFramework(classToScan.getSuperclass())) return true;
        return false;
    }

    public static Class<?> loadClass(Class thisClass, String className) throws Exception {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (Throwable t) {
            return thisClass.getClassLoader().loadClass(className);
        }
    }

}