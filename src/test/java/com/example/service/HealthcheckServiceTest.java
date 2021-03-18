package com.example.service;

import com.example.HealthcheckGrpc;
import com.example.HealthcheckOuterClass.DeepHealthStatus;
import com.example.HealthcheckOuterClass.EtcdHealth;
import com.example.HealthcheckOuterClass.HealthStatus;
import com.example.HealthcheckOuterClass.StatusType;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthcheckServiceTest {
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Test
    public void testBasicHealthcheck() throws Exception {
        HealthStatus response = getHealthcheckClient().basicHealthcheck(Empty.getDefaultInstance());
        assertThat(response.getStatus()).isEqualTo(StatusType.OK);
    }

    @Test
    public void testDeepHealthcheck() throws Exception {
        DeepHealthStatus response = getHealthcheckClient().deepHealthcheck(Empty.getDefaultInstance());
        // should be unhealthy since unit test does not spin up etcd datastore
        assertThat(response.getStatus()).isEqualTo(StatusType.UNHEALTHY);
        final EtcdHealth expected = EtcdHealth.newBuilder()
                .setNodeCount(1)
                .addDownNodes("node1")
                .setLeaderId("node1")
                .build();
        assertThat(response.getDbHealth()).isEqualTo(expected);
    }

    private HealthcheckGrpc.HealthcheckBlockingStub getHealthcheckClient() throws Exception {
        final String serverName = InProcessServerBuilder.generateName();
        final Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new HealthcheckService())
                .build()
                .start();
        final ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        final HealthcheckGrpc.HealthcheckBlockingStub client = HealthcheckGrpc.newBlockingStub(channel);

        // register for automatic graceful shutdown
        grpcCleanup.register(server);
        grpcCleanup.register(channel);

        return client;
    }
}
