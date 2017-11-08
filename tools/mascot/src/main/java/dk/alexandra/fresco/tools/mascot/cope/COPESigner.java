package dk.alexandra.fresco.tools.mascot.cope;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.tools.mascot.field.FieldElement;

public class COPESigner extends COPEShared {

  private List<BigInteger> chosenSeeds;
  private FieldElement macKeyShare;

  public COPESigner(int otherID, int kBitLength, int lambdaSecurityParam, Random rand,
      FieldElement macKeyShare, Network network, BigInteger prime) {
    super(otherID, kBitLength, lambdaSecurityParam, rand, network, prime);
    this.chosenSeeds = new ArrayList<>();
    this.macKeyShare = macKeyShare;
  }

  public void initialize() {
    chosenSeeds = ot.receive(macKeyShare.toBigInteger(), kBitLength);
    System.out.println(chosenSeeds);
    initialized = true;
  }

  public FieldElement extend() {
    if (!initialized) {
      throw new IllegalStateException("Cannot call extend before initializing");
    }
    List<FieldElement> uValues = new ArrayList<>();
    // TODO: batch receive
    try {
      for (int k = 0; k < kBitLength; k++) {
        BigInteger raw = new BigInteger(network.receive(0, otherID));
        uValues.add(new FieldElement(raw, modulus, kBitLength));
      }
    } catch (Exception e) {
      System.out.println("Broke while receiving");
      e.printStackTrace(System.out);
      return null;
    }
    List<FieldElement> qValues = new ArrayList<>();
    // TODO: optimize
    int k = 0;
    for (FieldElement uValue : uValues) {
      BigInteger chosenSeed = chosenSeeds.get(k);
      FieldElement tChoice = this.prf.evaluate(chosenSeed, counter, modulus, kBitLength);
      boolean bit = macKeyShare.getBit(k);
      FieldElement qValue = uValue.select(bit).add(tChoice);
      qValues.add(qValue);
      k++;
    }
    counter = counter.add(BigInteger.ONE);
    return FieldElement.recombine(qValues, modulus, kBitLength);
  }

  public List<FieldElement> extend(int numInputs) {
    List<FieldElement> shares = new ArrayList<>();
    for (int r = 0; r < numInputs; r++) {
      shares.add(extend());
    }
    return shares;
  }

}
