// automatically generated by the FlatBuffers compiler, do not modify

package org.locationtech.geogig.flatbuffers.generated.v1.values;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;

@SuppressWarnings("unused")
public final class STRING extends Table {
  public static STRING getRootAsSTRING(ByteBuffer _bb) { return getRootAsSTRING(_bb, new STRING()); }
  public static STRING getRootAsSTRING(ByteBuffer _bb, STRING obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public STRING __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public String value() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer valueAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }
  public ByteBuffer valueInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 4, 1); }

  public static int createSTRING(FlatBufferBuilder builder,
      int valueOffset) {
    builder.startObject(1);
    STRING.addValue(builder, valueOffset);
    return STRING.endSTRING(builder);
  }

  public static void startSTRING(FlatBufferBuilder builder) { builder.startObject(1); }
  public static void addValue(FlatBufferBuilder builder, int valueOffset) { builder.addOffset(0, valueOffset, 0); }
  public static int endSTRING(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

