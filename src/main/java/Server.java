import com.example.CreateItemRequest;
import com.example.CreateItemResponse;
import com.example.DeleteItemRequest;
import com.example.DeleteItemResponse;
import com.example.GetItemRequest;
import com.example.GetItemResponse;
import com.example.HealthcheckGrpc;
import com.example.HealthcheckOuterClass.DeepHealthStatus;
import com.example.HealthcheckOuterClass.HealthStatus;
import com.example.ItemBody;
import com.example.ListItemsRequest;
import com.example.ListItemsResponse;
import com.example.SpecGrpc;
import com.example.UpdateItemRequest;
import com.example.UpdateItemResponse;
import com.google.protobuf.Empty;
import com.ibm.etcd.api.MaintenanceGrpc;
import com.ibm.etcd.api.PutRequest;
import com.ibm.etcd.client.EtcdClient;
import com.ibm.etcd.client.kv.EtcdKvClient;
import io.etcd.jetcd.Client;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import model.ItemEntity;
import model.KeyEntity;

import java.io.IOException;

/**
 * gRPC server for the specified services
 */
public class Server {
    private static Dao dao;

    public static void main(String[] args) {
        // server startup
        // https://stackoverflow.com/questions/56268424/can-i-run-multiple-grpc-services-on-same-port [YES]
        io.grpc.Server grpcServer = ServerBuilder.forPort(9090)
                .addService(new HealthcheckService())
                .addService(new ItemService())
                .build();

        try {
            grpcServer.start();
            grpcServer.awaitTermination();
        } catch (IOException|InterruptedException e) {
            e.printStackTrace();
        }
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
//            EtcdClient etcd = getClient();
            //2.convert etcd response into response for our client
            //
//            etcd.put(PutRequest.newBuilder()..build())
//            etcd.watch(null);
//            MaintenanceGrpc.MaintenanceImplBase
        }

        private Client getClient() {
            // https://github.com/etcd-io/jetcd/tree/master/jetcd-examples
            Client client = Client.builder().endpoints("http://127.0.0.1:2379").build();
//            client.getMaintenanceClient().
            return null;
        }

        private EtcdClient getKvClient() {
            // The official etcd ports are 2379 for client requests and 2380 for peer communication.
            // https://etcd.io/docs/v3.1.12/op-guide/configuration/
            return EtcdClient.forEndpoint("localhost", 2379)
//                    .withCredentials("name", "password")
                    .withPlainText()
                    .build();
        }
    }

    /**
     * Impl of Item service, outer classname property does not impact the generated *ImplBase naming
     */
    private static class ItemService extends SpecGrpc.SpecImplBase {
        @Override
        public void listItems(ListItemsRequest request,
                              StreamObserver<ListItemsResponse> responseObserver) {
            dao.list(request.getGroupId());
        }

        @Override
        public void createItem(CreateItemRequest request,
                               StreamObserver<CreateItemResponse> responseObserver) {
            final ItemBody item = request.getItem();
            final KeyEntity key = KeyEntity.builder()
                    .groupId(item.getGroupId())
                    .itemId(item.getItemId())
                    .build();
            final ItemEntity value = ItemEntity.builder()
                    .coreCount(item.getCoreCount())
                    .memorySizeInMBs(item.getMemorySizeInMBs())
                    .build();
            dao.insert(key, value);
        }

        @Override
        public void getItem(GetItemRequest request,
                            StreamObserver<GetItemResponse> responseObserver) {
            final KeyEntity key = KeyEntity.builder()
                    .groupId(request.getGroupId())
                    .itemId(request.getItemId())
                    .build();
            dao.get(key);
        }

        @Override
        public void updateItem(UpdateItemRequest request,
                               StreamObserver<UpdateItemResponse> responseObserver) {
            final ItemBody item = request.getItem();
            final KeyEntity key = KeyEntity.builder()
                    .groupId(item.getGroupId())
                    .itemId(item.getItemId())
                    .build();
            final ItemEntity value = ItemEntity.builder()
                    .coreCount(item.getCoreCount())
                    .memorySizeInMBs(item.getMemorySizeInMBs())
                    .build();
            dao.update(key, value);
        }

        @Override
        public void deleteItem(DeleteItemRequest request,
                               StreamObserver<DeleteItemResponse> responseObserver) {
            final KeyEntity key = KeyEntity.builder()
                    .groupId(request.getGroupId())
                    .itemId(request.getItemId())
                    .build();
            dao.delete(key);
        }
    }
}
