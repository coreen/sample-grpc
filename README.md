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
* probably need to setup docker-compose.yaml for etcd cluster setup
* executable jar or as phase at end of maven lifecycle?

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