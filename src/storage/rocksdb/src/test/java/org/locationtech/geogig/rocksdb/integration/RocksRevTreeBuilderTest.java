/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb.integration;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilderTest;
import org.locationtech.geogig.rocksdb.RocksdbObjectStore;
import org.locationtech.geogig.storage.ObjectStore;

public class RocksRevTreeBuilderTest extends CanonicalTreeBuilderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    protected @Override ObjectStore createObjectStore() {
        File dbdir;
        try {
            dbdir = tmp.newFolder(".geogig");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        RocksdbObjectStore store = new RocksdbObjectStore(dbdir, false);
        return store;
    }
}
