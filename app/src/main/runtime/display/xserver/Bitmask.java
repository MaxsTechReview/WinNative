package com.winlator.cmod.runtime.display.xserver;

import androidx.annotation.NonNull;
import java.util.Iterator;

public class Bitmask implements Iterable<Long> {
  private long bits = 0;

  public Bitmask() {}

  public Bitmask(long bits) {
    this.bits = bits;
  }

  public boolean isSet(long flag) {
    return (flag & this.bits) != 0;
  }

  public boolean intersects(Bitmask mask) {
    return (mask.bits & this.bits) != 0;
  }

  public void set(long flag) {
    bits |= flag;
  }

  public void set(long flag, boolean value) {
    if (value) {
      set(flag);
    } else unset(flag);
  }

  public void unset(long flag) {
    bits &= ~flag;
  }

  public boolean isEmpty() {
    return bits == 0;
  }

  public long getBits() {
    return bits;
  }

  public void join(Bitmask mask) {
    this.bits = mask.bits | this.bits;
  }

  @NonNull @Override
  public Iterator<Long> iterator() {
    final long[] bits = {this.bits};
    return new Iterator<Long>() {
      @Override
      public boolean hasNext() {
        return bits[0] != 0;
      }

      @Override
      public Long next() {
        long index = Long.lowestOneBit(bits[0]);
        bits[0] &= ~index;
        return index;
      }
    };
  }

  @Override
  public int hashCode() {
    return Long.hashCode(bits);
  }
}
