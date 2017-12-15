package dk.alexandra.fresco.tools.ot.otextension;

import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.framework.util.Drbg;
import dk.alexandra.fresco.framework.util.StrictBitVector;
import dk.alexandra.fresco.tools.cointossing.CoinTossing;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Superclass containing the common variables and methods for the sender and
 * receiver parties of random OT extension.
 *
 * @author jot2re
 *
 */
public class RotShared {
  // Internal state variables
  protected CoteShared cote;
  protected CoinTossing ct;
  protected boolean initialized = false;
  protected final String hashAlgorithm;

  /**
   * Constructs a random OT extension super-class using an underlying correlated
   * OT with errors object.
   *
   * @param cote
   *          The underlying correlated OT with errors
   */
  public RotShared(CoteShared cote) {
    super();
    this.cote = cote;
    this.ct = new CoinTossing(cote.getMyId(), cote.getOtherId(),
        cote.getRand(), cote.getNetwork());
    this.hashAlgorithm = "SHA-256";
  }

  public int getOtherId() {
    return cote.getOtherId();
  }

  public int getKbitLength() {
    return cote.getkBitLength();
  }

  public int getLambdaSecurityParam() {
    return cote.getLambdaSecurityParam();
  }

  public Drbg getRand() {
    return cote.getRand();
  }

  public Network getNetwork() {
    return cote.getNetwork();
  }

  /**
   * Computes the inner product of two lists of StrictBitVector objects. The
   * product will be based on Galois multiplication in the binary extension
   * field of the individual elements of the lists, but without reducing modulo
   * a reduction polynomial. Thus the inner product is computed purely using
   * shifts and XOR operations.
   * <p>
   * All elements of both lists MUST have equal size! And both lists MUST
   * contain an equal amount of entries!
   * </p>
   *
   * @param alist
   *          The first input list, with all elements of equal size
   * @param blist
   *          The second input list, with all elements of equal size
   * @return The inner product represented as a StrictBitVector
   */
  protected static StrictBitVector computeInnerProduct(
      List<StrictBitVector> alist, List<StrictBitVector> blist) {
    // All elements of each list MUST have equal size so we find the size using
    // the first element.
    StrictBitVector res = new StrictBitVector(
        alist.get(0).getSize() + blist.get(0).getSize());
    for (int i = 0; i < alist.size(); i++) {
      // Multiply entry i of alist with entry i of blist
      StrictBitVector temp = multiplyWithoutReduction(alist.get(i),
          blist.get(i));
      // XOR the product into the res holder as XOR constitutes multiplication
      // in the binary Galois extension field.
      res.xor(temp);
    }
    return res;
  }

  /**
   * Computes the Galois product of two bit vectors, without reduction modulo a
   * reduction polynomial.
   *
   * @param avec
   *          The first bit vector
   * @param bvec
   *          The second bit vector
   * @return The product represented as a StrictBitVector
   */
  protected static StrictBitVector multiplyWithoutReduction(
      StrictBitVector avec, StrictBitVector bvec) {
    byte[] res = new byte[(avec.getSize() + bvec.getSize()) / 8];
    byte[] avecBytes = avec.toByteArray();
    byte[] bvecBytes = bvec.toByteArray();
    // TODO automatically do it st we iterate over the smallest length
    for (int i = 0; i < avec.getSize(); i++) {
      // If the i'th bit of avec is 1 then we shift the bvec i positions and xor
      // the shifted bvec into the result vector, res
      byte currentByte = (byte) (avecBytes[i / 8] >> (7 - (i % 8)));
      if ((currentByte & 1) == 1) {
        byte start = (byte) ((bvecBytes[0] << 24) >>> (24 + (i % 8)));
        res[(i / 8) + 0] ^= start;
        for (int j = 1; j < bvecBytes.length; j++) {
          byte first = (byte) (bvecBytes[j - 1] << (8 - (i % 8)));
          byte second = (byte) ((bvecBytes[j] << 24) >>> (24 + (i % 8)));
          second ^= first;
          res[(i / 8) + j] ^= second;
        }
        byte last = (byte) (bvecBytes[bvecBytes.length - 1] << (8 - (i % 8)));
        res[(i / 8) + bvecBytes.length] ^= last;
      }
    }
    return new StrictBitVector(res, avec.getSize() + bvec.getSize());
  }

  // protected static StrictBitVector multiplyWithoutReduction(
  // StrictBitVector avec, StrictBitVector bvec) {
  // StrictBitVector res = new StrictBitVector(avec.getSize() + bvec.getSize());
  // for (int i = 0; i < avec.getSize(); i++) {
  // // If the i'th bit of avec is 1 then we shift the bvec i positions and xor
  // // the shifted bvec into the result vector, res
  // if (avec.getBit(i, false) == true) {
  // StrictBitVector temp = new StrictBitVector(
  // avec.getSize() + bvec.getSize());
  // shiftArray(bvec, i, temp);
  // res.xor(temp);
  // }
  // }
  // return res;
  // }

  /**
   * Shifts a bitvector "pos" positions by constructing a new StrictBitVector of
   * capacity "maxSize".
   *
   * @param in
   *          The bitvector to shift
   * @param pos
   *          The amount of positions to shift
   * @param dest
   *          The vector which the shifted bit vector should be stored
   */
  private static void shiftArray(StrictBitVector in, int pos,
      StrictBitVector dest) {
    for (int i = 0; i < in.getSize(); i++) {
      dest.setBit(i + pos, in.getBit(i, false), false);
    }
  }

  /**
   * Compute a SHA-256 digest of elements in a list, concatenated with their
   * index in the list. Only the first "size" elements of the list will be
   * hashed.
   *
   * @param input
   *          The list of StrictBitVector elements to hash. All elements MUST
   *          have same length
   * @param size
   *          The amount of elements of the list, to hash. Must be less than or
   *          equal to the amount of elements in the list.
   * @param digest
   *          THe generic digest for hashing
   * @return A list containing the hashed StrictBitVector as StrictBitVector
   *         objects
   */
  protected List<StrictBitVector> hashBitVector(List<StrictBitVector> input,
      int size, MessageDigest digest) {
    List<StrictBitVector> res = new ArrayList<>(size);
    // Allocate a buffer to contain the index of the value to hash along with
    // the value itself.
    // Size of an int is always 4 bytes in java
    ByteBuffer indexBuffer = ByteBuffer
        .allocate(4 + input.get(0).getSize() * 8);
    byte[] hash;
    for (int i = 0; i < size; i++) {
      indexBuffer.clear();
      // Move the index into the buffer
      indexBuffer.putInt(i);
      // Move the value to hash into the buffer
      indexBuffer.put(input.get(i).toByteArray());
      hash = digest.digest(indexBuffer.array());
      // Allocate the new bitvector, which contains 256 bits since SHA-256 is
      // used
      res.add(new StrictBitVector(hash, 256));
    }
    return res;
  }

  /**
   * Agree on a list of "size" coin-tossed elements, represented by
   * StrictBitVectors. Each consisting of bits reflecting the computational
   * security used at initialization of this class
   *
   * @param size
   *          The amount of elements in the resultant list
   * @return The list of coin-tossed elements
   */
  protected List<StrictBitVector> getChallenges(int size) {
    List<StrictBitVector> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      StrictBitVector currentToss = ct.toss(getKbitLength());
      list.add(currentToss);
    }
    return list;
  }
}
