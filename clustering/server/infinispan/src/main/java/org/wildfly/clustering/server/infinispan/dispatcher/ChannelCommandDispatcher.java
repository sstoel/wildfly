/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.server.infinispan.dispatcher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.jgroups.Address;
import org.jgroups.BytesMessage;
import org.jgroups.Message;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RspFilter;
import org.wildfly.clustering.dispatcher.Command;
import org.wildfly.clustering.dispatcher.CommandDispatcher;
import org.wildfly.clustering.dispatcher.CommandDispatcherException;
import org.wildfly.clustering.group.Node;
import org.wildfly.clustering.server.group.Group;
import org.wildfly.clustering.server.infinispan.group.JGroupsAddressResolver;

/**
 * MessageDispatcher-based command dispatcher.
 * @author Paul Ferraro
 *
 * @param <CC> command execution context
 */
public class ChannelCommandDispatcher<CC, MC> implements CommandDispatcher<CC> {

    private static final RspFilter FILTER = new RspFilter() {
        @Override
        public boolean isAcceptable(Object response, Address sender) {
            return !(response instanceof NoSuchService);
        }

        @Override
        public boolean needMoreResponses() {
            return true;
        }
    };

    private final MessageDispatcher dispatcher;
    private final CommandMarshaller<CC> marshaller;
    private final MC context;
    private final Group<Address> group;
    private final Duration timeout;
    private final CommandDispatcher<CC> localDispatcher;
    private final Runnable closeTask;
    private final Address localAddress;
    private final RequestOptions options;

    public ChannelCommandDispatcher(MessageDispatcher dispatcher, CommandMarshaller<CC> marshaller, MC context, Group<Address> group, Duration timeout, CommandDispatcher<CC> localDispatcher, Runnable closeTask) {
        this.dispatcher = dispatcher;
        this.marshaller = marshaller;
        this.context = context;
        this.group = group;
        this.timeout = timeout;
        this.localDispatcher = localDispatcher;
        this.closeTask = closeTask;
        this.localAddress = dispatcher.getChannel().getAddress();
        this.options = new RequestOptions(ResponseMode.GET_ALL, this.timeout.toMillis(), false, FILTER, Message.Flag.DONT_BUNDLE, Message.Flag.OOB);
    }

    @Override
    public CC getContext() {
        return this.localDispatcher.getContext();
    }

    @Override
    public void close() {
        this.closeTask.run();
    }

    @Override
    public <R> CompletionStage<R> executeOnMember(Command<R, ? super CC> command, Node member) throws CommandDispatcherException {
        // Bypass MessageDispatcher if target node is local
        Address address = JGroupsAddressResolver.INSTANCE.apply(member);
        if (this.localAddress.equals(address)) {
            return this.localDispatcher.executeOnMember(command, member);
        }
        ByteBuffer buffer = this.createBuffer(command);
        Message message = this.createMessage(buffer, address);
        ServiceRequest<R, MC> request = new ServiceRequest<>(this.dispatcher.getCorrelator(), address, this.options, this.context);
        return request.send(message);
    }

    @Override
    public <R> Map<Node, CompletionStage<R>> executeOnGroup(Command<R, ? super CC> command, Node... excludedMembers) throws CommandDispatcherException {
        Set<Node> excluded = (excludedMembers != null) ? new HashSet<>(Arrays.asList(excludedMembers)) : Collections.emptySet();
        Map<Node, CompletionStage<R>> results = new ConcurrentHashMap<>();
        ByteBuffer buffer = this.createBuffer(command);
        for (Node member : this.group.getMembership().getMembers()) {
            if (!excluded.contains(member)) {
                Address address = JGroupsAddressResolver.INSTANCE.apply(member);
                if (this.localAddress.equals(address)) {
                    results.put(member, this.localDispatcher.executeOnMember(command, member));
                } else {
                    try {
                        ServiceRequest<R, MC> request = new ServiceRequest<>(this.dispatcher.getCorrelator(), address, this.options, this.context);
                        Message message = this.createMessage(buffer, address);
                        CompletionStage<R> future = request.send(message);
                        results.put(member, future);
                        future.whenComplete(new PruneCancellationTask<>(results, member));
                    } catch (CommandDispatcherException e) {
                        // Cancel previously dispatched messages
                        for (CompletionStage<R> result : results.values()) {
                            result.toCompletableFuture().cancel(true);
                        }
                        throw e;
                    }
                }
            }
        }
        return results;
    }

    private <R> ByteBuffer createBuffer(Command<R, ? super CC> command) {
        try {
            return this.marshaller.marshal(command);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Message createMessage(ByteBuffer buffer, Address destination) {
        return new BytesMessage().setArray(buffer.array(), buffer.arrayOffset(), buffer.limit() - buffer.arrayOffset()).src(this.localAddress).dest(destination);
    }

    private static class PruneCancellationTask<T> implements BiConsumer<T, Throwable> {
        private final Map<Node, CompletionStage<T>> results;
        private final Node member;

        PruneCancellationTask(Map<Node, CompletionStage<T>> results, Node member) {
            this.results = results;
            this.member = member;
        }

        @Override
        public void accept(T result, Throwable exception) {
            if (exception instanceof CancellationException) {
                this.results.remove(this.member);
            }
        }
    }
}
