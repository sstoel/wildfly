/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.apache.kafka.raft;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.raft.generated.QuorumStateData;
import org.apache.kafka.raft.generated.QuorumStateData.Voter;
import org.apache.kafka.raft.generated.QuorumStateDataJsonConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ShortNode;

/**
 * Applies https://github.com/apache/kafka/commit/f7cc920771735576d9cfba2afe6f26fdcfb2ccd4 to
 * https://github.com/apache/kafka/blob/trunk/raft/src/main/java/org/apache/kafka/raft/FileBasedStateStore.java
 *
 * The fix is needed to be able to boot the embedded Kafka on Windows. The mentioned patch will not be
 * included in Kafka upstream, as Windows is not as a supported platform for Kafka.
 *
 * The aim for us is to make sure that we can connect to a running Kafka from both Linux and Windows.
 *
 * We might need to update this patch if FileBasedStateStore on Kafka changes in new versions.
 * The change needed for Windows is in writeElectionStateToFile():
 *
 * <pre>
 * -        try (final FileOutputStream fileOutputStream = new FileOutputStream(temp);
 * -             final BufferedWriter writer = new BufferedWriter(
 * -                 new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8))) {
 * +        final OpenOption[] options = { StandardOpenOption.WRITE,
 * +                StandardOpenOption.CREATE_NEW, StandardOpenOption.SPARSE };
 * +
 * +        try (BufferedWriter writer = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8, options)) {
 *              short version = state.highestSupportedVersion();
 *
 *              ObjectNode jsonState = (ObjectNode) QuorumStateDataJsonConverter.write(state, version);
 *              jsonState.set(DATA_VERSION, new ShortNode(version));
 *              writer.write(jsonState.toString());
 *              writer.flush();
 * -            fileOutputStream.getFD().sync();
 * +            writer.close();
 *              Utils.atomicMoveWithFallback(temp.toPath(), stateFile.toPath());
 * </pre>
 */
public class FileBasedStateStore implements QuorumStateStore {
    private static final Logger log = LoggerFactory.getLogger(FileBasedStateStore.class);

    private final File stateFile;

    static final String DATA_VERSION = "data_version";

    public FileBasedStateStore(final File stateFile) {
        this.stateFile = stateFile;
    }

    private QuorumStateData readStateFromFile(File file) {
        try (final BufferedReader reader = Files.newBufferedReader(file.toPath())) {
            final String line = reader.readLine();
            if (line == null) {
                throw new EOFException("File ended prematurely.");
            }

            final ObjectMapper objectMapper = new ObjectMapper();
            JsonNode readNode = objectMapper.readTree(line);

            if (!(readNode instanceof ObjectNode)) {
                throw new IOException("Deserialized node " + readNode +
                        " is not an object node");
            }
            final ObjectNode dataObject = (ObjectNode) readNode;

            JsonNode dataVersionNode = dataObject.get(DATA_VERSION);
            if (dataVersionNode == null) {
                throw new IOException("Deserialized node " + readNode +
                        " does not have " + DATA_VERSION + " field");
            }

            final short dataVersion = dataVersionNode.shortValue();
            return QuorumStateDataJsonConverter.read(dataObject, dataVersion);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Error while reading the Quorum status from the file %s", file), e);
        }
    }

    /**
     * Reads the election state from local file.
     */
    @Override
    public ElectionState readElectionState() {
        if (!stateFile.exists()) {
            return null;
        }

        QuorumStateData data = readStateFromFile(stateFile);

        return new ElectionState(data.leaderEpoch(),
                data.leaderId() == UNKNOWN_LEADER_ID ? OptionalInt.empty() : OptionalInt.of(data.leaderId()),
                data.votedId() == NOT_VOTED ? OptionalInt.empty() : OptionalInt.of(data.votedId()),
                data.currentVoters()
                        .stream().map(Voter::voterId).collect(Collectors.toSet()));
    }

    @Override
    public void writeElectionState(ElectionState latest) {
        QuorumStateData data = new QuorumStateData()
                .setLeaderEpoch(latest.epoch)
                .setVotedId(latest.hasVoted() ? latest.votedId() : NOT_VOTED)
                .setLeaderId(latest.hasLeader() ? latest.leaderId() : UNKNOWN_LEADER_ID)
                .setCurrentVoters(voters(latest.voters()));
        writeElectionStateToFile(stateFile, data);
    }

    private List<Voter> voters(Set<Integer> votersId) {
        return votersId.stream().map(
                voterId -> new Voter().setVoterId(voterId)).collect(Collectors.toList());
    }

    private void writeElectionStateToFile(final File stateFile, QuorumStateData state) {

        final File temp = new File(stateFile.getAbsolutePath() + ".tmp");
        deleteFileIfExists(temp);

        log.trace("Writing tmp quorum state {}", temp.getAbsolutePath());

        final OpenOption[] options = { StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.SPARSE };

        try (BufferedWriter writer = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8, options)) {
            short version = state.highestSupportedVersion();

            ObjectNode jsonState = (ObjectNode) QuorumStateDataJsonConverter.write(state, version);
            jsonState.set(DATA_VERSION, new ShortNode(version));
            writer.write(jsonState.toString());
            writer.flush();
            writer.close();
            Utils.atomicMoveWithFallback(temp.toPath(), stateFile.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Error while writing the Quorum status from the file %s",
                            stateFile.getAbsolutePath()),
                    e);
        } finally {
            // cleanup the temp file when the write finishes (either success or fail).
            deleteFileIfExists(temp);
        }
    }

    /**
     * Clear state store by deleting the local quorum state file
     */
    @Override
    public void clear() {
        deleteFileIfExists(stateFile);
        deleteFileIfExists(new File(stateFile.getAbsolutePath() + ".tmp"));
    }

    @Override
    public String toString() {
        return "Quorum state filepath: " + stateFile.getAbsolutePath();
    }

    private void deleteFileIfExists(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Error while deleting file %s", file.getAbsoluteFile()), e);
        }
    }
}