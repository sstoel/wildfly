/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.weld.util;

import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import jakarta.annotation.Resource;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.InjectionPoint;

import org.jboss.as.weld.logging.WeldLogger;
import org.jboss.metadata.property.PropertyReplacer;
import org.jboss.weld.injection.ParameterInjectionPoint;

public class ResourceInjectionUtilities {

    private ResourceInjectionUtilities() {
    }

    public static final String RESOURCE_LOOKUP_PREFIX = "java:comp/env";

    public static String getResourceName(String jndiName, String mappedName) {
        if (mappedName != null) {
            return mappedName;
        } else if (jndiName != null) {
            return jndiName;
        } else {
            throw WeldLogger.ROOT_LOGGER.cannotDetermineResourceName();
        }
    }

    public static String getResourceName(InjectionPoint injectionPoint, PropertyReplacer propertyReplacer) {
        Resource resource = getResourceAnnotated(injectionPoint).getAnnotation(Resource.class);
        String mappedName = resource.mappedName();
        if (!mappedName.equals("")) {
            return propertyReplacer == null ? mappedName : propertyReplacer.replaceProperties(mappedName);
        }
        String name = resource.name();
        if (!name.equals("")) {
            name = propertyReplacer == null ? name : propertyReplacer.replaceProperties(name);
            //see if this is a prefixed name
            //and if so just return it
            int firstSlash = name.indexOf("/");
            int colon = name.indexOf(":");
            if (colon != -1
                    && (firstSlash == -1 || colon < firstSlash)) {
                    return name;
            }

            return RESOURCE_LOOKUP_PREFIX + "/" + name;
        }
        String propertyName;
        if (injectionPoint.getMember() instanceof Field) {
            propertyName = injectionPoint.getMember().getName();
        } else if (injectionPoint.getMember() instanceof Method) {
            propertyName = getPropertyName((Method) injectionPoint.getMember());
            if (propertyName == null) {
                throw WeldLogger.ROOT_LOGGER.injectionPointNotAJavabean((Method) injectionPoint.getMember());
            }
        } else {
            throw WeldLogger.ROOT_LOGGER.cannotInject(injectionPoint);
        }
        String className = injectionPoint.getMember().getDeclaringClass().getName();
        return RESOURCE_LOOKUP_PREFIX + "/" + className + "/" + propertyName;
    }

    public static String getPropertyName(Method method) {
        String methodName = method.getName();
        if (methodName.matches("^(get).*") && method.getParameterCount() == 0) {
            return Introspector.decapitalize(methodName.substring(3));
        } else if (methodName.matches("^(is).*") && method.getParameterCount() == 0) {
            return Introspector.decapitalize(methodName.substring(2));
        } else if (methodName.matches("^(set).*") && method.getParameterCount() == 1) {
            return Introspector.decapitalize(methodName.substring(3));
        }
        return null;
    }

    public static String getPropertyName(Member member) {
        if (member instanceof Method) {
            return getPropertyName((Method) member);
        }
        return member.getName();
    }

    public static Annotated getResourceAnnotated(InjectionPoint injectionPoint) {

        if(injectionPoint instanceof ParameterInjectionPoint) {
            return ((ParameterInjectionPoint<?, ?>)injectionPoint).getAnnotated().getDeclaringCallable();
        }
        return injectionPoint.getAnnotated();
    }

}
