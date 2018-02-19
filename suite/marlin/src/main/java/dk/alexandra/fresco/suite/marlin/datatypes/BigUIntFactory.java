package dk.alexandra.fresco.suite.marlin.datatypes;

import dk.alexandra.fresco.framework.network.serializers.ByteSerializer;
import java.math.BigInteger;

public interface BigUIntFactory<T extends BigUInt<T>> {

  /**
   * Creates new {@link T} from a long.
   */
  T createFromLong(long value);

  /**
   * Creates new {@link T} from a raw array of bytes.
   */
  T createFromBytes(byte[] bytes);

  T createFromLow(T value);

  /**
   * Creates new {@link T} from a {@link BigInteger}.
   */
  default T createFromBigInteger(BigInteger value) {
    return (value == null) ? null : createFromBytes(value.toByteArray());
  }

  /**
   * Creates random {@link T}.
   */
  T createRandom();

  /**
   * Creates element whose value is zero.
   */
  default T zero() {
    return createFromBytes(new byte[getOperationalBitLength() / Byte.SIZE]);
  }

  /**
   * Creates serializer for {@link T} instances.
   */
  ByteSerializer<T> createSerializer();

  int getOperationalBitLength();

  int getEffectiveBitLength();

}
