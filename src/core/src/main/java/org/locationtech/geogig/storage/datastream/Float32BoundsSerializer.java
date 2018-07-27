/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import static org.locationtech.geogig.storage.datastream.Varint.readSignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeSignedVarInt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.locationtech.jts.geom.Envelope;

/**
 * Utility class to serialize FLOAT32-gridded Envelops. NOTE: the envelope MUST be represented by
 * float32 numbers (i.e. e.getMinX() can be exactly represented by a float32)
 *
 * Will throw an runtime exception if you try to serialize a "bad" envelope!
 *
 * cf Float32Bounds
 */
class Float32BoundsSerializer {

    public static Envelope deserialize(int[] serializedForm) {
        float xmin = Float.intBitsToFloat(serializedForm[0]);
        float xmax = Float.intBitsToFloat(serializedForm[0] + serializedForm[1]);
        float ymin = Float.intBitsToFloat(serializedForm[2]);
        float ymax = Float.intBitsToFloat(serializedForm[2] + serializedForm[3]);
        boolean isNull = xmin > xmax;

        if (isNull)
            return new Envelope();
        return new Envelope(xmin, xmax, ymin, ymax);
    }

    public static Envelope deserialize(DataInput in) throws IOException {
        int i0 = readSignedVarInt(in);
        int i1 = readSignedVarInt(in);
        int i2 = readSignedVarInt(in);
        int i3 = readSignedVarInt(in);
        float xmin = Float.intBitsToFloat(i0);
        float xmax = Float.intBitsToFloat(i0 + i1);
        float ymin = Float.intBitsToFloat(i2);
        float ymax = Float.intBitsToFloat(i2 + i3);
        boolean isNull = xmin > xmax;

        if (isNull)
            return new Envelope();
        return new Envelope(xmin, xmax, ymin, ymax);
    }

    /**
     * we assume this is properly aligned on float32 bounds serialized form is 4 ints - representing
     * the bounding box
     * <p>
     * int[0] - direct representation of xmin (Float.floatToRawIntBits) int[1] - offset (in raw int)
     * between xmin and xmax int[2] - direct representation of ymin (Float.floatToRawIntBits)
     * int[3]- offset (in raw int) between ymin and ymax
     * <p>
     * we use the offset so that varint representation can more effectively compress
     *
     * @param e
     * @return
     */
    public static int[] serialize(Envelope e) {
        int[] result = new int[4];

        if (e.isNull()) {
            // xmin,ymin=Float.MIN_VALUE xmax,ymax=0
            // xmin > xmax --> definition of Null
            // these values chosen to so varint is very small
            result[0] = 1;// Float.MIN_VALUE = 1.4E-45
            result[1] = -1;
            result[2] = 1;
            result[3] = -1;
            return result;
        }

        float xmin = (float) e.getMinX();
        float ymin = (float) e.getMinY();
        float xmax = (float) e.getMaxX();
        float ymax = (float) e.getMaxY();

        if ((xmin > e.getMinX()) || (ymin > e.getMinY()) || (xmax < e.getMaxX())
                || (ymax < e.getMaxY()))
            throw new RuntimeException("attempted to serialize a non-float32 compatible envelope");

        result[0] = Float.floatToRawIntBits(xmin);
        result[1] = Float.floatToRawIntBits(xmax) - Float.floatToRawIntBits(xmin);
        result[2] = Float.floatToRawIntBits(ymin);
        result[3] = Float.floatToRawIntBits(ymax) - Float.floatToRawIntBits(ymin);
        return result;
    }

    public static void serialize(Envelope e, DataOutput data) throws IOException {
        int result0, result1, result2, result3;
        if (e.isNull()) {
            // xmin,ymin=Float.MIN_VALUE xmax,ymax=0
            // xmin > xmax --> definition of Null
            // these values chosen to so varint is very small
            result0 = 1;// Float.MIN_VALUE = 1.4E-45
            result1 = -1;
            result2 = 1;
            result3 = -1;
        } else {

            float xmin = (float) e.getMinX();
            float ymin = (float) e.getMinY();
            float xmax = (float) e.getMaxX();
            float ymax = (float) e.getMaxY();

            if ((xmin > e.getMinX()) || (ymin > e.getMinY()) || (xmax < e.getMaxX())
                    || (ymax < e.getMaxY()))
                throw new RuntimeException(
                        "attempted to serialize a non-float32 compatible envelope");

            result0 = Float.floatToRawIntBits(xmin);
            result1 = Float.floatToRawIntBits(xmax) - Float.floatToRawIntBits(xmin);
            result2 = Float.floatToRawIntBits(ymin);
            result3 = Float.floatToRawIntBits(ymax) - Float.floatToRawIntBits(ymin);
        }
        byte[] buff = new byte[5];
        writeSignedVarInt(buff, result0, data);
        writeSignedVarInt(buff, result1, data);
        writeSignedVarInt(buff, result2, data);
        writeSignedVarInt(buff, result3, data);
    }
}
