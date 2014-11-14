/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.datastream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.CountingOutputStream;
import com.ning.compress.lzf.LZFOutputStream;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.CoordinateSequenceFilter;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.io.OutStream;
import com.vividsolutions.jts.io.OutputStreamOutStream;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;
import com.vividsolutions.jts.io.WKTReader;

public class GeometrySerializerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private GeometrySerializer serializer;

    ByteArrayDataOutput out;

    @Before
    public void before() {
        out = ByteStreams.newDataOutput();
        serializer = GeometrySerializer.defaultInstance();
    }

    private Geometry geom(String wkt) {
        try {
            return new WKTReader().read(wkt);
        } catch (ParseException e) {
            throw Throwables.propagate(e);
        }
    }

    private ByteArrayDataInput getInput() {
        return ByteStreams.newDataInput(out.toByteArray());
    }

    @Test
    public void testNull() throws IOException {
        thrown.expect(NullPointerException.class);
        thrown.expectMessage("null geometry");
        serializer.write(null, out);
    }

    @Test
    public void testEmptyGeometry() throws IOException {
        testEmptyGeometry("POINT EMPTY");
        testEmptyGeometry("LINESTRING EMPTY");
        testEmptyGeometry("POLYGON EMPTY");
        testEmptyGeometry("MULTIPOINT EMPTY");
        testEmptyGeometry("MULTILINESTRING EMPTY");
        testEmptyGeometry("MULTIPOLYGON EMPTY");
        testEmptyGeometry("GEOMETRYCOLLECTION EMPTY");
    }

    private void testEmptyGeometry(String wkt) throws IOException {
        out = ByteStreams.newDataOutput();
        Geometry original = geom(wkt);
        serializer.write(original, out);
        Geometry read = serializer.read(getInput());
        assertNotNull(read);
        assertEquals(original.getGeometryType(), read.getGeometryType());
        assertSame(serializer.getFactory(), read.getFactory());
        assertTrue(read.isEmpty());
    }

    @Test
    public void testPoint() throws IOException {
        testGeom("POINT (1.0 1.1)");
        testGeom("POINT (1000 -1000)");
        testGeom("POINT (-32.34546 -17.45652546)");
        testGeom("POINT (1000.00000000000009 -1000.00000000000009)");
        testGeom("POINT (1000000.00000000009 -1000000.00000000009)");
        testGeom("POINT (10000000.0000000009 -10000000.0000000009)");
        testGeom("POINT (100000000.000000009 -100000000.000000009)");
        testGeom("POINT (1000000000.00000009 -1000000000.00000009)");

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("out of long range");
        testGeom("POINT (1e17 -1)");
    }

    @Test
    public void testMultiPoint() throws IOException {
        testGeom("MULTIPOINT (1.0 1.1, 2.0 2.1, 1000 2000, 50000000 25000000)");
    }

    @Test
    public void testLineString() throws IOException {
        testGeom("LINESTRING (1.0 1.1, 2.0 2.1, 1000 2000, 50000000 -25000000)");
    }

    @Test
    public void testMultiLineString() throws IOException {
        testGeom("MULTILINESTRING ((1.0 1.1, 2.0 2.1), (1000 2000, 50000000 -25000000))");
    }

    @Test
    public void testPolygon() throws IOException {
        testGeom("POLYGON ((1.0 1.1, 2.0 2.1, 3.0 3.1, 1.0 1.1))");
        testGeom("POLYGON ((-10 -10, -10 10, 10 10, 10 -10, -10 -10),(-1 -1, -1 1, 1 1, 1 -1, -1 -1))");
        testGeom("POLYGON ((-10 -10, -10 10, 10 10, 10 -10, -10 -10),(-1 -1, -1 1, 1 1, 1 -1, -1 -1),(3 3, 3 4, 4 4, 4 4, 3 3))");
    }

    @Test
    public void testBigPolygon() throws IOException {
        testGeom(BIG_UTM_POLYGON);
    }

    @Test
    public void testMultiPolygon() throws IOException {
        testGeom("MULTIPOLYGON (((1.0 1.1, 2.0 2.1, 3.0 3.1, 1.0 1.1)))");

        testGeom("MULTIPOLYGON (((1.0 1.1, 2.0 2.1, 3.0 3.1, 1.0 1.1)),"//
                + "((1.0 1.1, 2.0 2.1, 3.0 3.1, 1.0 1.1)),"//
                + "((-10 -10, -10 10, 10 10, 10 -10, -10 -10),(-1 -1, -1 1, 1 1, 1 -1, -1 -1)),"//
                + "((-10 -10, -10 10, 10 10, 10 -10, -10 -10),(-1 -1, -1 1, 1 1, 1 -1, -1 -1),(3 3, 3 4, 4 4, 4 4, 3 3)))");
    }

    @Test
    public void testGeometryCollection() throws IOException {
        testGeom("GEOMETRYCOLLECTION( POINT (1.0 1.1) )");

        testGeom("GEOMETRYCOLLECTION(POINT (1.0 1.1)),"//
                + " MULTIPOLYGON (((1.0 1.1, 2.0 2.1, 3.0 3.1, 1.0 1.1)),"//
                + "  ((1.0 1.1, 2.0 2.1, 3.0 3.1, 1.0 1.1)),"//
                + "  ((-10 -10, -10 10, 10 10, 10 -10, -10 -10),(-1 -1, -1 1, 1 1, 1 -1, -1 -1)),"//
                + "  ((-10 -10, -10 10, 10 10, 10 -10, -10 -10),(-1 -1, -1 1, 1 1, 1 -1, -1 -1),(3 3, 3 4, 4 4, 4 4, 3 3))),"
                + " MULTILINESTRING ((1.0 1.1, 2.0 2.1), (1000 2000, 50000000 -25000000)))"//
        );
    }

    private void testGeom(String wkt) throws IOException {
        testGeom(wkt, 0);
        testGeom(wkt, 1);
        testGeom(wkt, 2);
        testGeom(wkt, 3);
        testGeom(wkt, 4);
        testGeom(wkt, 5);
        testGeom(wkt, 6);
        testGeom(wkt, 7);
        testGeom(wkt, 8);
        testGeom(wkt, 9);
    }

    private void testGeom(final String wkt, final int numDecimals) throws IOException {

        final GeometrySerializer serializer = GeometrySerializer.withDecimalPlaces(numDecimals);
        out = ByteStreams.newDataOutput();
        final Geometry geom = geom(wkt);
        serializer.write(geom, out);
        // byte[] bytes = out.toByteArray();
        // byte[] wkb = new WKBWriter().write(geom);
        // System.err.printf("Size: %,d, wkb: %,d (%,d%%) decimals:%d\t\t%s\n", bytes.length,
        // wkb.length, (bytes.length * 100 / wkb.length), numDecimals, wkt);

        Geometry read = serializer.read(getInput());

        Geometry expected = serializer.getFactory().createGeometry(geom);
        expected.apply(new CoordinateSequenceFilter() {

            @Override
            public boolean isGeometryChanged() {
                return true;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public void filter(CoordinateSequence seq, int i) {
                PrecisionModel precisionModel = serializer.getFactory().getPrecisionModel();
                seq.setOrdinate(i, 0, precisionModel.makePrecise(seq.getOrdinate(i, 0)));
                seq.setOrdinate(i, 1, precisionModel.makePrecise(seq.getOrdinate(i, 1)));
            }
        });

        assertEquals(expected, read);
    }

    @Test
    public void compareBareEncodingPerformanceWithWKB() throws IOException {
        System.err.println("Testing bare geometry encoding performance (no actual output)");
        testEncoderPerf(NULL_OUTPUT_PROVIDER);
    }

    @Test
    public void compareToByteArrayEncodingPerformanceWithWKB() throws IOException {
        System.err.println("Testing geometry encoding performance to ByteArrayOutputStream");
        testEncoderPerf(SAMEBYTEARRAY_OUTPUT_PROVIDER);
    }

    private void testEncoderPerf(final Supplier<OutputStream> outSupplier) throws IOException {
        final int numRuns = 10;
        final Geometry geometry = geom(BIG_UTM_POLYGON);

        testEncodePerf(numRuns, geometry, 0, outSupplier);
        testEncodePerf(numRuns, geometry, 1, outSupplier);
        testEncodePerf(numRuns, geometry, 2, outSupplier);
        testEncodePerf(numRuns, geometry, 3, outSupplier);
        testEncodePerf(numRuns, geometry, 4, outSupplier);
        testEncodePerf(numRuns, geometry, 5, outSupplier);
        testEncodePerf(numRuns, geometry, 6, outSupplier);
        testEncodePerf(numRuns, geometry, 7, outSupplier);
        testEncodePerf(numRuns, geometry, 8, outSupplier);
        testEncodePerf(numRuns, geometry, 9, outSupplier);

        AtomicLong sizeTarget = new AtomicLong();
        AtomicLong compressedSizeTarget = new AtomicLong();
        // test WKB.
        // warm up...
        encodeWKB(geometry, numRuns, outSupplier, sizeTarget, compressedSizeTarget);
        Stopwatch sw = encodeWKB(geometry, numRuns, outSupplier, sizeTarget, compressedSizeTarget);
        System.err.printf("WKB encode geom %,d times in %s, (avg: %fms) (size: %,d, lzw: %,d)\n",
                numRuns, sw, ((double) sw.elapsed(TimeUnit.MILLISECONDS) / numRuns),
                sizeTarget.get(), compressedSizeTarget.get());
    }

    @Test
    public void compareDecodingPerformanceWithWKB() throws Exception {
        final int numRuns = 10;
        final Geometry geometry = geom(BIG_UTM_POLYGON);

        testDecodePerf(numRuns, geometry, 0);
        testDecodePerf(numRuns, geometry, 1);
        testDecodePerf(numRuns, geometry, 2);
        testDecodePerf(numRuns, geometry, 3);
        testDecodePerf(numRuns, geometry, 4);
        testDecodePerf(numRuns, geometry, 5);
        testDecodePerf(numRuns, geometry, 6);
        testDecodePerf(numRuns, geometry, 7);
        testDecodePerf(numRuns, geometry, 8);
        testDecodePerf(numRuns, geometry, 9);

        // test WKB.
        // warm up...
        decodeWKB(geometry, numRuns);
        Stopwatch sw = decodeWKB(geometry, numRuns);
        System.err.printf("Decoded WKB %,d coords geom %,d times in %s, (avg: %fms)\n",
                geometry.getNumPoints(), numRuns, sw,
                ((double) sw.elapsed(TimeUnit.MILLISECONDS) / numRuns));
    }

    private void testEncodePerf(final int numRuns, final Geometry geometry, final int numDecimals,
            final Supplier<OutputStream> outSupplier) throws IOException {
        AtomicLong sizeTarget = new AtomicLong();
        AtomicLong compressedSizeTarget = new AtomicLong();
        // warm up...
        encode(geometry, numDecimals, numRuns, outSupplier, sizeTarget, compressedSizeTarget);
        // now test
        Stopwatch sw = encode(geometry, numDecimals, numRuns, outSupplier, sizeTarget,
                compressedSizeTarget);

        System.err
                .printf("Encoded %,d coords geom %,d times with %d decimals in %s, (avg: %fms) (size: %,d, lzw: %,d)\n",
                        geometry.getNumPoints(), numRuns, numDecimals, sw,
                        ((double) sw.elapsed(TimeUnit.MILLISECONDS) / numRuns), sizeTarget.get(),
                        compressedSizeTarget.get());

    }

    private void testDecodePerf(final int numRuns, final Geometry geometry, final int numDecimals)
            throws IOException {
        // warm up...
        decode(geometry, numDecimals, numRuns);
        // now test
        Stopwatch sw = decode(geometry, numDecimals, numRuns);
        System.err.printf(
                "Decoded %,d coords geom %,d times with %d decimals in %s, (avg: %fms)\n",
                geometry.getNumPoints(), numRuns, numDecimals, sw,
                ((double) sw.elapsed(TimeUnit.MILLISECONDS) / numRuns));

    }

    private Stopwatch encode(final Geometry geom, final int numDecimals, final int numRuns,
            final Supplier<OutputStream> outputSupplier, AtomicLong sizeTarget,
            AtomicLong compressedSizeTarget) throws IOException {

        final GeometrySerializer serializer = GeometrySerializer.withDecimalPlaces(numDecimals);
        OutputStream outStream = outputSupplier.get();
        CountingOutputStream counting = new CountingOutputStream(outStream);
        DataOutput output = new DataOutputStream(counting);

        // warm up output stream
        serializer.write(geom, output);

        Stopwatch sw = Stopwatch.createStarted();
        for (int i = 0; i < numRuns; i++) {
            outStream = outputSupplier.get();
            output = new DataOutputStream(outStream);
            serializer.write(geom, output);
        }
        sw.stop();
        sizeTarget.set(counting.getCount());

        counting = new CountingOutputStream(new ByteArrayOutputStream());
        outStream = new LZFOutputStream(counting);
        output = new DataOutputStream(outStream);
        serializer.write(geom, output);
        outStream.close();
        compressedSizeTarget.set(counting.getCount());
        return sw;
    }

    private Stopwatch decode(final Geometry geom, final int numDecimals, final int numRuns)
            throws IOException {

        final GeometrySerializer serializer = GeometrySerializer.withDecimalPlaces(numDecimals);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayDataOutput output = ByteStreams.newDataOutput(outStream);
        serializer.write(geom, output);
        byte[] bytes = outStream.toByteArray();

        Stopwatch sw = Stopwatch.createStarted();
        for (int i = 0; i < numRuns; i++) {
            DataInput in = ByteStreams.newDataInput(bytes);
            serializer.read(in);
        }
        return sw.stop();
    }

    private Stopwatch encodeWKB(final Geometry geom, final int numRuns,
            final Supplier<OutputStream> outSupp, AtomicLong sizeTarget,
            AtomicLong compressedSizeTarget) throws IOException {

        final int outputDimension = 2;
        final WKBWriter writer = new WKBWriter(outputDimension);
        Stopwatch sw = Stopwatch.createStarted();
        CountingOutputStream counting = null;
        for (int i = 0; i < numRuns; i++) {
            OutputStream out = outSupp.get();
            counting = new CountingOutputStream(out);
            OutStream outStream = new OutputStreamOutStream(counting);
            writer.write(geom, outStream);
        }
        sw.stop();
        sizeTarget.set(counting.getCount());

        OutputStream out = outSupp.get();
        counting = new CountingOutputStream(out);
        LZFOutputStream cout = new LZFOutputStream(counting);
        OutStream outStream = new OutputStreamOutStream(cout);
        writer.write(geom, outStream);
        cout.close();
        compressedSizeTarget.set(counting.getCount());
        return sw;
    }

    private Stopwatch decodeWKB(final Geometry geom, final int numRuns) throws IOException,
            ParseException {

        byte[] bytes;
        {
            final WKBWriter writer = new WKBWriter();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            OutStream outStream = new OutputStreamOutStream(out);
            writer.write(geom, outStream);
            bytes = out.toByteArray();
        }

        WKBReader reader = new WKBReader();

        Stopwatch sw = Stopwatch.createStarted();
        for (int i = 0; i < numRuns; i++) {
            reader.read(bytes);
        }
        return sw.stop();
    }

    // private static final String BIG_UTM_POLYGON =
    // "POLYGON ((251141.82420082277 863947.7208682193, 252637.72420082276 863594.6208582192, 252995.3742008228 863062.9208582193, 253003.3142008228 863062.5608582193, 253003.95420082277 863057.7208582193, 252999.60420082277 863050.7908582193, 252995.61420082278 863046.0908582193, 252997.13420082277 863041.8708482193, 252992.79420082277 863038.6908482193, 252993.3742008228 863036.4608482192, 252991.49420082278 863034.1108482193, 252989.15420082278 863031.5308482193, 252989.5042008228 863029.1808482193, 252990.5642008228 863027.4208482193, 252992.08420082278 863020.9708482192, 252990.91420082276 863013.8008482193, 252990.20420082277 863006.8708482193, 252989.6242008228 862995.2408382193, 252989.38420082277 862990.3708382193, 252988.5642008228 862986.1408382193, 252980.57420082277 862978.2708382193, 252976.34423082278 862970.6308782193, 252967.18423082278 862967.8108782193, 252959.08423082277 862963.6508782193, 252952.4742308228 862957.9608682193, 252943.11423082277 862954.1108682193, 252932.92422082278 862949.9808682193, 252920.62422082276 862942.0908682193, 252910.9042208228 862938.2308682193, 252905.57422082277 862940.8008682192, 252903.37422082276 862948.1408682193, 252903.00422082277 862951.4508682193, 252901.17422082278 862953.1008682193, 252894.38422082277 862949.9808682193, 252888.50421082278 862940.8008682192, 252881.71421082277 862938.4808682193, 252874.00421082278 862934.0808682193, 252867.58421082277 862927.2908682192, 252860.11421082277 862927.8008682192, 252853.13421082278 862930.0008682193, 252847.63420082277 862930.0008682193, 252842.3142008228 862927.6208682193, 252839.3742008228 862923.7608682193, 252839.3742008228 862918.8108582193, 252842.1242008228 862912.7508582192, 252846.89420082278 862910.1808582193, 252849.46420082278 862906.6908582193, 252849.10420082277 862903.0208582192, 252846.71420082278 862900.0908582193, 252843.41420082276 862900.2708582192, 252837.72420082276 862901.5608582193, 252832.58420082278 862902.2908582193, 252829.64420082278 862901.0008582192, 252827.6242008228 862897.7008582193, 252825.97420082276 862894.2108582193, 252823.77420082278 862890.9108582193, 252819.18420082278 862889.9908582193, 252810.92420082277 862891.2808582193, 252804.31423082278 862890.5408582193, 252800.27423082278 862889.2608582192, 252794.95423082277 862889.8108582193, 252789.4442308228 862889.9908582193, 252784.86423082277 862889.0708582193, 252778.06423082278 862884.6708582193, 252772.01423082277 862882.6508582192, 252768.15423082278 862879.1608582193, 252762.83422082278 862876.4108582193, 252755.86422082278 862876.0408582193, 252751.08422082278 862876.9608582193, 252748.33422082278 862881.1808582193, 252747.05422082279 862887.6108582193, 252745.03422082277 862893.1108582193, 252741.91422082277 862894.5808582193, 252737.50422082277 862893.6608582193, 252733.62422082276 862890.5008582192, 252730.89422082278 862887.4208582193, 252726.03422082277 862879.5308582193, 252715.20421082276 862858.7908482193, 252711.35421082278 862852.5908482192, 252705.2942108228 862851.3008482193, 252695.56421082278 862854.6008482192, 252687.4842108228 862858.6408482193, 252680.88420082277 862865.4308482193, 252677.39420082278 862867.6408482193, 252673.90420082278 862866.9008482193, 252669.5042008228 862862.3108482193, 252667.11420082278 862857.7208482192, 252664.91420082276 862855.7108482192, 252660.32420082277 862857.1708482193, 252656.3742008228 862862.5008482193, 252653.80420082278 862867.2708482193, 252651.78420082276 862870.2108482192, 252647.5642008228 862868.9208482193, 252641.69420082276 862865.6208482193, 252639.1242008228 862862.6808482193, 252634.7242308228 862859.5608482193, 252631.96423082278 862857.1708482193, 252630.68423082278 862851.6708482193, 252628.66423082276 862845.9808482192, 252627.1942308228 862843.5908482192, 252621.87423082278 862844.1408482193, 252615.26423082277 862846.5308482193, 252610.67423082277 862849.8308482192, 252605.16423082276 862853.1408482193, 252601.26423082277 862852.9508482192, 252596.72422082277 862852.5908482192, 252591.58422082278 862853.1408482193, 252587.08422082278 862855.7108482192, 252584.5242208228 862859.3808482193, 252578.83422082278 862860.8408482192, 252574.05422082279 862860.2908482193, 252571.30422082279 862856.8108482193, 252570.75422082277 862850.9308482193, 252570.93422082276 862845.9808482192, 252569.83422082278 862841.5708482193, 252565.61422082278 862839.9208482193, 252560.84422082276 862840.1008482192, 252556.62422082276 862844.1408482193, 252554.41421082278 862850.2008482192, 252553.86421082277 862856.0708482193, 252552.03421082278 862861.2108482192, 252546.52421082277 862862.6808482193, 252543.22421082278 862862.1308482193, 252539.18421082277 862856.2608482193, 252535.51421082276 862849.2808482193, 252525.05421082277 862833.8008482193, 252520.09421082278 862832.8808482193, 252513.36421082277 862835.4808482192, 252508.99420082278 862832.8608482193, 252503.73420082277 862830.2308382193, 252504.61420082278 862822.3608382193, 252507.24420082278 862815.3508382193, 252508.11420082278 862809.2308382193, 252503.73420082277 862803.1008382193, 252494.98420082277 862796.1008782192, 252489.73420082277 862789.1008682193, 252481.85420082277 862787.3508682193, 252471.35420082277 862783.8508682193, 252460.60423082276 862782.4708682193, 252460.85423082276 862788.2208682193, 252462.60423082276 862795.2208782192, 252460.85423082276 862803.9808382193, 252455.60423082276 862803.9808382193, 252448.60423082276 862803.9808382193, 252444.53423082278 862806.9608382193, 252440.9442308228 862813.0508382192, 252434.59423082278 862811.8508382193, 252432.05423082277 862807.2808382192, 252430.96423082278 862800.2608782193, 252428.31422082277 862794.3308782192, 252426.43422082276 862787.1508682193, 252423.31422082277 862783.4108682192, 252419.57422082277 862784.1908682192, 252414.2742208228 862789.8008682192, 252410.37422082276 862797.2908782193, 252406.62422082276 862800.8808782193, 252402.72422082277 862801.3508782192, 252397.89422082278 862799.4808782192, 252394.14422082278 862796.5108782193, 252390.55422082279 862797.4508782192, 252388.53422082277 862802.6008782192, 252386.6542208228 862810.5508382192, 252385.72422082277 862821.3108382192, 252382.2942108228 862827.7108382193, 252375.35421082278 862835.6308482193, 252372.85421082278 862839.5308482193, 252373.94421082278 862846.7108482192, 252372.69421082278 862851.8608482193, 252369.7342108228 862853.4208482193, 252365.99421082277 862851.5508482193, 252360.21421082277 862847.9608482192, 252352.26421082276 862837.3508482192, 252350.85421082278 862833.1408482193, 252347.11421082277 862831.1108382193, 252340.0942008228 862832.3608482193, 252331.04420082277 862839.5308482193, 252319.8142008228 862848.5808482192, 252311.5642008228 862862.0008482193, 252300.74420082278 862888.3608582193, 252298.01423082277 862893.4308582193, 252287.87423082278 862893.6308582192, 252277.14423082277 862900.4508582193, 252266.61423082277 862908.2508582192, 252259.84423082278 862910.0508582193, 252255.88422082277 862909.1308582192, 252253.14422082278 862905.4808582193, 252248.57422082277 862900.6008582193, 252229.37422082276 862890.8508582193, 252220.23422082278 862887.3308582193, 252222.72422082277 862819.6708382192, 252232.34422082276 862776.8508682193, 252236.70422082278 862597.7308682193, 252242.79422082278 862594.6308682193, 252253.0242208228 862592.5808682193, 252257.88422082277 862585.9308682192, 252261.20423082277 862575.4408582193, 252265.30423082277 862564.1908582193, 252269.90423082278 862558.0508582193, 252277.06423082278 862552.1708582193, 252286.78423082278 862542.2008582193, 252284.99423082278 862537.5908582193, 252278.85423082276 862535.0408482193, 252271.9442308228 862534.7808482193, 252270.67423082277 862531.9708482192, 252268.8842308228 862527.8808482193, 252270.41423082276 862524.8108482193, 252274.76423082277 862521.2308482192, 252277.32423082276 862518.6708482193, 252278.08423082277 862512.0208482193, 252276.29423082276 862508.4408482193, 252275.01423082277 862504.6008482192, 252268.8842308228 862503.5808482192, 252263.2542308228 862503.0708482193, 252259.16422082277 862499.2308482192, 252260.6942308228 862495.4008482193, 252259.41423082276 862487.5208382193, 252258.13422082277 862479.8508382193, 252258.9042208228 862475.5008382193, 252256.34422082276 862469.8708382193, 252254.04422082278 862468.0808382193, 252252.00422082277 862453.7608682193, 252245.35422082277 862449.4108682192, 252238.95422082278 862449.1608682192, 252233.33422082278 862447.6208682193, 252228.72422082277 862443.0208682193, 252228.2142208228 862437.3908682193, 252233.58422082278 862429.9808682193, 252241.7742208228 862426.1408682193, 252249.18422082276 862420.5108682193, 252249.44422082277 862416.6808682192, 252247.14422082278 862411.3108682192, 252241.51422082278 862405.4808582193, 252237.16422082277 862402.9208582193, 252233.07422082277 862399.5908582193, 252230.7742208228 862394.7408582192, 252223.35422082277 862393.9708582193, 252215.42421082276 862397.8008582193, 252207.75421082278 862394.4808582193, 252200.85421082278 862384.7608582192, 252201.19421082278 862378.0908582193, 252195.3242108228 862369.1008582193, 252188.86421082277 862363.6208482193, 252184.17421082276 862357.5508482193, 252177.52421082277 862352.8608482193, 252164.02420082278 862342.4908482193, 252154.63420082277 862337.8008482193, 252149.74420082278 862332.3208482193, 252146.8142008228 862323.9108482193, 252146.8142008228 862317.4508382193, 252147.0042008228 862309.8208382193, 252146.61420082278 862305.3208382193, 252143.48420082277 862300.2408382193, 252135.26420082277 862296.5208382193, 252130.96420082278 862293.8208782193, 252129.98420082277 862285.6008782192, 252129.79420082277 862272.3008682192, 252127.83423082277 862264.8608682193, 252123.7242308228 862260.3708682193, 252120.79423082276 862258.6108682193, 252119.03423082278 862253.9108682192, 252121.37423082278 862248.0408682192, 252122.16423082276 862242.5708682192, 252120.40423082278 862237.6808582193, 252113.35423082276 862235.1308582192, 252103.96423082278 862234.7408582192, 252097.2242308228 862232.5108582192, 252097.2242308228 862228.2108582193, 252098.39423082277 862221.9508582193, 252098.98423082277 862214.5108582192, 252100.15423082278 862208.0608582193, 252099.95423082277 862202.9708582193, 252096.82423082276 862197.3008482193, 252092.52423082278 862193.7808482193, 252085.09422082276 862190.4508482192, 252074.72422082277 862190.2608482193, 252064.1542208228 862188.8908482193, 252057.70422082278 862183.2108482192, 252054.76422082278 862177.9308482193, 252053.59422082276 862160.7208482192, 252054.18422082276 862153.2808382192, 252055.55422082279 862148.0408382192, 252061.81422082277 862142.7608382193, 252068.26422082278 862140.8008382192, 252077.85422082277 862138.4608382193, 252083.33422082278 862134.1508382193, 252090.37423082278 862126.7208382193, 252090.37423082278 862120.0708782193, 252087.83422082278 862113.4208682192, 252082.35422082277 862107.1608682192, 252077.85422082277 862098.9808682193, 252074.86422082278 862090.5408682192, 252070.81422082277 862080.2008682193, 252065.33422082278 862060.4408582193, 252061.81422082277 862052.4208582193, 252059.07422082277 862046.3608582193, 252053.20422082278 862042.6408582192, 252045.5742108228 862042.6408582192, 252034.81421082278 862043.6208582192, 252025.42421082276 862048.7008582193, 252016.03421082278 862051.6408582192, 252009.18421082277 862051.0508582193, 252004.9842108228 862046.9808582193, 252002.83420082278 862030.9408582193, 252002.04420082277 862023.5108482193, 251997.94420082276 862009.7608482193, 251988.16420082276 861983.9308382192, 251982.8742008228 861973.7608382193, 251976.8142008228 861968.2808382192, 251969.01420082277 861963.8908382193, 251961.0042008228 861963.0908382193, 251950.59423082278 861964.6908382192, 251938.05423082277 861971.1008382193, 251924.4442308228 861974.5708382193, 251919.10422082277 861974.0308382192, 252024.2942108228 861831.2608482193, 251983.11420082278 861655.1708482193, 252704.25421082278 861419.5508682192, 252716.39421082276 861387.3108582193, 252709.69421082278 861377.6008582193, 252724.24421082277 861370.3208582193, 252754.57422082277 861295.1108382193, 252758.2142208228 861177.4508482192, 253075.2142208228 861042.4408582193, 253064.78422082277 860949.2708382193, 252973.07423082276 860963.4408382192, 252964.49423082278 860789.9808382193, 252506.24420082278 860770.5508382192, 252509.65420082278 860688.6508582192, 252426.9042208228 860657.0808482192, 252354.74421082277 861088.2808682193, 252270.85423082276 861113.5708382193, 252241.95422082278 861242.3508682193, 252169.23420082277 861230.7008682193, 252141.57420082277 861326.8908482193, 252214.56421082278 861381.2108582193, 252147.8442008228 861541.6108582193, 252037.86421082277 861480.1008482192, 251953.46423082278 861656.9208482193, 251742.38422082277 861560.9808582193, 251815.23420082277 861390.6608582193, 251739.91422082277 861379.1308582192, 251643.3742008228 861187.8908582192, 251482.08420082278 861191.0008582192, 251418.2242308228 861272.5308782193, 251449.87423082278 861331.0408482193, 251415.34423082278 861440.3908782193, 251274.7242308228 861402.3408682193, 251269.6942308228 861508.1308482193, 251385.57422082277 861537.9708582193, 251298.16420082276 861676.9208482193, 251288.3142008228 861705.2108582193, 251161.33421082277 861670.9708482192, 251143.8742008228 861716.1208582192, 251325.9842108228 861760.0508682192, 251307.90420082278 861771.5308682193, 251157.7342108228 861815.8908482193, 251074.7542308228 861894.7708582192, 250993.71421082277 861940.4308682192, 250995.06421082278 861911.4208682192, 250985.17420082277 861911.4208682192, 250988.06421082278 861984.8208482193, 251092.41423082276 861981.2108382193, 251091.42423082277 862076.9208682192, 251112.82420082277 862076.5108682193, 251094.70423082277 862426.1008682193, 251114.74420082278 862440.0408682192, 251143.70420082277 862376.1408582192, 251136.98420082277 862239.7208582193, 251145.6242008228 862198.4108582193, 251135.15420082278 861989.4708482192, 251115.52420082278 861970.4008382193, 251120.01420082277 861954.1408782193, 251176.65421082277 861973.2008382193, 251197.95421082276 861975.7608382193, 251201.62422082276 861926.6708682192, 251192.40421082277 861908.8208682192, 251202.24422082276 861892.5208582192, 251217.93422082276 861904.5208682193, 251224.70422082278 861897.1308582192, 251201.4642208228 861856.8608482193, 251202.85422082277 861848.8408482192, 251229.62422082276 861894.9808582193, 251221.93422082276 861909.7508682193, 251208.39422082278 861927.5908682193, 251204.69422082277 861976.5708382193, 251240.0242208228 862021.4308482193, 251217.0242208228 862186.3008482193, 251193.47421082278 862201.4408582193, 251191.7942108228 862223.8808582192, 251240.58422082278 862249.3908682193, 251205.81422082277 862354.2608482193, 251200.76422082278 862435.2908682192, 251219.83422082278 862509.8808482193, 251214.78422082277 862543.6408582192, 251238.33422082278 862666.9008482193, 251289.93420082278 862685.9708482192, 251283.20420082277 862698.8708482193, 251226.00422082277 862678.6808482193, 251200.76422082278 862738.6808582193, 251178.89421082276 862821.1208382193, 251128.42420082277 862871.8708482193, 251031.9642208228 862866.4308482193, 251097.57423082276 862672.1208482193, 251061.68422082276 862604.2608682193, 251092.51423082277 862462.0008782193, 251067.87422082276 862456.8608782192, 251023.53421082278 862674.6008482192, 251079.30423082277 862686.0808482192, 251045.98422082278 862780.1308682193, 250986.14420082278 862879.5008582192, 251160.94421082278 862946.9608682193, 251210.29422082278 863165.0008382193, 251146.61420082278 863181.3308482192, 251155.90421082277 863239.0208582192, 251182.81421082278 863246.8708582192, 251243.9442308228 863385.8408582193, 251272.82423082276 863591.6508582192, 251270.02423082278 863699.3208482193, 251181.60421082278 863898.4408582193, 251141.82420082277 863947.7208682193))";

    private static final String BIG_UTM_POLYGON;
    static {
        try (InputStream in = new GZIPInputStream(
                GeometrySerializerTest.class.getResourceAsStream("polyC.wkt.gz"))) {
            BIG_UTM_POLYGON = CharStreams.toString(new InputStreamReader(in));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Provides a "null object" output stream. Used to test bare encoding performance
     */
    private static final Supplier<OutputStream> NULL_OUTPUT_PROVIDER = new Supplier<OutputStream>() {

        private final OutputStream NULLSTREAM = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                // do nothing
            }
        };

        @Override
        public OutputStream get() {
            return NULLSTREAM;
        }
    };

    /**
     * Repeatedly provides the same ByteArrayOutputStresm, resetted before returning
     */
    private static final Supplier<OutputStream> SAMEBYTEARRAY_OUTPUT_PROVIDER = new Supplier<OutputStream>() {

        private final ByteArrayOutputStream STREAM = new ByteArrayOutputStream();

        @Override
        public OutputStream get() {
            STREAM.reset();
            return STREAM;
        }
    };
}
