/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */

package org.locationtech.geogig.remotes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.filter;
import static org.locationtech.geogig.remotes.TransferSummary.ChangedRef.ChangeTypes.ADDED_REF;
import static org.locationtech.geogig.remotes.TransferSummary.ChangedRef.ChangeTypes.CHANGED_REF;
import static org.locationtech.geogig.remotes.TransferSummary.ChangedRef.ChangeTypes.DEEPENED_REF;
import static org.locationtech.geogig.remotes.TransferSummary.ChangedRef.ChangeTypes.REMOVED_REF;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.remotes.TransferSummary.ChangedRef;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.remotes.internal.RemoteResolver;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

/**
 * Fetches named heads or tags from one or more other repositories, along with the objects necessary
 * to complete them.
 */
public class FetchOp extends AbstractGeoGigOp<TransferSummary> {

    /**
     * Immutable state of command arguments
     */
    private static class FetchArgs {

        /**
         * Builder for command arguments
         */
        private static class Builder {
            private boolean all;

            private boolean prune;

            private boolean fullDepth = false;

            private List<Remote> remotes = new ArrayList<Remote>();

            private Optional<Integer> depth = Optional.absent();

            public FetchArgs build(Repository repo) {
                if (all) {
                    remotes.clear();
                    // Add all remotes to list.
                    ImmutableList<Remote> localRemotes = repo.command(RemoteListOp.class).call();
                    remotes.addAll(localRemotes);
                } else if (remotes.isEmpty()) {
                    // If no remotes are specified, default to the origin remote
                    Optional<Remote> origin;
                    origin = repo.command(RemoteResolve.class)
                            .setName(NodeRef.nodeFromPath(Ref.ORIGIN)).call();
                    checkArgument(origin.isPresent(), "Remote could not be resolved.");
                    remotes.add(origin.get());
                }

                final Optional<Integer> repoDepth = repo.getDepth();
                if (repoDepth.isPresent()) {
                    if (fullDepth) {
                        depth = Optional.of(Integer.MAX_VALUE);
                    }
                    if (depth.isPresent()) {
                        if (depth.get() > repoDepth.get()) {
                            repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET)
                                    .setScope(ConfigScope.LOCAL)
                                    .setName(Repository.DEPTH_CONFIG_KEY)
                                    .setValue(depth.get().toString()).call();
                        }
                    }
                } else if (depth.isPresent() || fullDepth) {
                    // Ignore depth, this is a full repository
                    depth = Optional.absent();
                    fullDepth = false;
                }

                return new FetchArgs(all, prune, fullDepth, ImmutableList.copyOf(remotes), depth);
            }

        }

        final boolean all;

        final boolean prune;

        final boolean fullDepth;

        final ImmutableList<Remote> remotes;

        final Optional<Integer> depth;

