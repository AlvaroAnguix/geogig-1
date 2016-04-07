/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.repository.WorkingTree;
import org.opengis.feature.Feature;

import com.google.common.base.Supplier;

/**
 * Imports feature tress (layers) into a repository from a GeoTools {@link DataStore}. This
 * operation essentially decorates the {@link ImportOp} operation by calling {@link AddOp} followed
 * by {@link CommitOp} to make the import atomic.
 *
 */
public class DataStoreImportOp extends AbstractGeoGigOp<RevCommit> {

    /**
     * Extends the {@link com.google.common.base.Supplier} interface to provide for a way to request
     * resource cleanup, if applicable to the {@link org.geotools.data.DataStore DataStore}.
     */
    public static interface DataStoreSupplier extends Supplier<DataStore> {
        /**
         * Called after {@link DataStore#dispose()} on the supplied data store to clean up any
         * resource needed after {@link DataStoreImportOp} finished.
         */
        void cleanupResources();
    }

    private DataStoreSupplier dataStoreSupplier;

    // commit options
    @Nullable
    private String authorEmail;

    @Nullable
    private String authorName;

    @Nullable
    private String commitMessage;

    // import options
    @Nullable // except if all == false
    private String table;

    private boolean all = false;

    private boolean add = false;

    private boolean alter = false;

    private boolean forceFeatureType = false;

    @Nullable
    private String dest;

    @Nullable
    private String fidAttribute;

    /**
     * Set the source {@link DataStore DataStore}, from which features should be imported.
     *
     * @param dataStore A {@link com.google.common.base.Supplier Supplier} for the source
     *        {@link DataStore DataStore} containing features to import into the repository.
     * @return A reference to this Operation.
     */
    public DataStoreImportOp setDataStore(DataStoreSupplier dataStore) {
        this.dataStoreSupplier = dataStore;
        return this;
    }

    @Override
    protected RevCommit _call() {

        final DataStore dataStore = dataStoreSupplier.get();

        RevCommit revCommit;

        /**
         * Import needs to: 1) Import the data 2) Add changes to be staged 3) Commit staged changes
         */
        try {
            // import data into the repository
            final ImportOp importOp = getImportOp(dataStore);
            importOp.setProgressListener(getProgressListener());
            final RevTree revTree = importOp.call();
            // add the imported data to the staging area
            final WorkingTree workingTree = callAdd();
            // commit the staged changes
            revCommit = callCommit();
        } finally {
            dataStore.dispose();
            dataStoreSupplier.cleanupResources();
        }

        return revCommit;
    }

    private WorkingTree callAdd() {
        final AddOp addOp = context.command(AddOp.class);
        addOp.setProgressListener(getProgressListener());
        return addOp.call();
    }

    private RevCommit callCommit() {
        final CommitOp commitOp = context.command(CommitOp.class).setAll(true)
                .setAuthor(authorName, authorEmail).setMessage(commitMessage);
        commitOp.setProgressListener(getProgressListener());
        return commitOp.call();
    }

    private ImportOp getImportOp(DataStore dataStore) {
        final ImportOp importOp = context.command(ImportOp.class);
        return importOp.setDataStore(dataStore).setTable(table).setAll(all).setOverwrite(!add)
                .setAdaptToDefaultFeatureType(!forceFeatureType).setAlter(alter)
                .setDestinationPath(dest).setFidAttribute(fidAttribute);
    }

