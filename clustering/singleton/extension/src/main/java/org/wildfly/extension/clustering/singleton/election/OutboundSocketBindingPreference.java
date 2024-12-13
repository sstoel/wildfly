/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.singleton.election;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.jgroups.JGroupsAddress;
import org.jboss.as.network.OutboundSocketBinding;
import org.jgroups.Event;
import org.jgroups.JChannel;
import org.jgroups.stack.IpAddress;
import org.wildfly.clustering.server.GroupMember;
import org.wildfly.clustering.server.infinispan.CacheContainerGroupMember;

/**
 * An election policy preference defined as an outbound socket binding.
 * @author Paul Ferraro
 */
public class OutboundSocketBindingPreference implements Predicate<GroupMember> {

    private final Supplier<OutboundSocketBinding> binding;
    private final Supplier<JChannel> channel;

    public OutboundSocketBindingPreference(Supplier<OutboundSocketBinding> binding, Supplier<JChannel> channel) {
        this.binding = binding;
        this.channel = channel;
    }

    @Override
    public boolean test(GroupMember member) {
        if (member instanceof CacheContainerGroupMember) {
            CacheContainerGroupMember infinispanMember = (CacheContainerGroupMember) member;
            Address infinispanAddress = infinispanMember.getAddress();
            if (infinispanAddress instanceof JGroupsAddress) {
                org.jgroups.Address address = ((JGroupsAddress) infinispanAddress).getJGroupsAddress();
                IpAddress physicalAddress = (IpAddress) this.channel.get().down(new Event(Event.GET_PHYSICAL_ADDRESS, address));
                // Physical address might be null if node is no longer a member of the cluster
                if (physicalAddress != null) {
                    OutboundSocketBinding binding = this.binding.get();
                    try {
                        return binding.getResolvedDestinationAddress().equals(physicalAddress.getIpAddress()) && (binding.getDestinationPort() == physicalAddress.getPort());
                    } catch (UnknownHostException e) {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        OutboundSocketBinding binding = this.binding.get();
        return InetSocketAddress.createUnresolved(binding.getUnresolvedDestinationAddress(), binding.getDestinationPort()).toString();
    }
}
