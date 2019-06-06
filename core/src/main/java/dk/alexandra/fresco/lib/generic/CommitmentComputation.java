package dk.alexandra.fresco.lib.generic;

import dk.alexandra.fresco.commitment.HashBasedCommitment;
import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.builder.Computation;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.network.serializers.ByteSerializer;
import dk.alexandra.fresco.framework.util.Drbg;
import dk.alexandra.fresco.framework.util.Pair;
import java.util.ArrayList;
import java.util.List;

/**
 * Protocol for all parties to commit to a value each and open it to the other parties.
 */
public class CommitmentComputation implements
    Computation<List<byte[]>, ProtocolBuilderNumeric> {

  private final ByteSerializer<HashBasedCommitment> commitmentSerializer;
  private final byte[] value;
  private final int noOfParties;
  private final Drbg localDrbg;

  public CommitmentComputation(ByteSerializer<HashBasedCommitment> commitmentSerializer,
      byte[] value, int noOfParties, Drbg localDrbg) {
    this.commitmentSerializer = commitmentSerializer;
    this.value = value;
    this.noOfParties = noOfParties;
    this.localDrbg = localDrbg;
  }

  @Override
  public DRes<List<byte[]>> buildComputation(ProtocolBuilderNumeric builder) {
    HashBasedCommitment ownCommitment = new HashBasedCommitment();
    byte[] ownOpening = ownCommitment.commit(localDrbg, value);
    return builder.seq(
        seq -> {
          if (noOfParties > 2) {
            return new BroadcastComputation<ProtocolBuilderNumeric>(
                commitmentSerializer.serialize(ownCommitment))
                .buildComputation(seq);
          } else {
            return seq.append(new InsecureBroadcastProtocol<>(
                commitmentSerializer.serialize(ownCommitment)));
          }
        })
        .seq((seq, rawCommitments) -> {
          DRes<List<byte[]>> res = seq.append(new InsecureBroadcastProtocol<>(ownOpening));
          final Pair<DRes<List<byte[]>>, List<byte[]>> dResListPair = new Pair<>(res,
              rawCommitments);
          return () -> dResListPair;
        })
        .seq((seq, pair) -> {
          List<HashBasedCommitment> commitments = commitmentSerializer
              .deserializeList(pair.getSecond());
          List<byte[]> opened = open(commitments, pair.getFirst().out(), noOfParties);
          return () -> opened;
        });
  }

  private List<byte[]> open(List<HashBasedCommitment> commitments, List<byte[]> openings,
      int noOfParties) {
    List<byte[]> result = new ArrayList<>(commitments.size());
    for (int i = 0; i < noOfParties; i++) {
      HashBasedCommitment commitment = commitments.get(i);
      byte[] opening = openings.get(i);
      result.add(commitment.open(opening));
    }
    return result;
  }

}
