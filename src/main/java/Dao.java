import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.ibm.etcd.api.DeleteRangeRequest;
import com.ibm.etcd.api.DeleteRangeResponse;
import com.ibm.etcd.api.PutRequest;
import com.ibm.etcd.api.RangeResponse;
import com.ibm.etcd.client.EtcdClient;
import com.ibm.etcd.client.KvStoreClient;
import com.ibm.etcd.client.kv.KvClient;
import model.ItemEntity;
import model.KeyEntity;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Key: composite => groupId/itemId
 * Value: JSON blob of everything else
 */
public class Dao {
    private final ObjectMapper mapper = new ObjectMapper();
    private KvClient kvClient;

    public Dao() {
        // The official etcd ports are 2379 for client requests and 2380 for peer communication.
        // https://etcd.io/docs/v3.1.12/op-guide/configuration/
        final KvStoreClient etcdClient = EtcdClient.forEndpoint("localhost", 2379)
                .withPlainText()
                .build();
        kvClient = etcdClient.getKvClient();
    }

    public List<ItemEntity> list(String groupId) {
        RangeResponse response = kvClient.get(ByteString.copyFrom(groupId.getBytes())).asPrefix().sync();
        response.getKvsList().stream()
                .map(keyValue -> valueFromJsonByteString(keyValue.getValue(), ItemEntity.class))
                .collect(Collectors.toList());
        return null;
    }

    public Optional<ItemEntity> get(KeyEntity key) {
        RangeResponse response = kvClient.get(key.getByteString()).limit(1).sync();
        assert(response.getKvsCount() == 1);
        ByteString value = response.getKvs(0).getValue();
        return Optional.of(valueFromJsonByteString(value, ItemEntity.class));
    }

    public ItemEntity getOrThrow(KeyEntity key, Supplier<? extends RuntimeException> supplier) {
        return get(key).orElseThrow(supplier);
    }

    public ItemEntity insert(KeyEntity key, ItemEntity value) {
        final ByteString bsKey = key.getByteString();
        kvClient.put(bsKey, valueToJsonByteString(value)).sync();

        // test if exists, return that deserialized entity
        RangeResponse response = kvClient.get(bsKey).limit(1).sync();
        assert(response.getKvsCount() == 1);
        ByteString bsValue = response.getKvs(0).getValue();
        return valueFromJsonByteString(bsValue, ItemEntity.class);
    }

    /**
     * Throws if entry does not exist, otherwise updates values to match provided ItemEntity (delete / recreate)
     */
    public ItemEntity update(KeyEntity key, ItemEntity updatedValue) {
        final ByteString bsKey = key.getByteString();
        RangeResponse response = kvClient.get(bsKey).limit(1).sync();
        assert(response.getKvsCount() == 1); // entry should exist, AssertionError if not

        ByteString bsValue = response.getKvs(0).getValue();
        ItemEntity oldValue = valueFromJsonByteString(bsValue, ItemEntity.class);

        kvClient.batch()
                .delete(DeleteRangeRequest.newBuilder().setKey(bsKey).build())
                .put(PutRequest.newBuilder().setKey(bsKey).setValue(valueToJsonByteString(updatedValue)))
                .sync();

        return oldValue;
    }

    public void delete(KeyEntity key) {
        DeleteRangeResponse response = kvClient.delete(key.getByteString()).sync();
        assert(response.getDeleted() <= 1); // key should be unique to single entry, ok if doesn't exist
    }

    public <T> ByteString valueToJsonByteString(T value) {
        try {
            // writeValueAsBytes uses utf8 encoding.
            return ByteString.copyFrom(mapper.writer().writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T valueFromJsonByteString(ByteString value, Class<T> clazz) {
        try {
            // autodetects encoding according to json spec.
            return mapper.readerFor(clazz).readValue(value.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
