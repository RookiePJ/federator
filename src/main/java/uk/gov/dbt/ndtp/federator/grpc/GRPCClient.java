// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.grpc;

import static uk.gov.dbt.ndtp.federator.utils.GRPCExceptionUtils.handleGRPCException;

import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.exceptions.RetryableException;
import uk.gov.dbt.ndtp.federator.utils.KafkaUtil;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.utils.RedisUtil;
import uk.gov.dbt.ndtp.grpc.API;
import uk.gov.dbt.ndtp.grpc.APITopics;
import uk.gov.dbt.ndtp.grpc.FederatorServiceGrpc;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.grpc.TopicRequest;
import uk.gov.dbt.ndtp.secure.agent.sources.Event;
import uk.gov.dbt.ndtp.secure.agent.sources.Header;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.sinks.KafkaSink;
import uk.gov.dbt.ndtp.secure.agent.sources.memory.SimpleEvent;

/**
 * GRPCClient is a client for the FederatorService GRPC service.
 * It is used to obtain topics and consume messages from the GRPC service.
 * It also sends messages to the KafkaSink.
 * It is also used to test connectivity to the KafkaSink.
 * It is also used to test connectivity to the GRPC service.
 * It is also used to close the GRPC client.
 * It is also used to process a topic.
 * It is also used to consume messages and send them to the KafkaSink.
 */
