package dk.alexandra.fresco.suite.dummy.arithmetic;

import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.builder.numeric.FieldElement;
import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.framework.value.SInt;
import java.math.BigInteger;

/**
 * Implements openings for the Dummy Arithmetic protocol suite, where all operations are done in the
 * clear.
 */
public class DummyArithmeticOpenToAllProtocol extends DummyArithmeticNativeProtocol<BigInteger> {

  DRes<SInt> closed;
  BigInteger opened;

  /**
   * Constructs a native protocol to open a closed integer towards all parties.
   *
   * @param s a computation supplying the {@link SInt} to open
   */
  public DummyArithmeticOpenToAllProtocol(DRes<SInt> s) {
    super();
    this.closed = s;
  }

  @Override
  public EvaluationStatus evaluate(int round, DummyArithmeticResourcePool resourcePool,
      Network network) {
    FieldElement value = ((DummyArithmeticSInt) closed.out()).getValue();
    opened = resourcePool.convertRepresentation(value);
    return EvaluationStatus.IS_DONE;
  }

  @Override
  public BigInteger out() {
    return opened;
  }
}
