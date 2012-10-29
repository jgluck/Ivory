package ivory.core.data.dictionary;

import it.unimi.dsi.bits.AbstractBitVector;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.TransformationStrategy;

import java.io.Serializable;
import java.nio.charset.CharacterCodingException;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableUtils;

// Copied from dsiutils-2.0.9-sources/src/it/unimi/dsi/bits/TransformationStrategies.java
public class DictionaryTransformationStrategy implements TransformationStrategy<CharSequence> {
  private static final long serialVersionUID = 1L;
  /** Whether we should guarantee prefix-freeness by adding 0 to the end of each string. */
  private final boolean prefixFree;

  /**
   * Creates an ISO transformation strategy. The strategy will map a string to the lowest eight bits
   * of its natural UTF16 bit sequence.
   * 
   * @param prefixFree if true, the resulting set of binary words will be made prefix free by adding
   */
  public DictionaryTransformationStrategy(boolean prefixFree) {
    this.prefixFree = prefixFree;
  }

  private static class ISOCharSequenceBitVector extends AbstractBitVector implements Serializable {
    private static final long serialVersionUID = 1L;
    private transient CharSequence s;
    private transient long length;
    private transient long actualEnd;

    public ISOCharSequenceBitVector(final CharSequence s, final boolean prefixFree) {
      this.s = s;
      actualEnd = s.length() * (long) Byte.SIZE;
      length = actualEnd + (prefixFree ? Byte.SIZE : 0);
    }

    public boolean getBoolean(long index) {
      if (index > length)
        throw new IndexOutOfBoundsException();
      if (index >= actualEnd)
        return false;
      final int byteIndex = (int) (index / Byte.SIZE);
      return (s.charAt(byteIndex) & 0x80 >>> index % Byte.SIZE) != 0;
    }

    public long getLong(final long from, final long to) {
      // System.err.println ( from + "->" + to );
      final int startBit = (int) (from % Long.SIZE);
      if (startBit == 0 && to % Byte.SIZE == 0) {
        if (from == to)
          return 0;
        long l;
        int pos = (int) (from / Byte.SIZE);
        if (to == from + Long.SIZE)
          l = (to > actualEnd ? 0 : (s.charAt(pos + 7) & 0xFFL)) << 56
              | (s.charAt(pos + 6) & 0xFFL) << 48 | (s.charAt(pos + 5) & 0xFFL) << 40
              | (s.charAt(pos + 4) & 0xFFL) << 32 | (s.charAt(pos + 3) & 0xFFL) << 24
              | (s.charAt(pos + 2) & 0xFF) << 16 | (s.charAt(pos + 1) & 0xFF) << 8
              | (s.charAt(pos) & 0xFF);
        else {
          l = 0;
          final int residual = (int) (Math.min(actualEnd, to) - from);
          for (int i = residual / Byte.SIZE; i-- != 0;)
            l |= (s.charAt(pos + i) & 0xFFL) << i * Byte.SIZE;
        }

        l = (l & 0x5555555555555555L) << 1 | (l >>> 1) & 0x5555555555555555L;
        l = (l & 0x3333333333333333L) << 2 | (l >>> 2) & 0x3333333333333333L;
        return (l & 0x0f0f0f0f0f0f0f0fL) << 4 | (l >>> 4) & 0x0f0f0f0f0f0f0f0fL;
      }

      final long l = Long.SIZE - (to - from);
      final long startPos = from - startBit;
      if (l == Long.SIZE)
        return 0;

      if (startBit <= l)
        return getLong(startPos, Math.min(length, startPos + Long.SIZE)) << l - startBit >>> l;
      return getLong(startPos, startPos + Long.SIZE) >>> startBit
          | getLong(startPos + Long.SIZE, Math.min(length, startPos + 2 * Long.SIZE)) << Long.SIZE
              + l - startBit >>> l;
    }

    public long length() {
      return length;
    }
  }

  @Override
  public long length(final CharSequence s) {
    return (s.length() + (prefixFree ? 1 : 0)) * (long) Byte.SIZE;
  }

  @Override
  public BitVector toBitVector(final CharSequence s) {
    return new ISOCharSequenceBitVector(s, prefixFree);
  }

  @Override
  public long numBits() {
    return 0;
  }

  @Override
  public TransformationStrategy<CharSequence> copy() {
    return new DictionaryTransformationStrategy(prefixFree);
  }

  public static class Comparator implements java.util.Comparator<String> {
    private final TransformationStrategy<CharSequence> strategy =
        new DictionaryTransformationStrategy(true);

    @Override
    public int compare(String s1, String s2) {
      return strategy.toBitVector(s1).compareTo(strategy.toBitVector(s2));
    }
  }

  public static class WritableComparator extends org.apache.hadoop.io.WritableComparator {
    private final TransformationStrategy<CharSequence> strategy =
        new DictionaryTransformationStrategy(true);

    public WritableComparator() {
      super(Text.class);
    }

    public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
      int n1 = WritableUtils.decodeVIntSize(b1[s1]);
      int n2 = WritableUtils.decodeVIntSize(b2[s2]);

      String t1 = null, t2 = null;
      try {
        t1 = Text.decode(b1, s1 + n1, l1 - n1);
        t2 = Text.decode(b2, s2 + n2, l2 - n2);
      } catch (CharacterCodingException e) {
        throw new RuntimeException(e);
      }

      return strategy.toBitVector(t1).compareTo(strategy.toBitVector(t2));
    }
  }
}
