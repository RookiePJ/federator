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

import io.grpc.stub.ServerCallStreamObserver;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;

/**
 * Wraps the GRPC ServerCallStreamObserver inside the StreamObservable interface.
 *
 * The StreamObservable interface is used within the FederatorService and this class wraps
 * the GRPC components in that interface.
 */
public class LimitedServerCallStreamObserver implements StreamObservable {

    private ServerCallStreamObserver<KafkaByteBatch> serverCallStreamObserver;

    public LimitedServerCallStreamObserver(ServerCallStreamObserver<KafkaByteBatch> serverCallStreamObserver) {
        this.serverCallStreamObserver = (ServerCallStreamObserver<KafkaByteBatch>) serverCallStreamObserver;
    }

    @Override
    public void setOnCancelHandler(Runnable onCancelHandler) {
        this.serverCallStreamObserver.setOnCancelHandler(onCancelHandler);
    }

    @Override
    public boolean isCancelled() {
        return this.serverCallStreamObserver.isCancelled();
    }

    @Override
    public void onNext(KafkaByteBatch value) {
        this.serverCallStreamObserver.onNext(value);
    }

    @Override
    public void onError(Exception e) {
        this.serverCallStreamObserver.onError(e);
    }

    @Override
    public void onCompleted() {
        this.serverCallStreamObserver.onCompleted();
    }
}
