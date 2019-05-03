/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.ValueArray;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * A binary representation of the values of a Feature.
 * 
 */
class RevFeatureImpl extends AbstractRevObject implements RevFeature {

    private final Object[] values;

    /**
     * Constructs a new {@code RevFeature} with the provided {@link ObjectId} and set of values
     * <p>
     * Takes ownership of the array, which is given by the builder, no need to copy
     * 
     * @param id the {@link ObjectId} to use for this feature
     * @param values a list of values, {@code null} members allowed
     */
    RevFeatureImpl(ObjectId id, Object[] values) {
        super(id);
        this.values = values;
    }

    public @Override List<Optional<Object>> getValues() {
        final int size = size();
        List<Optional<Object>> retvalues = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            retvalues.add(Optional.ofNullable(ValueArray.safeCopy(values[i])));
        }
        return retvalues;
    }

    public @Override int size() {
        return values.length;
    }

    public @Override Optional<Object> get(final int index) {
        return Optional.ofNullable(ValueArray.safeCopy(values[index]));
    }

    public @Override Optional<Geometry> get(int index, GeometryFactory gf) {
        Geometry g = (Geometry) values[index];
        Geometry g2 = null;
        if (g != null) {
            g2 = gf.createGeometry(g);
        }
        return Optional.ofNullable(g2);
    }

    public @Override void forEach(final Consumer<Object> consumer) {
        for (Object v : values) {
            consumer.accept(ValueArray.safeCopy(v));
        }
    }

    public @Override TYPE getType() {
        return TYPE.FEATURE;
    }

    public @Override String toString() {
        return RevObjects.toString(this);
    }

}
