/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.geogig.web.functional;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.porcelain.InitOp;
import org.locationtech.geogig.cli.CLIContextBuilder;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.web.DirectoryRepositoryProvider;
import org.locationtech.geogig.web.Main;
import org.locationtech.geogig.web.api.TestData;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.w3c.dom.Document;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class FunctionalTestContext extends ExternalResource {

    /**
     * A temporary folder where to store repositories
     */
    private TemporaryFolder tempFolder;

    private Main app;

    private DirectoryRepositoryProvider repoProvider;

    private Response lastResponse;

    private Map<String, String> variables = new HashMap<>();

    @Override
    public synchronized void before() throws Exception {
        if (app == null) {
            GlobalContextBuilder.builder = new CLIContextBuilder();
            this.tempFolder = new TemporaryFolder();
            this.tempFolder.create();

            File rootFolder = tempFolder.getRoot();
            repoProvider = new DirectoryRepositoryProvider(rootFolder);
            this.app = new Main(repoProvider, true);
            this.app.start();
        }
    }

    @Override
    public synchronized void after() {
        if (app != null) {
            try {
                // this.client.stop();
                this.app.stop();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            } finally {
                this.app = null;
                tempFolder.delete();
            }
        }
    }

    public void setUpDefaultMultiRepoServer() throws Exception {
        createRepo("repo1")//
                .init("geogigUser", "repo1_Owner@geogig.org")//
                .loadDefaultData()//
                .getRepo().close();

        createRepo("repo2")//
                .init("geogigUser", "repo2_Owner@geogig.org")//
                .loadDefaultData()//
                .getRepo().close();
    }

    private TestData createRepo(final String name) throws Exception {
        URI repoURI = new File(tempFolder.getRoot(), name).toURI();
        Hints hints = new Hints();
        hints.set(Hints.REPOSITORY_URL, repoURI);
        Context repoContext = GlobalContextBuilder.builder.build(hints);
        Repository repo = repoContext.command(InitOp.class).call();
        repo.close();
        repo = RepositoryResolver.load(repoURI);
        GeoGIG geogig = new GeoGIG(repo);
        TestData testData = new TestData(geogig);
        return testData;
    }

    public void call(final Method method, String resourceUri) {

        this.lastResponse = callInternal(method, resourceUri);
    }

    public Response callDontSaveResponse(final Method method, String resourceUri) {
        return callInternal(method, resourceUri);
    }

    private Response callInternal(final Method method, String resourceUri) {

        resourceUri = replaceVariables(resourceUri, this.variables);

        Request request = new Request(method, resourceUri);
        request.setRootRef(new Reference(""));
        if (Method.PUT.equals(method) || Method.POST.equals(method)) {
            // if the request entity is empty or null, Resouce.handlePut() doesn't get thru the last
            // CommandResource at all
            request.setEntity("empty payload", MediaType.TEXT_PLAIN);
        }
        Response response = app.handle(request);
        return response;
    }

    public void setVariable(String name, String value) {
        this.variables.put(name, value);
    }

    public String getVariable(String name) {
        return getVariable(name, this.variables);
    }

    static public String getVariable(String varName, Map<String, String> variables) {
        String varValue = variables.get(varName);
        Preconditions.checkState(varValue != null, "Variable " + varName + " does not exist");
        return varValue;
    }

    public String replaceVariables(final String text) {
        return replaceVariables(text, this.variables);
    }

    static String replaceVariables(final String text, Map<String, String> variables) {
        String resource = text;
        int varIndex = -1;
        while ((varIndex = resource.indexOf("{@")) > -1) {
            for (int i = varIndex + 1; i < resource.length(); i++) {
                char c = resource.charAt(i);
                if (c == '}') {
                    String varName = resource.substring(varIndex + 1, i);
                    String varValue = getVariable(varName, variables);
                    String tmp = resource.replace("{" + varName + "}", varValue);
                    resource = tmp;
                    break;
                }
            }
        }
        return resource;
    }

    public Response getLastResponse() {
        Preconditions.checkState(lastResponse != null);
        return lastResponse;
    }

    public String getLastResponseText() {
        String xml;
        try {
            xml = getLastResponse().getEntity().getText();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return xml;
    }

    public String getLastResponseContentType() {
        final String xml = getLastResponse().getEntity().getMediaType().getName();
        return xml;
    }

    public Document getLastResponseAsDom() {
        try {
            return getLastResponse().getEntityAsDom().getDocument();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

}
