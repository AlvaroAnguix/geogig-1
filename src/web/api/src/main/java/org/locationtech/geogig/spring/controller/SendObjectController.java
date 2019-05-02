/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 * 
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.locationtech.geogig.rest.repository.RepositoryProvider.BASE_REPOSITORY_ROUTE;
import static org.locationtech.geogig.rest.repository.RepositoryProvider.GEOGIG_ROUTE_PREFIX;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.OPTIONS;
import static org.springframework.web.bind.annotation.RequestMethod.PATCH;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;
import static org.springframework.web.bind.annotation.RequestMethod.TRACE;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.SendObject;
import org.locationtech.geogig.spring.service.LegacySendObjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.Sets;

/**
 *
 */
@RestController
@RequestMapping(path = GEOGIG_ROUTE_PREFIX + "/" + BASE_REPOSITORY_ROUTE
        + "/{repoName}/repo/sendobject")
public class SendObjectController extends AbstractRepositoryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendObjectController.class);

    @Autowired
    private LegacySendObjectService legacySendObjectService;

    @RequestMapping(method = {GET, PUT, DELETE, PATCH, TRACE, OPTIONS})
    public void catchAll() {
        // if we hit this controller, it's a 405
        supportedMethods(Sets.newHashSet(POST.toString()));
    }

    @PostMapping()
    public void postSendObject(@PathVariable(name = "repoName") String repoName,
            InputStream requestInput, HttpServletRequest request, HttpServletResponse response,
            @RequestHeader(name = "Content-encoding", required = false) String contentEncoding) {
        // get the provider
        Optional<RepositoryProvider> optional = getRepoProvider(request);
        if (optional.isPresent()) {
            final RepositoryProvider provider = optional.get();
            // ensure the repo exists and is opened
            if (!isOpenRepository(provider, repoName, response)) {
                // done
                return;
            }
            // get the FilteredChanges from the service
            // if the content-encoding indicates the InputStream is compressed, we need to wrap
            // the stream.
            try {
                SendObject sendObject = legacySendObjectService.sendObject(provider, repoName,
                        "gzip".equals(contentEncoding) ? new GZIPInputStream(requestInput):
                                requestInput);
                // encode to Stream
                encodeToStream(sendObject, request, response);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        } else {
            throw NO_PROVIDER;
        }
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
