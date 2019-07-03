/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.hooks;

import org.locationtech.geogig.di.Decorator;
import org.locationtech.geogig.repository.Command;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

/**
 * An interceptor for the call() method in GeoGig operations that allow hooks
 * 
 */
public class CommandHooksDecorator implements Decorator {

    @SuppressWarnings("unchecked")
    public @Override boolean canDecorate(Object instance) {
        boolean canDecorate = instance instanceof Command;
        if (canDecorate) {
            canDecorate &= instance.getClass().isAnnotationPresent(Hookable.class);
            if (!canDecorate) {
                Class<? extends AbstractGeoGigOp<?>> cmdClass;
                cmdClass = (Class<? extends AbstractGeoGigOp<?>>) instance.getClass();
                canDecorate = CommandHookChain.hasClasspathHooks(cmdClass);
            }
        }
        return canDecorate;
    }

    public @Override <I> I decorate(I subject) {
        AbstractGeoGigOp<?> op = (AbstractGeoGigOp<?>) subject;
        CommandHookChain callChain = CommandHookChain.builder().command(op).build();
        if (!callChain.isEmpty()) {
            op.addListener(new HooksListener(callChain));
        }
        return subject;
    }

    private static class HooksListener implements Command.CommandListener {

        private CommandHookChain callChain;

        public HooksListener(CommandHookChain callChain) {
            this.callChain = callChain;
        }

        public @Override void preCall(Command<?> command) {
            callChain.runPreHooks();
        }

        public @Override void postCall(Command<?> command, Object result,
                RuntimeException exception) {
            callChain.runPostHooks(result, exception);
        }

    }
}
