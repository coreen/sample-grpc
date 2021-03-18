package com.example;

import com.example.service.HealthcheckService;
import com.example.service.ItemService;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * gRPC server for the specified services
 */
@Slf4j
public class App {
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
                .addService(new ItemService(new ItemDao()))
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
}
