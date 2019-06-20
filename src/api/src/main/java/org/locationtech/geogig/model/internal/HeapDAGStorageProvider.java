/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;

class HeapDAGStorageProvider implements DAGStorageProvider {

    Map<NodeId, DAGNode> nodes;

    SortedMap<TreeId, DAG> trees;

    private ObjectStore source;

    public HeapDAGStorageProvider(ObjectStore source) {
        this.source = source;
        this.nodes = new ConcurrentHashMap<>();
        this.trees = new TreeMap<>();
    }

    public void close() {
        dispose();
    }

    public synchronized void dispose() {
        if (nodes != null) {
            nodes.clear();
            trees.clear();
            nodes = null;
            trees = null;
        }
    }

    public @Override RevTree getTree(ObjectId treeId) {
        return source.getTree(treeId);
    }

    public @Override List<DAG> getTrees(Set<TreeId> ids) throws NoSuchElementException {
        List<DAG> res = new ArrayList<>(ids.size());
        ids.forEach((id) -> {
            DAG dag = trees.get(id);
            if (dag == null) {
                throw new NoSuchElementException(id.toString());
            }
            res.add(dag);
        });
        return res;
    }

    private DAG createTree(TreeId treeId, ObjectId originalTreeId) {
        DAG dag = new DAG(treeId, originalTreeId);
        DAG existing = trees.putIfAbsent(treeId, dag);
        Preconditions.checkState(existing == null, "DAG %s[%s] already exists: %s", treeId,
                originalTreeId, existing);
        return dag;
    }

    public @Override DAG getOrCreateTree(TreeId treeId, ObjectId originalTreeId) {
        DAG dag = trees.get(treeId);
        if (dag == null) {
            dag = createTree(treeId, originalTreeId);
        }
        return dag;// .clone();
    }

    public @Override Map<NodeId, Node> getNodes(final Set<NodeId> nodeIds) {

        Map<NodeId, Node> res = new HashMap<>();
        nodeIds.forEach((nid) -> {
            DAGNode dagNode = nodes.get(nid);
            Preconditions.checkState(dagNode != null);
            Node node = dagNode.resolve(source);
            res.put(nid, node);
        });
        return res;
    }

    public @Override void saveNode(NodeId nodeId, Node node) {
        nodes.put(nodeId, DAGNode.of(node));
    }

    public @Override void saveNodes(Map<NodeId, DAGNode> nodeMappings) {
        nodes.putAll(nodeMappings);
    }

    public @Override void save(Map<TreeId, DAG> dags) {
        // trees.putAll(Maps.transformValues(dags, (d) -> d.clone()));
        trees.putAll(dags);
    }

    public long nodeCount() {
        return nodes.size();
    }

}