        private FetchArgs(boolean all, boolean prune, boolean fullDepth,
                ImmutableList<Remote> remotes, Optional<Integer> depth) {
            this.all = all;
            this.prune = prune;
            this.fullDepth = fullDepth;
            this.remotes = remotes;
            this.depth = depth;
        }
    }

    private FetchArgs.Builder argsBuilder = new FetchArgs.Builder();

    /**
     * @param all if {@code true}, fetch from all remotes.
     * @return {@code this}
     */
    public FetchOp setAll(final boolean all) {
        argsBuilder.all = all;
        return this;
    }

    public boolean isAll() {
        return argsBuilder.all;
    }

    /**
     * @param prune if {@code true}, remote tracking branches that no longer exist will be removed
     *        locally.
     * @return {@code this}
     */
    public FetchOp setPrune(final boolean prune) {
        argsBuilder.prune = prune;
        return this;
    }

    public boolean isPrune() {
        return argsBuilder.prune;
    }

    /**
     * If no depth is specified, fetch will pull all history from the specified ref(s). If the
     * repository is shallow, it will maintain the existing depth.
     * 
     * @param depth maximum commit depth to fetch
     * @return {@code this}
     */
    public FetchOp setDepth(final int depth) {
        if (depth > 0) {
            argsBuilder.depth = Optional.of(depth);
        }
        return this;
    }

    public Integer getDepth() {
        return argsBuilder.depth.orNull();
    }

    /**
     * If full depth is set on a shallow clone, then the full history will be fetched.
     * 
     * @param fulldepth whether or not to fetch the full history
     * @return {@code this}
     */
    public FetchOp setFullDepth(boolean fullDepth) {
        argsBuilder.fullDepth = fullDepth;
        return this;
    }

    public boolean isFullDepth() {
        return argsBuilder.fullDepth;
    }

    /**
     * @param remoteName the name or URL of a remote repository to fetch from
     * @return {@code this}
     */
    public FetchOp addRemote(final String remoteName) {
        checkNotNull(remoteName);
        return addRemote(command(RemoteResolve.class).setName(remoteName));
    }

    public List<String> getRemoteNames() {
        return Lists.transform(argsBuilder.remotes, (remote) -> remote.getName());
    }

    /**
     * @param remoteSupplier the remote repository to fetch from
     * @return {@code this}
     */
    public FetchOp addRemote(Supplier<Optional<Remote>> remoteSupplier) {
        checkNotNull(remoteSupplier);
        Optional<Remote> remote = remoteSupplier.get();
        checkArgument(remote.isPresent(), "Remote could not be resolved.");
        argsBuilder.remotes.add(remote.get());

        return this;
    }

    public List<Remote> getRemotes() {
        return ImmutableList.copyOf(argsBuilder.remotes);
    }

    /**
     * Executes the fetch operation.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     */
    @Override
    protected TransferSummary _call() {
        final Repository repository = repository();
        final FetchArgs args = argsBuilder.build(repository);

        getProgressListener().started();

        TransferSummary result = new TransferSummary();

        for (Remote remote : args.remotes) {
            List<ChangedRef> needUpdate = fetch(remote, args);
            if (!needUpdate.isEmpty()) {
                String fetchURL = remote.getFetchURL();
                result.addAll(fetchURL, needUpdate);
            }
        }

        if (args.fullDepth) {
            // The full history was fetched, this is no longer a shallow clone
            command(ConfigOp.class)//
                    .setAction(ConfigAction.CONFIG_UNSET)//
                    .setScope(ConfigScope.LOCAL)//
                    .setName(Repository.DEPTH_CONFIG_KEY)//
                    .call();
        }

        getProgressListener().complete();

        return result;
    }

    private List<ChangedRef> fetch(Remote remote, FetchArgs args) {

        List<ChangedRef> needUpdate;

        try (IRemoteRepo remoteRepo = openRemote(remote)) {
            final Repository repository = repository();
            final ImmutableSet<Ref> remoteRemoteRefs = getRemoteRefs(remoteRepo, args, remote);
            final ImmutableSet<Ref> localRemoteRefs = getRemoteLocalRefs(remote);

            // If we have specified a depth to pull, we may have more history to pull from
            // existing
            // refs.
            needUpdate = findOutdatedRefs(remote, remoteRemoteRefs, localRemoteRefs, args.depth);
            if (args.prune) {
                prune(remoteRemoteRefs, localRemoteRefs, needUpdate);
            }
            for (ChangedRef ref : filter(needUpdate, (r) -> r.getType() != REMOVED_REF)) {
                final Optional<Integer> repoDepth = repository.getDepth();
                final boolean isShallow = repoDepth.isPresent();

                // If we haven't specified a depth, but this is a shallow repository, set
                // the fetch limit to the current repository depth.
                final Optional<Integer> newFetchLimit = args.depth.or(
                        isShallow && ref.getType() == ADDED_REF ? repoDepth : Optional.absent());

                // Fetch updated data from this ref
                final Ref newRef = ref.getNewRef();
                remoteRepo.fetchNewData(newRef, newFetchLimit, getProgressListener());

                if (isShallow && !args.fullDepth) {
                    // Update the repository depth if it is deeper than before.
                    int newDepth = repository.graphDatabase().getDepth(newRef.getObjectId());

                    if (newDepth > repoDepth.get()) {
                        command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET)
                                .setScope(ConfigScope.LOCAL).setName(Repository.DEPTH_CONFIG_KEY)
                                .setValue(Integer.toString(newDepth)).call();
                    }
                }

                // Update the ref
                Ref updatedRef = updateLocalRef(newRef, remote, localRemoteRefs);
                ref.setNewRef(updatedRef);
            }

            // Update HEAD ref
            if (!remote.getMapped()) {
                Optional<Ref> remoteHead = remoteRepo.headRef();
                if (remoteHead.isPresent() && !remoteHead.get().getObjectId().isNull()) {
                    updateLocalRef(remoteHead.get(), remote, localRemoteRefs);
                }
            }
        } catch (Exception ce) {
            throw Throwables.propagate(ce);
        }
        return needUpdate;
    }

    private void prune(final ImmutableSet<Ref> remoteRemoteRefs,
            final ImmutableSet<Ref> localRemoteRefs, List<ChangedRef> needUpdate) {
        // Delete local refs that aren't in the remote
        List<Ref> locals = new ArrayList<Ref>();
        // only branches, not tags, appear in the remoteRemoteRefs list so we will not catch
        // any tags in this check. However, we do not track which remote originally
        // provided a tag so it makes sense not to prune them anyway.
        for (Ref remoteRef : remoteRemoteRefs) {
            Optional<Ref> localRef = findLocal(remoteRef, localRemoteRefs);
            if (localRef.isPresent()) {
                locals.add(localRef.get());
            }
        }
        for (Ref localRef : localRemoteRefs) {
            if (!(localRef instanceof SymRef) && !locals.contains(localRef)) {
                // Delete the ref
                ChangedRef changedRef = new ChangedRef(localRef, null, REMOVED_REF);
                needUpdate.add(changedRef);
                command(UpdateRef.class).setDelete(true).setName(localRef.getName()).call();
            }
        }
    }

    private ImmutableSet<Ref> getRemoteLocalRefs(Remote remote) {
        final ImmutableSet<Ref> localRemoteRefs;
        localRemoteRefs = command(LsRemoteOp.class)//
                .retrieveLocalRefs(true)//
                .setRemote(Suppliers.ofInstance(Optional.of(remote)))//
                .call();
        return localRemoteRefs;
    }

    private ImmutableSet<Ref> getRemoteRefs(final IRemoteRepo remoteRepo, final FetchArgs args,
            Remote remote) {

        final Optional<Integer> repoDepth = repository().getDepth();
        final boolean getTags = !remote.getMapped() && (!repoDepth.isPresent() || args.fullDepth);

        ImmutableSet<Ref> remoteRemoteRefs;
        remoteRemoteRefs = command(LsRemoteOp.class)//
                .setRemote(remoteRepo)//
                .retrieveLocalRefs(false)//
                .retrieveTags(getTags)//
                .call();

        return remoteRemoteRefs;
    }

    /**
     * @param remote the remote to get
     * @return an interface for the remote repository
     * @throws RepositoryConnectionException
     */
    public IRemoteRepo openRemote(Remote remote) throws RepositoryConnectionException {
        Optional<IRemoteRepo> remoteRepo = RemoteResolver.newRemote(repository(), remote,
                Hints.readOnly());
        checkState(remoteRepo.isPresent(), "Failed to connect to the remote.");
        IRemoteRepo repo = remoteRepo.get();
        repo.open();
        return repo;
    }

    private Ref updateLocalRef(Ref remoteRef, Remote remote, ImmutableSet<Ref> localRemoteRefs) {
        final String refName;
        if (remoteRef.getName().startsWith(Ref.TAGS_PREFIX)) {
            refName = remoteRef.getName();
        } else {
            refName = Ref.REMOTES_PREFIX + remote.getName() + "/" + remoteRef.localName();
        }
        Ref updatedRef = remoteRef;
        if (remoteRef instanceof SymRef) {
            String targetBranch = Ref.localName(((SymRef) remoteRef).getTarget());
            String newTarget = Ref.REMOTES_PREFIX + remote.getName() + "/" + targetBranch;
            command(UpdateSymRef.class).setName(refName).setNewValue(newTarget).call();
        } else {
            ObjectId effectiveId = remoteRef.getObjectId();

            if (remote.getMapped() && !repository().commitExists(remoteRef.getObjectId())) {
                effectiveId = graphDatabase().getMapping(effectiveId);
                updatedRef = new Ref(remoteRef.getName(), effectiveId);
            }
            command(UpdateRef.class).setName(refName).setNewValue(effectiveId).call();
        }
        return updatedRef;
    }

    /**
     * Filters the remote references for the given remote that are not present or outdated in the
     * local repository
     */
    private List<ChangedRef> findOutdatedRefs(Remote remote, ImmutableSet<Ref> remoteRefs,
            ImmutableSet<Ref> localRemoteRefs, Optional<Integer> depth) {

        List<ChangedRef> changedRefs = Lists.newLinkedList();

        for (Ref remoteRef : remoteRefs) {// refs/heads/xxx or refs/tags/yyy, though we don't handle
                                          // tags yet
            if (remote.getMapped()
                    && !remoteRef.localName().equals(Ref.localName(remote.getMappedBranch()))) {
                // for a mapped remote, we are only interested in the branch we are mapped to
                continue;
            }
            Optional<Ref> local = findLocal(remoteRef, localRemoteRefs);
            if (local.isPresent()) {
                if (!local.get().getObjectId().equals(remoteRef.getObjectId())) {
                    ChangedRef changedRef = new ChangedRef(local.get(), remoteRef, CHANGED_REF);
                    changedRefs.add(changedRef);
                } else if (depth.isPresent()) {
                    int commitDepth = graphDatabase().getDepth(local.get().getObjectId());
                    if (depth.get() > commitDepth) {
                        ChangedRef changedRef = new ChangedRef(local.get(), remoteRef,
                                DEEPENED_REF);
                        changedRefs.add(changedRef);
                    }
                }
            } else {
                ChangedRef changedRef = new ChangedRef(null, remoteRef, ADDED_REF);
                changedRefs.add(changedRef);
            }
        }
        return changedRefs;
    }

    /**
     * Finds the corresponding local reference in {@code localRemoteRefs} for the given remote ref
     * 
     * @param remoteRef a ref in the {@code refs/heads} or {@code refs/tags} namespace as given by
     *        {@link LsRemoteOp} when querying a remote repository
     * @param localRemoteRefs the list of locally known references of the given remote in the
     *        {@code refs/remotes/<remote name>/} namespace
     */
    private Optional<Ref> findLocal(Ref remoteRef, ImmutableSet<Ref> localRemoteRefs) {
        if (remoteRef.getName().startsWith(Ref.TAGS_PREFIX)) {
            return command(RefParse.class).setName(remoteRef.getName()).call();
        } else {
            for (Ref localRef : localRemoteRefs) {
                if (localRef.localName().equals(remoteRef.localName())) {
                    return Optional.of(localRef);
                }
            }
            return Optional.absent();
        }
    }
}
