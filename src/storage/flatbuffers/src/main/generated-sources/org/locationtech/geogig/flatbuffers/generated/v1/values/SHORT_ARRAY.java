// automatically generated by the FlatBuffers compiler, do not modify

package org.locationtech.geogig.flatbuffers.generated.v1.values;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;

@SuppressWarnings("unused")
public final class SHORT_ARRAY extends Table {
  public static SHORT_ARRAY getRootAsSHORT_ARRAY(ByteBuffer _bb) { return getRootAsSHORT_ARRAY(_bb, new SHORT_ARRAY()); }
  public static SHORT_ARRAY getRootAsSHORT_ARRAY(ByteBuffer _bb, SHORT_ARRAY obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public SHORT_ARRAY __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public short value(int j) { int o = __offset(4); return o != 0 ? bb.getShort(__vector(o) + j * 2) : 0; }
  public int valueLength() { int o = __offset(4); return o != 0 ? __vector_len(o) : 0; }
  public ByteBuffer valueAsByteBuffer() { return __vector_as_bytebuffer(4, 2); }
  public ByteBuffer valueInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 4, 2); }

  public static int createSHORT_ARRAY(FlatBufferBuilder builder,
      int valueOffset) {
    builder.startObject(1);
    SHORT_ARRAY.addValue(builder, valueOffset);
    return SHORT_ARRAY.endSHORT_ARRAY(builder);
  }

  public static void startSHORT_ARRAY(FlatBufferBuilder builder) { builder.startObject(1); }
  public static void addValue(FlatBufferBuilder builder, int valueOffset) { builder.addOffset(0, valueOffset, 0); }
  public static int createValueVector(FlatBufferBuilder builder, short[] data) { builder.startVector(2, data.length, 2); for (int i = data.length - 1; i >= 0; i--) builder.addShort(data[i]); return builder.endVector(); }
  public static void startValueVector(FlatBufferBuilder builder, int numElems) { builder.startVector(2, numElems, 2); }
  public static int endSHORT_ARRAY(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

