/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.net.URI;
import java.util.Optional;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.RepositoryFinder;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.storage.ConfigDatabase;

/**
 * Resolves the name of the repository.
 * <p>
 * The name can be configured through the {@link ConfigDatabase}, but will default to the repository
 * location.
 */
public class ResolveRepositoryName extends AbstractGeoGigOp<String> {

    /**
     * @return the name of the repository
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     */
    protected @Override String _call() {
        Optional<String> repoName = configDatabase().get("repo.name");
        if (repoName.isPresent()) {
            return repoName.get();
        }
        URI repoURI = repository().getLocation();
        RepositoryResolver resolver = RepositoryFinder.INSTANCE.lookup(repoURI);
        return resolver.getName(repoURI);
    }
}
