package com.example.service;

import com.example.CreateItemRequest;
import com.example.CreateItemResponse;
import com.example.DeleteItemResponse;
import com.example.ItemGrpc;
import com.example.ListItemsResponse;
import com.example.UpdateItemRequest;
import com.example.UpdateItemResponse;
import com.example.model.ItemEntity;
import com.example.ItemDao;
import com.example.DeleteItemRequest;
import com.example.GetItemRequest;
import com.example.GetItemResponse;
import com.example.ItemBody;
import com.example.ListItemsRequest;
import com.example.ResponseCode;
import com.example.model.KeyEntity;
import com.google.common.annotations.VisibleForTesting;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Impl of Item service, outer classname property does not impact the generated *ImplBase naming
 */
@Slf4j
public class ItemService extends ItemGrpc.ItemImplBase {
    private ItemDao itemDao;

    @VisibleForTesting
    public ItemService(ItemDao itemDao) {
        this.itemDao = itemDao;
    }

    @Override
    public void listItems(ListItemsRequest request,
                          StreamObserver<ListItemsResponse> responseObserver) {
        final List<ItemEntity> itemsByGroup = itemDao.list(request.getGroupId());
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
            itemDao.insert(key, value);

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
        final Optional<ItemEntity> maybeEntity = itemDao.get(key);

        GetItemResponse.Builder responseBuilder = GetItemResponse.newBuilder();
        if (!maybeEntity.isPresent()) {
            log.warn("no value found with key {} in etcd", key);

            // by default all fields in proto3 are optional, not set means null
        } else {
            final ItemEntity entity = maybeEntity.get();

            log.info("found value {} from key {} in etcd", entity, key);

            responseBuilder.setItem(ItemBody.newBuilder()
                    .setItemId(request.getItemId())
                    .setGroupId(request.getGroupId())
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
            itemDao.update(key, value);

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
            itemDao.delete(key);

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
