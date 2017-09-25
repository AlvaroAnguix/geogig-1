/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes;

import static com.google.common.base.Preconditions.checkArgument;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.remotes.internal.RemoteResolver;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

/**
 * Connects to the specified remote, retrieves its {@link Ref refs}, closes the remote connection
 * and returns the list of remote references.
 */
public class LsRemoteOp extends AbstractGeoGigOp<ImmutableSet<Ref>> {

    // optional, if not supplied #remoteRepo is mandatory
    private Supplier<Optional<Remote>> remote;

    // optional, if not supplied #remote is mandatory, if supplied #local must be false
    private IRemoteRepo remoteRepo;

    private boolean getHeads;

    private boolean getTags;

    private boolean local;

    /**
     * Constructs a new {@code LsRemote}.
     */
    public LsRemoteOp() {
        this.remote = Suppliers.ofInstance(Optional.absent());
        this.getHeads = true;
        this.getTags = true;
    }

    /**
     * @param remote the remote whose refs should be listed
     * @return {@code this}
     */
    public LsRemoteOp setRemote(Supplier<Optional<Remote>> remote) {
        this.remote = remote;
        this.remoteRepo = null;
        return this;
    }

    public LsRemoteOp setRemote(IRemoteRepo remoteRepo) {
        this.remoteRepo = remoteRepo;
        this.remote = Suppliers.ofInstance(Optional.absent());
        return this;
    }

    /**
     * Find the remote to be listed
     */
    public Optional<Remote> getRemote() {
        return remote.get();
    }

    /**
     * @param getHeads tells whether to retrieve remote heads, defaults to {@code true}
     * @return {@code this}
     */
    public LsRemoteOp retrieveHeads(boolean getHeads) {
        this.getHeads = getHeads;
        return this;
    }

    /**
     * @param getTags tells whether to retrieve remote tags, defaults to {@code true}
     * @return {@code this}
     */
    public LsRemoteOp retrieveTags(boolean getTags) {
        this.getTags = getTags;
        return this;
    }

    /**
     * @param local if {@code true} retrieves the refs of the remote repository known to the local
     *        repository instead (i.e. those under the {@code refs/remotes/<remote name>} namespace
     *        in the local repo. Defaults to {@code false}
     * @return {@code this}
     */
    public LsRemoteOp retrieveLocalRefs(boolean local) {
        this.local = local;
        return this;
    }

    /**
     * Lists all refs for the given remote.
     * 
     * @return an immutable set of the refs for the given remote
     */
    @Override
    protected ImmutableSet<Ref> _call() {
        final Remote remoteConfig = this.remote.get().orNull();

        Preconditions.checkState(remoteRepo != null || remoteConfig != null,
                "Remote was not provided");

        if (local) {
            checkArgument(remoteConfig != null,
                    "if retrieving local remote refs, a Remote must be provided");
            return locallyKnownRefs(remoteConfig);
        }

        ImmutableSet<Ref> remoteRefs;
        if (remoteRepo == null) {
            try (IRemoteRepo remoteRepo = openRemote(remoteConfig)) {
                getProgressListener().setDescription("Connected to remote " + remoteConfig.getName()
                        + ". Retrieving references");

                remoteRefs = remoteRepo.listRefs(getHeads, getTags);

            } catch (RepositoryConnectionException e) {
                throw Throwables.propagate(e);
            }
        } else {
            remoteRefs = remoteRepo.listRefs(getHeads, getTags);
        }
        
        return remoteRefs;
    }

    /**
     * @param remote the remote to get
     * @return an interface for the remote repository
     * @throws RepositoryConnectionException
     */
    public IRemoteRepo openRemote(Remote remote) throws RepositoryConnectionException {
        Repository localRepository = repository();
        Optional<IRemoteRepo> remoterepo;
        getProgressListener().setDescription("Obtaining remote " + remote.getName());
        remoterepo = RemoteResolver.newRemote(localRepository, remote, Hints.readOnly());
        Preconditions.checkState(remoterepo.isPresent(), "Remote could not be opened.");
        IRemoteRepo iRemoteRepo = remoterepo.get();
        getProgressListener().setDescription("Connecting to remote " + remote.getName());
        iRemoteRepo.open();
        return iRemoteRepo;
    }

    /**
     * @see ForEachRef
     */
    private ImmutableSet<Ref> locallyKnownRefs(final Remote remoteConfig) {
        Predicate<Ref> filter = new Predicate<Ref>() {
            final String prefix = Ref.REMOTES_PREFIX + remoteConfig.getName() + "/";

            @Override
            public boolean apply(Ref input) {
                return input.getName().startsWith(prefix);
            }
        };
        return command(ForEachRef.class).setFilter(filter).call();
    }

}