public class GRPCClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("GRPClient");
    private static final String CLIENT_KEEP_ALIVE_TIME = "client.keepAliveTime.secs";
    private static final String CLIENT_KEEP_ALIVE_TIMEOUT = "client.keepAliveTimeout.secs";
    private static final String CLIENT_IDLE_TIMEOUT = "client.idleTimeout.secs";
    private static final String TEN = "10";
    private static final String THIRTY = "30";

    private final ManagedChannel channel;
    private final FederatorServiceGrpc.FederatorServiceBlockingStub blockingStub;
    private final String client;
    private final String key;
    private final String topicPrefix;
    private final String serverName;

    public GRPCClient(ConnectionProperties connectionProperties, String topicPrefix) {
        this(
                connectionProperties.clientName(),
                connectionProperties.clientKey(),
                connectionProperties.serverName(),
                connectionProperties.serverHost(),
                connectionProperties.serverPort(),
                connectionProperties.tls(),
                topicPrefix);
    }

    public GRPCClient(
            String client,
            String key,
            String serverName,
            String host,
            int port,
            boolean isTLSEnabled,
            String topicPrefix) {
        LOGGER.info("Topic Prefix - '{}'", topicPrefix);
        this.topicPrefix = topicPrefix;
        LOGGER.info("Client Name - '{}'", client);
        this.client = client;
        LOGGER.info("Client Key is empty string - '{}'", key.isEmpty());
        this.key = key;
        LOGGER.info("GRPC Server Name - '{}'", serverName);
        this.serverName = serverName;
        LOGGER.info("GRPC Server Host - '{}'", host);
        LOGGER.info("GRPC Server Port - '{}'", port);
        LOGGER.info("GRPC Server TLS Enabled - '{}'", isTLSEnabled);
        channel = generateChannel(host, port, isTLSEnabled);
        blockingStub = FederatorServiceGrpc.newBlockingStub(channel);
    }

    public String getRedisPrefix() {
        return client + "-" + serverName;
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (Throwable t) {
            LOGGER.error("Exception closing client", t);
        }
    }

    public List<String> obtainTopics() {
        try {
            LOGGER.info("Making call to obtain topics for client: '{}'.", client);
            API apiRequest = API.newBuilder().setClient(client).setKey(key).build();
            LOGGER.info("built apiRequest for client grpc call");
            APITopics apiTopicsResponse = blockingStub.getKafkaTopics(apiRequest);
            LOGGER.info("Received response: {}", apiTopicsResponse.getTopicsList());
            return apiTopicsResponse.getTopicsList();
        } catch (StatusRuntimeException exception) {
            String msg = String.format("Unable to obtain topics: %s", exception.getMessage());
            LOGGER.warn(msg);
            handleGRPCException(exception);
        } catch (Exception exception) {
            String msg = String.format("Encountered error obtaining topics: %s", exception.getMessage());
            LOGGER.error(msg);
            throw exception;
        }
        return Collections.emptyList();
    }

    public void processTopic(String topic, long offset) {
        LOGGER.info("Processing topic: {}", topic);
        // This does a smoke test so hopefully fail early if problems.
        RedisUtil.getInstance();
        TopicRequest topicRequest = TopicRequest.newBuilder()
                .setTopic(topic)
                .setOffset(offset)
                .setAPIKey(key)
                .setClient(client)
                .build();

        try (KafkaSink<Bytes, Bytes> sink = getSender(topic, this.topicPrefix, this.serverName)) {
            try (Context.CancellableContext withCancellation = Context.current().withCancellation()) {
                withCancellation.run(() -> consumeMessagesAndSendOn(topicRequest, sink));
            } catch (StatusRuntimeException exception) {
                if (Status.INVALID_ARGUMENT
                        .getCode()
                        .equals(exception.getStatus().getCode())) {
                    LOGGER.error("Topic ({}) no longer valid for client ({})", topic, client);
                } else {
                    LOGGER.error("Topic processing stopped due to unknown error.", exception);
                }
            } catch (Exception exception) {
                LOGGER.error("Topic processing stopped due to error.", exception);
            }
        } catch (KafkaException e) {
            LOGGER.warn("Failed to create KafkaSink - '{}'", e.getMessage());
            throw new RetryableException(e);
        }
    }

    public void consumeMessagesAndSendOn(TopicRequest topicRequest, KafkaSink<Bytes, Bytes> sink) {
        Iterator<KafkaByteBatch> iterator = blockingStub.getKafkaConsumer(topicRequest);
        while (iterator.hasNext()) {
            KafkaByteBatch batch = iterator.next();
            if (null == batch) {
                LOGGER.info("Processing null message");
                continue;
            }
            LOGGER.debug(
                    "Consuming message: {}, {} : {}",
                    batch.getTopic(),
                    batch.getOffset(),
                    batch.getValue().toStringUtf8());
            sendMessage(sink, batch);
            RedisUtil.getInstance().setOffset(getRedisPrefix(), topicRequest.getTopic(), batch.getOffset());
            LOGGER.debug("Writing to redis: {}-{} {}", getRedisPrefix(), topicRequest.getTopic(), batch.getOffset());
        }
        LOGGER.info("Finished consuming");
    }

    public static void sendMessage(KafkaSink<Bytes, Bytes> sink, KafkaByteBatch batch) {
        LOGGER.debug("Creating message to send");
        Bytes key = new Bytes(batch.getKey().toByteArray());
        Bytes value = new Bytes(batch.getValue().toByteArray());
        List<Header> headers = batch.getSharedList().stream()
                .map(h -> new Header(h.getKey(), h.getValue()))
                .collect(Collectors.toList());
        Event<Bytes, Bytes> event = new SimpleEvent<>(headers, key, value);
        LOGGER.debug("Sending message");
        sink.send(event);
        LOGGER.debug("Sent event");
    }

    public static KafkaSink<Bytes, Bytes> getSender(String topic, String topicPrefix, String serverName) {
        return KafkaUtil.getKafkaSink(concatCompoundTopicName(topic, topicPrefix, serverName));
    }

    private static String concatCompoundTopicName(String topic, String topicPrefix, String serverName) {
        if (topicPrefix.isEmpty()) {
            return String.join("-", serverName, topic);
        }
        return String.join("-", topicPrefix, serverName, topic);
    }

    public static ManagedChannel generateChannel(String host, int port, boolean isTLSEnabled) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port)
                .keepAliveTime(PropertyUtil.getPropertyIntValue(CLIENT_KEEP_ALIVE_TIME, THIRTY), TimeUnit.SECONDS)
                .keepAliveTimeout(PropertyUtil.getPropertyIntValue(CLIENT_KEEP_ALIVE_TIMEOUT, TEN), TimeUnit.SECONDS)
                .idleTimeout(PropertyUtil.getPropertyIntValue(CLIENT_IDLE_TIMEOUT, TEN), TimeUnit.SECONDS);
        if (isTLSEnabled) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }
        builder.intercept(new CustomClientInterceptor());
        return builder.build();
    }

    public void testConnectivity() {
        KafkaUtil.getKafkaSinkBuilder().topic("test").build().close();
    }
}
