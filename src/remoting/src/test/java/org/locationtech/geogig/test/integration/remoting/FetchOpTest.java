/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration.remoting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.BranchDeleteOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.TagCreateOp;
import org.locationtech.geogig.porcelain.TagListOp;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.remotes.FetchOp;
import org.locationtech.geogig.remotes.RemoteRemoveOp;
import org.locationtech.geogig.test.TestSupport;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * {@link FetchOp} integration test suite for full clones (for shallow and sparse clones see
 * {@link ShallowCloneTest} and {@link SparseCloneTest})
 *
 */
public class FetchOpTest extends RemoteRepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    LinkedList<RevCommit> expectedMaster;

    LinkedList<RevCommit> expectedBranch;

    @Override
    protected void setUpInternal() throws Exception {
    }

    private void prepareForFetch(boolean doClone) throws Exception {
        if (doClone) {
            // clone the repository
            CloneOp clone = cloneOp();
            // clone.setRepositoryURL(remoteGeogig.envHome.toURI().toString()).call();
            clone.setRemoteURI(remoteGeogig.envHome.toURI())
                    .setCloneURI(localGeogig.envHome.toURI()).call();
        }

        // Commit several features to the remote

        expectedMaster = new LinkedList<RevCommit>();
        expectedBranch = new LinkedList<RevCommit>();

        insertAndAdd(remoteGeogig.geogig, points1);
        RevCommit commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);
        expectedBranch.addFirst(commit);

        // Create and checkout branch1
        remoteGeogig.geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("Branch1")
                .call();

        // Commit some changes to branch1
        insertAndAdd(remoteGeogig.geogig, points2);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        insertAndAdd(remoteGeogig.geogig, points3);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedBranch.addFirst(commit);

        // Make sure Branch1 has all of the commits
        Iterator<RevCommit> logs = remoteGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = Lists.newArrayList(logs);
        assertEquals(expectedBranch, logged);

        // Checkout master and commit some changes
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("master").call();

        insertAndAdd(remoteGeogig.geogig, lines1);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        insertAndAdd(remoteGeogig.geogig, lines2);
        commit = remoteGeogig.geogig.command(CommitOp.class).call();
        expectedMaster.addFirst(commit);

        remoteGeogig.geogig.command(TagCreateOp.class) //
                .setMessage("TestTag") //
                .setCommitId(commit.getId()) //
                .setName("test") //
                .call();

        // Make sure master has all of the commits
        logs = remoteGeogig.geogig.command(LogOp.class).call();
        logged = Lists.newArrayList(logs);
        assertEquals(expectedMaster, logged);
    }

    private void verifyFetch() throws Exception {
        // Make sure the local repository got all of the commits from master
        localGeogig.geogig.command(CheckoutOp.class).setSource("refs/remotes/origin/master").call();
        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = Lists.newArrayList(logs);

        assertEquals(expectedMaster, logged);

        // Make sure the local repository got all of the commits from Branch1
        localGeogig.geogig.command(CheckoutOp.class).setSource("refs/remotes/origin/Branch1")
                .call();
        logs = localGeogig.geogig.command(LogOp.class).call();
        logged = Lists.newArrayList(logs);
        assertEquals(expectedBranch, logged);

        List<RevTag> tags = localGeogig.geogig.command(TagListOp.class).call();
        assertEquals(1, tags.size());

        TestSupport.verifyRepositoryContents(localGeogig.geogig.getRepository());
    }

    private void verifyPrune() throws Exception {
        // Make sure the local repository got all of the commits from master
        localGeogig.geogig.command(CheckoutOp.class).setForce(true)
                .setSource("refs/remotes/origin/master").call();
        Iterator<RevCommit> logs = localGeogig.geogig.command(LogOp.class).call();
        List<RevCommit> logged = Lists.newArrayList(logs);

        assertEquals(expectedMaster, logged);

        // Make sure the local repository no longer has Branch1
        Optional<Ref> missing = localGeogig.geogig.command(RefParse.class)
                .setName("refs/remotes/origin/Branch1").call();

        assertFalse(missing.isPresent());
    }

    @Test
    public void testFetch() throws Exception {

        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetchOp();
        fetch.call();

        verifyFetch();
    }

    @Test
    public void testFetchAll() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetchOp();
        fetch.setAll(true).call();

        verifyFetch();
    }

    @Test
    public void testFetchSpecificRemote() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetchOp();
        fetch.addRemote("origin").call();

        verifyFetch();
    }

    @Test
    public void testFetchSpecificRemoteAndAll() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetchOp();
        fetch.addRemote("origin").setAll(true).call();

        verifyFetch();
    }

    @Test
    public void testFetchNoRemotes() throws Exception {
        localGeogig.geogig.command(RemoteRemoveOp.class).setName(REMOTE_NAME).call();
        FetchOp fetch = fetchOp();
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Remote could not be resolved");
        fetch.call();
    }

    @Test
    public void testFetchNoChanges() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetchOp();
        fetch.addRemote("origin").setAll(true).call();

        verifyFetch();

        // fetch again
        fetch.call();

        verifyFetch();
    }

    @Test
    public void testFetchWithPrune() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetchOp();
        fetch.addRemote("origin").setAll(true).call();

        verifyFetch();

        // Remove a branch from the remote
        remoteGeogig.geogig.command(BranchDeleteOp.class).setName("Branch1").call();

        // fetch again
        fetch = fetchOp();
        fetch.setPrune(true).call();

        verifyPrune();
    }

    @Test
    public void testFetchWithPruneAndBranchAdded() throws Exception {
        prepareForFetch(true);

        // fetch from the remote
        FetchOp fetch = fetchOp();
        fetch.addRemote("origin").setAll(true).call();

        verifyFetch();

        // Remove a branch from the remote
        remoteGeogig.geogig.command(BranchDeleteOp.class).setName("Branch1").call();

        // Add another branch
        remoteGeogig.geogig.command(BranchCreateOp.class).setName("Branch2").call();

        // fetch again
        fetch = fetchOp();
        fetch.setPrune(true).call();

        verifyPrune();

        // Make sure the local repository has Branch2
        Optional<Ref> missing = localGeogig.geogig.command(RefParse.class)
                .setName("refs/remotes/origin/Branch2").call();

        assertTrue(missing.isPresent());
    }
}
