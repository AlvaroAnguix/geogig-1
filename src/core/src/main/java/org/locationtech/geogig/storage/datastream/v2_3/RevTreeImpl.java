/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.AbstractRevObject;

import com.google.common.collect.ImmutableList;

import lombok.NonNull;

class RevTreeImpl implements RevTree {

    private final ObjectId id;

    final DataBuffer data;

    public RevTreeImpl(@NonNull ObjectId id, @NonNull DataBuffer dataBuffer) {
        this.id = id;
        this.data = dataBuffer;
    }

    /**
     * Equality is based on id
     * 
     * @see AbstractRevObject#equals(Object)
     */
    public @Override boolean equals(Object o) {
        if (!(o instanceof RevTree)) {
            return false;
        }
        return id.equals(((RevTree) o).getId());
    }

    public @Override ObjectId getId() {
        return id;
    }

    public @Override long size() {
        return RevTreeFormat.size(data);
    }

    public @Override int numTrees() {
        return RevTreeFormat.numChildTrees(data);
    }

    public @Override ImmutableList<Node> trees() {
        return RevTreeFormat.trees(data);
    }

    public @Override ImmutableList<Node> features() {
        return RevTreeFormat.features(data);
    }

    public @Override Iterable<Bucket> getBuckets() {
        return RevTreeFormat.buckets(data).values();
    }
}
