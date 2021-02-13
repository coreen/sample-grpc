package com.example;

import com.example.HealthcheckOuterClass.DeepHealthStatus;
import com.example.HealthcheckOuterClass.EtcdHealth;
import com.example.HealthcheckOuterClass.HealthStatus;
import com.example.HealthcheckOuterClass.StatusType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import com.example.model.ItemEntity;
import com.example.model.KeyEntity;

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
    need explicit "-emit-defaults" to display enum values
        https://github.com/fullstorydev/grpcurl/issues/95
    ```
    ➜  sample-grpc$ cd src/main/proto
    ➜  proto$ grpcurl -emit-defaults -plaintext -proto healthcheck.proto localhost:9090 sample.Healthcheck/BasicHealthcheck
    {
      "status": "OK"
    }
    ➜  proto$ grpcurl -emit-defaults -plaintext -proto healthcheck.proto localhost:9090 sample.Healthcheck/DeepHealthcheck
    {
      "status": "UNHEALTHY",
      "dbHealth": {
        "nodeCount": 1,
        "upNodes": [

        ],
        "downNodes": [
          "node1"
        ],
        "leaderId": "node1"
      }
    }
    ➜  proto$ grpcurl -plaintext -proto item.proto -d '{"item": {"itemId": "item1", "groupId": "group1", "coreCount": 1.0, "memorySizeInMBs": 500}}' localhost:9090 com.example.Item/CreateItem
    ERROR:
      Code: Unknown
      Message:
    ```
    ^^^ insertion call fails since etcd isn't up, only seen via deep healthcheck
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
                // ProtoReflectionService requires grpc v1.35.0 which is newer than etcd packaged v1.33.1
//                .addService(ProtoReflectionService.newInstance())
                .build();
        try {
            grpcServer.start();
            System.out.println("Started grpc server, awaiting calls...");
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
            responseObserver.onNext(HealthStatus.newBuilder()
                    .setStatus(StatusType.OK)
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void deepHealthcheck(Empty request, StreamObserver<DeepHealthStatus> responseObserver) {
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
                        responseObserver.onNext(DeepHealthStatus.newBuilder()
                                .setStatus(StatusType.OK)
                                .setDbHealth(EtcdHealth.newBuilder()
                                        .setNodeCount(1)
                                        .addUpNodes("node1")
                                        .setLeaderId("node1").build())
                                .build());
                    } else {
                        responseObserver.onNext(DeepHealthStatus.newBuilder()
                                .setStatus(StatusType.UNHEALTHY)
                                .setDbHealth(EtcdHealth.newBuilder()
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

                responseObserver.onNext(DeepHealthStatus.newBuilder()
                        .setStatus(StatusType.UNHEALTHY)
                        .setDbHealth(EtcdHealth.newBuilder()
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
