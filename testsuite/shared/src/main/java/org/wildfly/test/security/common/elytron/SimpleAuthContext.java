/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.elytron;

import org.jboss.as.test.integration.management.util.CLIWrapper;

/**
 * Elytron authentication context configuration implementation.
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class SimpleAuthContext extends AbstractConfigurableElement {

    private final String extendsAttr;
    private final MatchRules matchRules;

    private SimpleAuthContext(final Builder builder) {
        super(builder);
        this.extendsAttr = builder.extendsAttr;
        this.matchRules = builder.matchRules;
    }

    @Override
    public void create(CLIWrapper cli) throws Exception {

        final StringBuilder sb = new StringBuilder("/subsystem=elytron/authentication-context=\"");
        sb.append(name).append("\":add(");
        if (extendsAttr != null && !extendsAttr.isEmpty()) {
            sb.append(String.format("extends=\"%s\", ", extendsAttr));
        }
        if (matchRules != null) {
            sb.append(matchRules.asString());
        }
        sb.append(")");
        cli.sendLine(sb.toString());
    }

    @Override
    public void remove(CLIWrapper cli) throws Exception {
        cli.sendLine(String.format("/subsystem=elytron/authentication-context=\"%s\":remove", name));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractConfigurableElement.Builder<Builder> {
        private String extendsAttr;
        private MatchRules matchRules;

        private Builder() {
        }

        public Builder withExtends(final String extendsAttr) {
            this.extendsAttr = extendsAttr;
            return this;
        }

        public Builder withMatchRules(final MatchRules matchRules) {
            this.matchRules = matchRules;
            return this;
        }

        public SimpleAuthContext build() {
            return new SimpleAuthContext(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
