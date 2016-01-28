/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.di.PluginDefaults;
import org.locationtech.geogig.di.VersionedFormat;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryInitializer;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.inject.Inject;

/**
 * Creates or "initializes" a repository in the {@link Platform#pwd() working directory}.
 * <p>
 * This command tries to find an existing {@code .geogig} repository directory in the current
 * directory's hierarchy. It is safe to call it from inside a directory that's a child of a
 * repository.
 * <p>
 * If no repository directory is found, then a new one is created on the current directory.
 * 
 * @see ResolveGeogigURI
 * @see RefParse
 * @see UpdateRef
 * @see UpdateSymRef
 */
@CanRunDuringConflict
public class InitOp extends AbstractGeoGigOp<Repository> {

    private Map<String, String> config;

    private PluginDefaults defaults;

    private String filterFile;

    @Nullable
    private File targetDir;

    private Hints hints;

    /**
     * Constructs a new {@code InitOp} with the specified parameters.
     * 
     * @param platform where to get the current directory from
     * @param hints may contain where to get the repository from (using the
     *        {@link Hints#REPOSITORY_URL} argument)
     */
    @Inject
    public InitOp(PluginDefaults defaults, Hints hints) {
        checkNotNull(defaults);
        this.defaults = defaults;
        this.config = Maps.newTreeMap();
        this.hints = hints;
    }

    public InitOp setConfig(Map<String, String> suppliedConfiguration) {
        this.config = ImmutableMap.copyOf(suppliedConfiguration);
        return this;
    }

    public InitOp setTarget(File targetRepoDirectory) {
        this.targetDir = targetRepoDirectory;
        return this;
    }

    public InitOp setFilterFile(String filterFile) {
        this.filterFile = filterFile;
        return this;
    }

    /**
     * Executes the Init operation.
     * 
     * @return the initialized repository
     * @throws IllegalStateException if a repository cannot be created on the current directory or
     *         re-initialized in the current dir or one if its parents as determined by
     *         {@link ResolveGeogigURI}
     */
    @Override
    protected Repository _call() {
        final Platform platform = platform();
        final File workingDirectory = platform.pwd();
        checkState(workingDirectory != null, "working directory is null");

        final File targetDir = this.targetDir == null ? workingDirectory : this.targetDir;
        Repository repository;
        try {
            repository = callInternal(targetDir);
        } finally {
            // restore current directory
            platform.setWorkingDir(workingDirectory);
        }
        return repository;
    }

    private Repository callInternal(File targetDir) {
        final Platform platform = platform();
        final Optional<URI> resolvedURI = new ResolveGeogigURI(platform, hints).call();

        URI repoURI;
        if (resolvedURI.isPresent()) {
            repoURI = resolvedURI.get();
        } else {
            repoURI = targetDir.toURI();
        }
        RepositoryInitializer repoInitializer = RepositoryInitializer.lookup(repoURI);
        final boolean repoExisted = repoInitializer.repoExists(repoURI);

        repoInitializer.initialize(repoURI, context());

        Map<String, String> effectiveConfigBuilder = Maps.newTreeMap();
        addDefaults(defaults, effectiveConfigBuilder);
        if (config != null) {
            effectiveConfigBuilder.putAll(config);
        }

        if (filterFile != null) {
            try {
                final String FILTER_FILE = "filter.ini";

                File oldFilterFile = new File(filterFile);
                if (!oldFilterFile.exists()) {
                    throw new FileNotFoundException("No filter file found at " + filterFile + ".");
                }

                Optional<URI> envHomeURL = new ResolveGeogigURI(platform, hints).call();
                Preconditions.checkState(envHomeURL.isPresent(), "Not inside a geogig directory");
                final URI url = envHomeURL.get();
                if (!"file".equals(url.getScheme())) {
                    throw new UnsupportedOperationException(
                            "Sparse clone works only against file system repositories. "
                                    + "Repository location: " + url);
                }

                File repoDir;
                try {
                    repoDir = new File(url);
                } catch (Exception e) {
                    throw new IllegalStateException("Unable to access directory " + url, e);
                }

                File newFilterFile = new File(repoDir, FILTER_FILE);

                Files.copy(oldFilterFile, newFilterFile);
                effectiveConfigBuilder.put("sparse.filter", FILTER_FILE);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to copy filter file at path " + filterFile
                        + " to the new repository.", e);
            }
        }
        //
        // Preconditions.checkState(envHome.toURI().equals(
        // new ResolveGeogigDir(platform, hints).call().get()));

