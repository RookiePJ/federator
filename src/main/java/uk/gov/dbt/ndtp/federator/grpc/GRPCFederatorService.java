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

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Set;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.FederatorService;
import uk.gov.dbt.ndtp.federator.exceptions.AccessDeniedException;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.utils.ClientFilter;
import uk.gov.dbt.ndtp.grpc.API;
import uk.gov.dbt.ndtp.grpc.APITopics;
import uk.gov.dbt.ndtp.grpc.FederatorServiceGrpc;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.grpc.TopicRequest;

/**
 * GRPC specific federator service that uses the POJO federator service and wrappers.
 */
public class GRPCFederatorService extends FederatorServiceGrpc.FederatorServiceImplBase {

    public static final Logger LOGGER = LoggerFactory.getLogger("GRPCFederatorService");
    private final FederatorService federator;

    /**
     * Constructor to generate the FederatorService using GRPC
     *
     * @param filters       (list of client:filter) is the filter used to make data access decisions
     * @param sharedHeaders are the header keys for headers to send to the client
     */
    public GRPCFederatorService(List<ClientFilter> filters, Set<String> sharedHeaders) {
        LOGGER.info("Creating FederatorService in GRPC:");
        this.federator = new FederatorService(filters, sharedHeaders);
    }

    @Override
    public void getKafkaTopics(API request, StreamObserver<APITopics> responseObserver) {
        try {
            LOGGER.info("GRPC getKafkaTopics for service:");
            APITopics response = federator.getKafkaTopics(request);
            responseObserver.onNext(response);
        } catch (AccessDeniedException exception) {
            LOGGER.error("Exception occurred during topic processing:", exception);
            responseObserver.onError(
                    Status.PERMISSION_DENIED.withCause(exception).asRuntimeException());
            return;
        } catch (Exception e) {
            LOGGER.error("Exception occurred during topic processing:", e);
            responseObserver.onError(Status.UNKNOWN.withCause(e).asRuntimeException());
            return;
        }
        responseObserver.onCompleted();
        LOGGER.info("Finished processing request from Client: {}", request.getClient());
    }

    @Override
    public void getKafkaConsumer(TopicRequest request, StreamObserver<KafkaByteBatch> responseObserver) {

        LOGGER.info("Started processing consumer request: {}", request.getClient());
        ServerCallStreamObserver<KafkaByteBatch> serverCallStreamObserver =
                (ServerCallStreamObserver<KafkaByteBatch>) responseObserver;
        StreamObservable streamObservable = new LimitedServerCallStreamObserver(serverCallStreamObserver);
        try {
            federator.getKafkaConsumer(request, streamObservable);
        } catch (InvalidTopicException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
