# Simple Healthcheck gRPC server w/ etcd deep check

Example gRPC setup for a server that displays:
* Basic healthcheck
```
{ status: "OK" }
```
* Deep healthcheck
```
{
  status: "UNHEALTHY",
  db_health: {
    nodeCount: 3,
    upNodes: ["node1", "node3"],
    downNodes: ["node2"],
    leaderId: "node1"
  }
}
```

## Usage
TBD
* executable jar generated via `mvn clean install` and used in Dockerfile for app launch
* docker-compose.yaml for etcd cluster setup (standalone to start) + server itself

## Testing
```
docker-compose up -d
docker exec -it <etcdContainerId> bash
$ etcdctl member list
```
Links:
* client test -- https://java-demos.blogspot.com/2018/12/persisting-key-value-in-etcd-using.html
* etcdctl output -- https://stackoverflow.com/questions/63433622/is-the-following-output-of-etcdctl-member-list-correct-and-etcd-cluster-is-in
   * learner nodes for snapshot replication prior to entering quorum
   
### Learner nodes
Standby nodes added to cluster for replicating leader logs, but not part of quorum until explicitly promoted. Can only
be promoted once replication has complete. Must be explicit. Using etcd 3.4 API, which requires the `--learner` flag to
be added to `member add` command. Following https://chromium.googlesource.com/external/github.com/coreos/etcd/+/HEAD/Documentation/learning/design-learner.md#features-in-v3_5
this will be the default behavior in 3.5 API

https://chromium.googlesource.com/external/github.com/coreos/etcd/+/HEAD/Documentation/op-guide/runtime-configuration.md#add-a-new-member-as-learner
```
etcdctl member add --learner
// replication 
etcdctl member promote
```

### Feature Requests
TODO list of nice-to-have features to add to this example.
* Prometheus monitoring (https://etcd.io/docs/v3.4.0/op-guide/monitoring/)

## Resources
gRPC
* https://grpc.io/docs/languages/java/basics/
* https://github.com/grpc/grpc-java/tree/master/examples/src/main/java/io/grpc/examples/routeguide

Clustering
* https://kubernetes.io/docs/tasks/administer-cluster/configure-upgrade-etcd/
* https://github.com/etcd-io/etcd/blob/master/Documentation/op-guide/clustering.md
* https://etcd.io/docs/v3.4.0/dev-guide/local_cluster/

etcd
* https://etcd.io/docs/v3.3.12/dev-guide/api_reference_v3/
* https://github.com/etcd-io/etcd/blob/master/api/etcdserverpb/rpc.proto

Ops
* https://docs.okd.io/3.10/admin_guide/assembly_replace-etcd-member.html
* https://etcd.io/docs/v3.4.0/learning/api/
* https://etcd.io/docs/v3.4.0/op-guide/maintenance/