    /**
     * Set the email address of the committer in the commit.
     * <p>
     * After the import completes, this operation will add the effective changes to the staging area
     * and then commit the changes. Setting this value will be reflected in the commit. Setting this
     * value is optional, but highly recommended for tracking changes. There is no default value for
     * authorEmail, and will default to the default {@link CommitOp} mechanism for resolving the
     * author email from the config database.
     *
     * @param authorEmail Email address of the committing author. Example: "john.doe@example.com"
     *
     * @return A reference to this operation.
     *
     * @see org.locationtech.geogig.api.porcelain.CommitOp#setAuthor(java.lang.String,
     *      java.lang.String)
     */
    public DataStoreImportOp setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
        return this;
    }

    /**
     * Set the Author name of the committer in the commit.
     * <p>
     * After the import completes, this operation will add the effective changes to the staging area
     * and then commit the changes. Setting this value will be reflected in the commit. Setting this
     * value is optional, but highly recommended for tracking changes. There is no default value for
     * authorName, and will default to the default {@link CommitOp} mechanism for resolving the
     * author name from the config database.
     *
     * @param authorName The first and last name of the committing author. Example: "John Doe"
     *
     * @return A reference to this operation.
     *
     * @see org.locationtech.geogig.api.porcelain.CommitOp#setAuthor(java.lang.String,
     *      java.lang.String)
     */
    public DataStoreImportOp setAuthorName(String authorName) {
        this.authorName = authorName;
        return this;
    }

    /**
     * Set the commit message.
     * <p>
     * After the import completes, this operation will add the effective changes to the staging area
     * and then commit the changes. Setting this value will be reflected in the commit. Setting this
     * value is optional, but highly recommended for tracking changes. There is no default commit
     * message.
     *
     * @param commitMessage The commit message for the commit. Example: "Update Buildings layer with
     *        new campus buildings"
     *
     * @return A reference to this operation.
     *
     * @see org.locationtech.geogig.api.porcelain.CommitOp#setMessage(java.lang.String)
     */
    public DataStoreImportOp setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
        return this;
    }

    /**
     * Controls whether to import on top of existing features, or truncate the destination feature
     * tree before importing.
     * <p>
     * If set to {@code true}, the import proceeds on top of the existing feature. New features will
     * be recognized as added and existing features (matching feature ids) as modifications, but
     * it's impossible to identify deleted features.
     * <p>
     * If set to {@code false}, the existing feature tree for a given layer is first truncated, and
     * then the import proceeds on top of the emptied feature tree. The end result is that, compared
     * to the previous commit, both adds, modifications, and deletes are recognized by a diff
     * operation. Note however this is only practical if the whole layer is being re-imported.
     *
     * @param add {@code true} if features should be imported on top a pre-existing feature tree (if
     *        such exists) matching the imported layer name, {@code false} if the import shall
     *        proceed on an empty feature tree (truncating it first if such feature tree exists).
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setOverwrite(boolean)
     */
    public DataStoreImportOp setAdd(boolean add) {
        this.add = add;
        return this;
    }

    /**
     * Sets the all flag, that then true, all tables from the source datastore will be imported into
     * the repository.
     * <p>
     * If set to false, {@link DataStoreImportOp#setTable(java.lang.String)} must be used to set a
     * specific table to import. Attributes <b>all</b> and <b>table</b> are mutually exclusive. You
     * <b>MUST</b> set one and leave the other unset. The default is false.
     *
     * @param all True if all tables from the datastore should be imported into the repository,
     *        false if only a specified table should be imported.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setAll(boolean)
     * @see ImportOp#setTable(java.lang.String)
     * @see DataStoreImportOp#setTable(java.lang.String)
     */
    public DataStoreImportOp setAll(boolean all) {
        this.all = all;
        return this;
    }

    /**
     * Sets the alter flag.
     * <p>
     * If set to true, this import operation will set the default feature type of the repository
     * path destination to match the feature type of the features being imported, and <b>alter</b>
     * the feature type of all features in the destination to match the feature type of the features
     * being imported. The default is false.
     *
     * @param alter True if this import operation should alter the feature type of the repository
     *        path destination to the feature type of the features being imported, false if no
     *        altering should occur.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setAlter(boolean)
     */
    public DataStoreImportOp setAlter(boolean alter) {
        this.alter = alter;
        return this;
    }

    /**
     * Sets the attribute from which the Feature Id should be created.
     * <p>
     * If not set, the Feature Id provided by each {@link Feature#getIdentifier()} is used. The
     * default is <b>null</b> and uses the default method in {@link ImportOp ImportOp}.
     * <p>
     * This is useful when the source DataStore can't provide stable feature ids (e.g. the Shapefile
     * datastore)
     *
     * @param fidAttribute The Attribute from which the Feature Id should be created. Null if the
     *        default Feature Id creation should be used.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setFidAttribute(java.lang.String)
     */
    public DataStoreImportOp setFidAttribute(String fidAttribute) {
        this.fidAttribute = fidAttribute;
        return this;
    }

    /**
     * Sets the table name within the source DataStore from which features should be imported.
     * <p>
     * If a table name is set, the <b>all</b> flag must NOT be set. If no table name is set,
     * {@link DataStoreImportOp#setAll(boolean)} must be used to set <b>all</b> to true to import
     * all tables. Attributes <b>all</b> and <b>table</b> are mutually exclusive. You <b>MUST</b>
     * set one and leave the other unset. The default is null/unset.
     *
     * @param table The name of the table within the source DataStore from which features should be
     *        imported, NULL if all tables should be imported.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setTable(java.lang.String)
     * @see ImportOp#setAll(boolean)
     * @see DataStoreImportOp#setAll(boolean)
     */
    public DataStoreImportOp setTable(String table) {
        this.table = table;
        return this;
    }

    /**
     * Sets the Force Feature Type flag.
     * <p>
     * If set to true, use the feature type of the features to be imported from the source
     * DataStore, even if it does not match the default feature type of the repository destination
     * path. If set to false, this import operation will try to adapt the features being imported to
     * the feature type of the repository destination path, if it is not the same. The default is
     * false. NOTE: this flag behaves as the inverse of
     * {@link ImportOp#setAdaptToDefaultFeatureType(boolean)}
     *
     * @param forceFeatureType True if the source feature type should be used on import, false if
     *        the destination feature type should be used on import.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setAdaptToDefaultFeatureType(boolean)
     */
    public DataStoreImportOp setForceFeatureType(boolean forceFeatureType) {
        this.forceFeatureType = forceFeatureType;
        return this;
    }

    /**
     * Sets the repository destination path.
     * <p>
     * If this value is set, the value provided will be used as the repository destination path name
     * on import. If not set, the path name will be derived from the feature table being imported.
     * The default is null/unset.
     *
     * @param dest The name of the repository destination path into which features should be
     *        imported, or null if the path should be derived from the table name of the features
     *        being imported.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setDestinationPath(java.lang.String)
     */
    public DataStoreImportOp setDest(String dest) {
        this.dest = dest;
        return this;
    }
}
