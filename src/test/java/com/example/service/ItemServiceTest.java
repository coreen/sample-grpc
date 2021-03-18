package com.example.service;

import com.example.CreateItemRequest;
import com.example.CreateItemResponse;
import com.example.DeleteItemRequest;
import com.example.DeleteItemResponse;
import com.example.GetItemRequest;
import com.example.GetItemResponse;
import com.example.ItemDao;
import com.example.ItemBody;
import com.example.ItemGrpc;
import com.example.ListItemsRequest;
import com.example.ListItemsResponse;
import com.example.ResponseCode;
import com.example.UpdateItemRequest;
import com.example.UpdateItemResponse;
import com.example.model.ItemEntity;
import com.google.common.collect.ImmutableList;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

@RunWith(MockitoJUnitRunner.class)
public class ItemServiceTest {
    private static final String GROUP_ID = "group1";
    private static final String ITEM_ID = "item1";
    private static final String ITEM_ID_2 = "item2";

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Test
    public void testCreate() throws Exception {
        CreateItemResponse response = createItem();
        assertThat(response.getResponseCode()).isEqualTo(ResponseCode.OK);
    }

    @Test
    public void testGet() throws Exception {
        createItem();
        GetItemRequest request = GetItemRequest.newBuilder()
                .setItemId(ITEM_ID)
                .setGroupId(GROUP_ID)
                .build();
        GetItemResponse response = getItemClient().getItem(request);
        assertThat(response.getItem()).isEqualTo(getItemBody(ITEM_ID));
    }

    @Test
    public void testDelete() throws Exception {
        createItem();
        DeleteItemRequest request = DeleteItemRequest.newBuilder()
                .setItemId(ITEM_ID)
                .setGroupId(GROUP_ID)
                .build();
        DeleteItemResponse response = getItemClient().deleteItem(request);
        assertThat(response.getResponseCode()).isEqualTo(ResponseCode.OK);
    }

    @Test
    public void testUpdate() throws Exception {
        createItem();
        UpdateItemRequest request = UpdateItemRequest.newBuilder()
                .setItem(getItemBody(ITEM_ID))
                .build();
        UpdateItemResponse response = getItemClient().updateItem(request);
        assertThat(response.getResponseCode()).isEqualTo(ResponseCode.OK);
    }

    @Test
    public void testList() throws Exception {
        createItem(ITEM_ID);
        createItem(ITEM_ID_2);
        ListItemsRequest request = ListItemsRequest.newBuilder()
                .setGroupId(GROUP_ID)
                .build();
        ListItemsResponse response = getItemClient().listItems(request);
        assertThat(response.getItemsCount()).isEqualTo(2);
    }

    private CreateItemResponse createItem() throws Exception {
        return createItem(ITEM_ID);
    }

    private CreateItemResponse createItem(String itemId) throws Exception {
        CreateItemRequest request = CreateItemRequest.newBuilder()
                .setItem(getItemBody(itemId))
                .build();
        return getItemClient().createItem(request);
    }

    private ItemBody getItemBody(String itemId) {
        return ItemBody.newBuilder()
                .setItemId(itemId)
                .setGroupId(GROUP_ID)
                .setCoreCount(1.0f)
                .setMemorySizeInMBs(500)
                .build();
    }

    private ItemGrpc.ItemBlockingStub getItemClient() throws Exception {
        // mock datastore calls since etcd isn't setup
        ItemDao itemDaoMock = Mockito.spy(new ItemDao());
        // actually calls the underlying method, which is undesirable
//        when(dao.insert(any(), any())).thenReturn(ItemEntity.builder().build());
        final ItemEntity mockItemEntity = ItemEntity.builder()
                .itemId(ITEM_ID)
                .coreCount(1.0f)
                .memorySizeInMBs(500)
                .build();
        final ItemEntity mockItemEntity2 = ItemEntity.builder()
                .itemId(ITEM_ID_2)
                .coreCount(1.0f)
                .memorySizeInMBs(500)
                .build();
        doReturn(mockItemEntity).when(itemDaoMock).insert(any(), any());
        doReturn(mockItemEntity).when(itemDaoMock).update(any(), any());
        doReturn(Optional.of(mockItemEntity)).when(itemDaoMock).get(any());
        doNothing().when(itemDaoMock).delete(any());
        doReturn(ImmutableList.of(mockItemEntity, mockItemEntity2)).when(itemDaoMock).list(any());

        final String serverName = InProcessServerBuilder.generateName();
        final Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new ItemService(itemDaoMock))
                .build()
                .start();
        final ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        final ItemGrpc.ItemBlockingStub client = ItemGrpc.newBlockingStub(channel);

        // register for automatic graceful shutdown
        grpcCleanup.register(server);
        grpcCleanup.register(channel);

        return client;
    }
}
