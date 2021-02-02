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
import com.example.ItemGrpc;
import com.example.ListItemsRequest;
import com.example.ListItemsResponse;
import com.example.ResponseCode;
import com.example.UpdateItemRequest;
import com.example.UpdateItemResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.ibm.etcd.api.MaintenanceGrpc;
import com.ibm.etcd.api.PutRequest;
import com.ibm.etcd.client.EtcdClient;
import com.ibm.etcd.client.KvStoreClient;
import com.ibm.etcd.client.kv.EtcdKvClient;
import com.ibm.etcd.client.kv.KvClient;
import io.etcd.jetcd.Client;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import model.ItemEntity;
import model.KeyEntity;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * gRPC server for the specified services
 */
@Slf4j
public class Server {
    /*
    https://github.com/fullstorydev/grpcurl
        requires reflection API to be enabled, or explicit *.proto filename (class based off specified package name)
        see SERVICE_NAME constant in target/generated-sources/protobuf/grpc-java folder files
    ```
    ➜  proto grpcurl -plaintext -proto healthcheck.proto localhost:9090 sample.Healthcheck/BasicHealthcheck
    {
      "status": "OK"
    }
    ➜  proto grpcurl -plaintext -proto item.proto -d '{"item": {"itemId": "item1", "groupId": "group1", "coreCount": 1.0, "memorySizeInMBs": 500}}' localhost:9090 com.example.Item/CreateItem
    ERROR:
      Code: Unknown
      Message:
    ```
    */
    public static void main(String[] args) {
        /**
         * Custom executor recommended for thread pool control.
         * https://javadoc.io/static/io.grpc/grpc-api/1.22.1/io/grpc/ServerBuilder.html#executor-java.util.concurrent.Executor-
         */
        // server startup
        // https://stackoverflow.com/questions/56268424/can-i-run-multiple-grpc-services-on-same-port [YES]
        io.grpc.Server grpcServer = ServerBuilder.forPort(9090)
                .addService(new HealthcheckService())
                .addService(new ItemService())
                // https://github.com/grpc/grpc-java/blob/master/documentation/server-reflection-tutorial.md#enable-server-reflection
                // reflection needed for grpcurl to not need to pass in the .proto file during call, requires newer grpc version
//                .addService(ProtoReflectionService.newInstance())
                .build();

        // check connection to etcd datastore first
//        WebTarget webTarget = ClientBuilder.newClient().target("http://localhost:2379");
//        Response response = null;
//        try {
//            response = webTarget.path("health").request().get();
//            if (response.getStatus() == 200) {
//                String message = response.readEntity(String.class);
//                System.out.println("Node Health: " + message);
//                log.info("etcd connection all good");
//            }
//        } catch (Exception e) {
//            log.error("something is wrong with connection to db", e);
//        } finally {
//            if (response != null) {
//                response.close();
//            }
//        }

        // temp etcd datastore test
/*
        Dao dao = new Dao();
        KeyEntity key = KeyEntity.builder().groupId("gid").itemId("iid").build();
        dao.insert(key,
                ItemEntity.builder().coreCount(1.0f).memorySizeInMBs(500).build());
        log.info("inserted entry");
        log.info("retrieved key", dao.get(key));
 */
//        final KvStoreClient etcdClient = EtcdClient.forEndpoint("localhost", 2379)
//                .withPlainText()
//                .build();
//        KvClient kvClient = etcdClient.getKvClient();
//        ByteString key = ByteString.copyFrom("testKey".getBytes());
//        kvClient.put(key, ByteString.copyFrom("testValue".getBytes())).sync();
//        log.info("inserted string key w/empty value");
//        log.info("retrieved key: " + kvClient.get(key));

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
    private static class ItemService extends ItemGrpc.ItemImplBase {
        private Dao dao = new Dao();

        @Override
        public void listItems(ListItemsRequest request,
                              StreamObserver<ListItemsResponse> responseObserver) {
            final List<ItemEntity> itemsByGroup = dao.list(request.getGroupId());
            List<ItemBody> responseItems = itemsByGroup.stream()
                    .map(itemEntity -> ItemBody.newBuilder()
                            .setItemId(itemEntity.getItemId())
                            .setCoreCount(itemEntity.getCoreCount())
                            .setMemorySizeInMBs(itemEntity.getMemorySizeInMBs())
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(ListItemsResponse.newBuilder()
                    .addAllItems(responseItems)
                    .build());
            responseObserver.onCompleted();
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
                    .itemId(item.getItemId()) // duplicated here so list contains itemId, rather than parsing key string
                    .coreCount(item.getCoreCount())
                    .memorySizeInMBs(item.getMemorySizeInMBs())
                    .build();

            try {
                dao.insert(key, value);

                log.info("successfully inserted value {} into etcd", value);

                responseObserver.onNext(CreateItemResponse.newBuilder()
                        .setResponseCode(ResponseCode.OK)
                        .build());
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("error occurred attempting to insert value {} into etcd", value);
                responseObserver.onError(e);
            }
        }

        @Override
        public void getItem(GetItemRequest request,
                            StreamObserver<GetItemResponse> responseObserver) {
            final KeyEntity key = KeyEntity.builder()
                    .groupId(request.getGroupId())
                    .itemId(request.getItemId())
                    .build();
            final Optional<ItemEntity> maybeEntity = dao.get(key);

            GetItemResponse.Builder responseBuilder = GetItemResponse.newBuilder();
            if (!maybeEntity.isPresent()) {
                log.warn("no value found with key {} in etcd", key);

                // by default all fields in proto3 are optional, not set means null
            } else {
                final ItemEntity entity = maybeEntity.get();

                log.info("found value {} from key {} in etcd", entity, key);

                responseBuilder.setItem(ItemBody.newBuilder()
                        .setCoreCount(entity.getCoreCount())
                        .setMemorySizeInMBs(entity.getMemorySizeInMBs())
                        .build());
            }
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
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

            try {
                dao.update(key, value);

                log.info("successfully updated key {} with value {} into etcd", key, value);

                responseObserver.onNext(UpdateItemResponse.newBuilder()
                        .setResponseCode(ResponseCode.OK)
                        .build());
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("error occurred attempting to update key {} with value {} into etcd", key, value);
                responseObserver.onError(e);
            }
        }

        @Override
        public void deleteItem(DeleteItemRequest request,
                               StreamObserver<DeleteItemResponse> responseObserver) {
            final KeyEntity key = KeyEntity.builder()
                    .groupId(request.getGroupId())
                    .itemId(request.getItemId())
                    .build();
            try {
                dao.delete(key);

                log.info("successfully deleted key {} from etcd", key);

                responseObserver.onNext(DeleteItemResponse.newBuilder()
                        .setResponseCode(ResponseCode.OK)
                        .build());
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("error occurred attempting to delete key {} from etcd", key);
                responseObserver.onError(e);
            }
        }
    }
}
