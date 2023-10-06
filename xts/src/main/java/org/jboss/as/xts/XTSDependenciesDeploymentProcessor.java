/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.xts;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.narayana.compensations.api.CancelOnFailure;
import org.jboss.narayana.compensations.api.Compensatable;
import org.jboss.narayana.compensations.api.CompensationScoped;
import org.jboss.narayana.compensations.api.TxCompensate;
import org.jboss.narayana.compensations.api.TxConfirm;
import org.jboss.narayana.compensations.api.TxLogged;

import jakarta.ejb.TransactionAttribute;
import jakarta.jws.WebService;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class XTSDependenciesDeploymentProcessor implements DeploymentUnitProcessor {

    private static final ModuleIdentifier XTS_MODULE = ModuleIdentifier.create("org.jboss.xts");

    private static final Class[] COMPENSATABLE_ANNOTATIONS = {
            Compensatable.class,
            CancelOnFailure.class,
            CompensationScoped.class,
            TxCompensate.class,
            TxConfirm.class,
            TxLogged.class
    };

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();

        final CompositeIndex compositeIndex = unit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        if (compositeIndex == null) {
            return;
        }

        if (isCompensationAnnotationPresent(compositeIndex) || isTransactionalEndpointPresent(compositeIndex)) {
            addXTSModuleDependency(unit);
        }
    }

    private boolean isCompensationAnnotationPresent(final CompositeIndex compositeIndex) {
        for (Class annotation : COMPENSATABLE_ANNOTATIONS) {
            if (!compositeIndex.getAnnotations(DotName.createSimple(annotation.getName())).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private boolean isTransactionalEndpointPresent(final CompositeIndex compositeIndex) {
        final List<AnnotationInstance> annotations = new ArrayList<>();
        annotations.addAll(compositeIndex.getAnnotations(DotName.createSimple(Transactional.class.getName())));
        annotations.addAll(compositeIndex.getAnnotations(DotName.createSimple(TransactionAttribute.class.getName())));

        for (final AnnotationInstance annotation : annotations) {
            final Object target = annotation.target();

            if (target instanceof ClassInfo) {
                final ClassInfo classInfo = (ClassInfo) target;

                if (classInfo.annotationsMap().get(DotName.createSimple(WebService.class.getName())) != null) {
                    return true;
                }
            }
        }

        return false;
    }

    private void addXTSModuleDependency(final DeploymentUnit unit) {
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();
        final ModuleSpecification moduleSpec = unit.getAttachment(Attachments.MODULE_SPECIFICATION);
        moduleSpec.addSystemDependency(new ModuleDependency(moduleLoader, XTS_MODULE, false, false, false, false));
    }

}