        Repository repository;
        try {
            if (!repoExisted) {
                ConfigDatabase configDB = context.configDatabase();
                try {
                    for (Entry<String, String> pair : effectiveConfigBuilder.entrySet()) {
                        String key = pair.getKey();
                        String value = pair.getValue();
                        configDB.put(key, value);
                    }
                    repository = repository();
                    repository.configure();
                } catch (RepositoryConnectionException e) {
                    throw new IllegalStateException(
                            "Unable to initialize repository for the first time: " + e.getMessage(),
                            e);
                }
            } else {
                repository = repository();
            }
            try {
                repository.open();
                // make sure the repo has the empty tree
                ObjectStore objectDatabase = repository.objectDatabase();
                objectDatabase.put(RevTree.EMPTY);
            } catch (RepositoryConnectionException e) {
                throw new IllegalStateException("Error opening repository databases: "
                        + e.getMessage(), e);
            }
        } catch (ConfigException e) {
            throw e;
        } catch (RuntimeException e) {
            Throwables.propagateIfInstanceOf(e, IllegalStateException.class);
            throw new IllegalStateException("Can't access repository at '" + repoURI + "'", e);
        }

        if (!repoExisted) {
            try {
                createDefaultRefs();
            } catch (IllegalStateException e) {
                Throwables.propagate(e);
            }
        }
        return repository;
    }

    private void addDefaults(PluginDefaults defaults, Map<String, String> configProps) {
        Optional<VersionedFormat> refs = defaults.getRefs();
        Optional<VersionedFormat> objects = defaults.getObjects();
        Optional<VersionedFormat> graph = defaults.getGraph();
        if (refs.isPresent()) {
            configProps.put("storage.refs", refs.get().getFormat());
            configProps.put(refs.get().getFormat() + ".version", refs.get().getVersion());
        }
        if (objects.isPresent()) {
            configProps.put("storage.objects", objects.get().getFormat());
            configProps.put(objects.get().getFormat() + ".version", objects.get().getVersion());
        }
        if (graph.isPresent()) {
            configProps.put("storage.graph", graph.get().getFormat());
            configProps.put(graph.get().getFormat() + ".version", graph.get().getVersion());
        }
    }

    private void createDefaultRefs() {
        Optional<Ref> master = command(RefParse.class).setName(Ref.MASTER).call();
        Preconditions.checkState(!master.isPresent(), Ref.MASTER + " was already initialized.");
        command(UpdateRef.class).setName(Ref.MASTER).setNewValue(ObjectId.NULL)
                .setReason("Repository initialization").call();

        Optional<Ref> head = command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions.checkState(!head.isPresent(), Ref.HEAD + " was already initialized.");
        command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(Ref.MASTER)
                .setReason("Repository initialization").call();

        Optional<Ref> workhead = command(RefParse.class).setName(Ref.WORK_HEAD).call();
        Preconditions
                .checkState(!workhead.isPresent(), Ref.WORK_HEAD + " was already initialized.");
        command(UpdateRef.class).setName(Ref.WORK_HEAD).setNewValue(RevTree.EMPTY.getId())
                .setReason("Repository initialization").call();

        Optional<Ref> stagehead = command(RefParse.class).setName(Ref.STAGE_HEAD).call();
        Preconditions.checkState(!stagehead.isPresent(), Ref.STAGE_HEAD
                + " was already initialized.");
        command(UpdateRef.class).setName(Ref.STAGE_HEAD).setNewValue(RevTree.EMPTY.getId())
                .setReason("Repository initialization").call();

    }
}
