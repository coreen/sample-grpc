import com.example.HealthcheckGrpc;
import com.example.HealthcheckOuterClass.DeepHealthStatus;
import com.example.HealthcheckOuterClass.HealthStatus;
import com.google.protobuf.Empty;
import com.ibm.etcd.api.MaintenanceGrpc;
import com.ibm.etcd.client.EtcdClient;
import io.grpc.stub.StreamObserver;

/**
 * gRPC server for the specified Healthcheck service
 */
public class Server {
    public static void main(String[] args) {
        // server startup

    }

    /**
     * Impl of Healthcheck service
     */
    private static class HealthcheckService extends HealthcheckGrpc.HealthcheckImplBase {
        @Override
        public void basicHealthcheck(Empty request, StreamObserver<HealthStatus> responseObserver) {
            final HealthStatus response = HealthStatus.newBuilder().setStatus("OK").build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void deepHealthcheck(Empty request, StreamObserver<DeepHealthStatus> responseObserver) {
            //1.get gRPC client to etcd
            EtcdClient etcd = getEtcdClient();
            //2.convert etcd response into response for our client
            etcd.getKvClient().watch(null);
//            MaintenanceGrpc.MaintenanceImplBase
        }

        private EtcdClient getEtcdClient() {
            // The official etcd ports are 2379 for client requests and 2380 for peer communication.
            // https://etcd.io/docs/v3.1.12/op-guide/configuration/
            return EtcdClient.forEndpoint("localhost", 2379)
//                    .withCredentials("name", "password")
                    .withPlainText()
                    .build();
        }
    }
}
