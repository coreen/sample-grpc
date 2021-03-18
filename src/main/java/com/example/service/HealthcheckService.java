package com.example.service;

import com.example.HealthcheckGrpc;
import com.example.EtcdHealthResponse;
import com.example.HealthcheckOuterClass;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

/**
 * Impl of Healthcheck service
 */
@Slf4j
public class HealthcheckService extends HealthcheckGrpc.HealthcheckImplBase {
    @Override
    public void basicHealthcheck(Empty request, StreamObserver<HealthcheckOuterClass.HealthStatus> responseObserver) {
        responseObserver.onNext(HealthcheckOuterClass.HealthStatus.newBuilder()
                .setStatus(HealthcheckOuterClass.StatusType.OK)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void deepHealthcheck(Empty request, StreamObserver<HealthcheckOuterClass.DeepHealthStatus> responseObserver) {
        // check connection to etcd datastore, todo: extend for multi-node cluster
        WebTarget webTarget = ClientBuilder.newClient().target("http://localhost:2379");
        ObjectMapper mapper = new ObjectMapper();
        Response response = null;
        try {
            response = webTarget.path("health").request().get();
            if (response.getStatus() == 200) {
                String message = response.readEntity(String.class);
                EtcdHealthResponse etcd = mapper.readValue(message, EtcdHealthResponse.class);
                boolean isHealthy = Boolean.parseBoolean(etcd.getHealth());
                if (isHealthy) {
                    responseObserver.onNext(HealthcheckOuterClass.DeepHealthStatus.newBuilder()
                            .setStatus(HealthcheckOuterClass.StatusType.OK)
                            .setDbHealth(HealthcheckOuterClass.EtcdHealth.newBuilder()
                                    .setNodeCount(1)
                                    .addUpNodes("node1")
                                    .setLeaderId("node1").build())
                            .build());
                } else {
                    responseObserver.onNext(HealthcheckOuterClass.DeepHealthStatus.newBuilder()
                            .setStatus(HealthcheckOuterClass.StatusType.UNHEALTHY)
                            .setDbHealth(HealthcheckOuterClass.EtcdHealth.newBuilder()
                                    .setNodeCount(1)
                                    .addDownNodes("node1")
                                    .setLeaderId("node1").build())
                            .build());
                }
                responseObserver.onCompleted();

                System.out.println("Node Healthy? " + isHealthy);
                log.info("etcd connection all good");
            }
        } catch (Exception e) {
            log.error("something is wrong with connection to db", e);

            responseObserver.onNext(HealthcheckOuterClass.DeepHealthStatus.newBuilder()
                    .setStatus(HealthcheckOuterClass.StatusType.UNHEALTHY)
                    .setDbHealth(HealthcheckOuterClass.EtcdHealth.newBuilder()
                            .setNodeCount(1)
                            .addDownNodes("node1")
                            .setLeaderId("node1").build())
                    .build());
            responseObserver.onCompleted();
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }
}
