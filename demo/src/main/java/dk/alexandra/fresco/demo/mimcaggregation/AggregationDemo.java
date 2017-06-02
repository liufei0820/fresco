package dk.alexandra.fresco.demo.mimcaggregation;

import dk.alexandra.fresco.framework.Party;
import dk.alexandra.fresco.framework.ProtocolEvaluator;
import dk.alexandra.fresco.framework.configuration.NetworkConfiguration;
import dk.alexandra.fresco.framework.configuration.PreprocessingStrategy;
import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.framework.network.NetworkingStrategy;
import dk.alexandra.fresco.framework.sce.SecureComputationEngine;
import dk.alexandra.fresco.framework.sce.SecureComputationEngineImpl;
import dk.alexandra.fresco.framework.sce.configuration.ProtocolSuiteConfiguration;
import dk.alexandra.fresco.framework.sce.configuration.SCEConfiguration;
import dk.alexandra.fresco.framework.sce.evaluator.SequentialEvaluator;
import dk.alexandra.fresco.framework.sce.resources.storage.InMemoryStorage;
import dk.alexandra.fresco.framework.sce.resources.storage.Storage;
import dk.alexandra.fresco.framework.sce.resources.storage.StreamedStorage;
import dk.alexandra.fresco.framework.value.OInt;
import dk.alexandra.fresco.framework.value.SInt;
import dk.alexandra.fresco.framework.value.Value;
import dk.alexandra.fresco.suite.ProtocolSuite;
import dk.alexandra.fresco.suite.spdz.configuration.SpdzConfiguration;
import dk.alexandra.fresco.suite.spdz.evaluation.strategy.SpdzProtocolSuite;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class AggregationDemo {

  /**
   * @return Generates mock input data.
   */
  public int[][] readInputs() {
    return new int[][]{
        {1, 10},
        {1, 7},
        {2, 100},
        {1, 50},
        {3, 15},
        {3, 15},
        {2, 70}
    };
  }

  /**
   * @param result Prints result values to console.
   */
  public void writeOutputs(int[][] result) {
    for (int[] row : result) {
      for (int value : row) {
        System.out.print(value + " ");
      }
      System.out.println();
    }
  }

  /**
   * @return Uses deterministic encryption (in this case MiMC) for encrypt, under MPC, the values in
   * the specified column, and opens the resulting cipher texts. The resulting OInts are appended to
   * the end of each row.
   *
   * NOTE: This leaks the equality of the encrypted input values.
   *
   * Example: ([k], [v]) -> ([k], [v], enc(k)) for columnIndex = 0
   */
  public Value[][] encryptAndReveal(SecureComputationEngine sce, SInt[][] inputRows,
      int columnIndex) {
    EncryptAndRevealStep ear = new EncryptAndRevealStep(inputRows, columnIndex);
    sce.runApplication(ear);
    return ear.getRowsWithOpenedCiphers();
  }

  /**
   * @return Takes in a secret-shared collection of rows (2d-array) and returns the secret-shared
   * result of a sum aggregation of the values in the agg column grouped by the values in the key
   * column.
   *
   * This method invokes encryptAndReveal and the aggregate step.
   *
   * Example: ([1], [2]), ([1], [3]), ([2], [4]) -> ([1], [5]), ([2], [4]) for keyColumn = 0 and
   * aggColumn = 1
   */
  public SInt[][] aggregate(SecureComputationEngine sce, SInt[][] inputRows, int keyColumn,
      int aggColumn) {
    // TODO: need to shuffle input rows and result
    Value[][] rowsWithOpenenedCiphers = encryptAndReveal(sce, inputRows, keyColumn);
    AggregateStep aggStep = new AggregateStep(
        rowsWithOpenenedCiphers, 2, keyColumn, aggColumn);
    sce.runApplication(aggStep);
    return aggStep.getResult();
  }

  /**
   * @return Runs the input step which secret shares all int values in inputRows. Returns and SInt
   * array containing the resulting shares.
   */
  public SInt[][] secretShare(SecureComputationEngine sce, int[][] inputRows, int pid) {
    InputStep inputStep = new InputStep(inputRows, pid);
    sce.runApplication(inputStep);
    return inputStep.getSecretSharedRows();
  }

  /**
   * @return Runs the output step which opens all secret shares. It converts the result to ints.
   */
  public int[][] open(SecureComputationEngine sce, SInt[][] secretShares) {
    OutputStep outputStep = new OutputStep(secretShares);
    sce.runApplication(outputStep);
    OInt[][] _opened = outputStep.getOpenedRows();
    int[][] opened = new int[_opened.length][_opened[0].length];
    int rowIndex = 0,
        colIndex = 0;
    for (OInt[] row : _opened) {
      for (OInt value : row) {
        opened[rowIndex][colIndex] = value.getValue().intValue();
        colIndex++;
      }
      rowIndex++;
      colIndex = 0;
    }
    return opened;
  }

  public void runApplication(SecureComputationEngine sce) {
    int pid = sce.getSCEConfiguration().getMyId();
    int keyColumnIndex = 0;
    int aggColumnIndex = 1;

    // Read inputs. For now this just returns a hard-coded array of values.
    int[][] inputRows = readInputs();

    // Secret-share the inputs.
    SInt[][] secretSharedRows = secretShare(sce, inputRows, pid);

    // Aggregate
    SInt[][] aggregated = aggregate(
        sce, secretSharedRows, keyColumnIndex, aggColumnIndex);

    // Recombine the secret shares of the result
    int[][] openedResult = open(sce, aggregated);

    // Write outputs. For now this just prints the results to the console.
    writeOutputs(openedResult);

    sce.shutdownSCE();
  }

  public static void main(String[] args) {

    // My player ID
    int myPID = Integer.parseInt(args[0]);

    // Set up our SecureComputationEngine configuration
    SCEConfiguration sceConfig = new SCEConfiguration() {

      @Override
      public int getMyId() {
        return myPID;
      }

      @Override
      public Map<Integer, Party> getParties() {
        // Set up network details of our two players
        Map<Integer, Party> parties = new HashMap<Integer, Party>();
        parties.put(1, new Party(1, "localhost", 8001));
        parties.put(2, new Party(2, "localhost", 8002));
        return parties;
      }

      @Override
      public Level getLogLevel() {
        return Level.INFO;
      }

      @Override
      public int getNoOfThreads() {
        return 2;
      }

      @Override
      public int getNoOfVMThreads() {
        return 2;
      }

      @Override
      public ProtocolEvaluator getEvaluator() {
        // We will use a sequential evaluation strategy
        ProtocolEvaluator evaluator = new SequentialEvaluator();
        return evaluator;
      }

      @Override
      public Storage getStorage() {
        return new InMemoryStorage();
      }

      @Override
      public int getMaxBatchSize() {
        return 4096;
      }

      @Override
      public StreamedStorage getStreamedStorage() {
        // We will not use StreamedStorage
        return null;
      }

      @Override
      public NetworkingStrategy getNetworkStrategy() {
        return NetworkingStrategy.KRYONET;
      }

      @Override
      public Network getNetwork(NetworkConfiguration configuration, int channelAmount) {
        return null;
      }
    };

    ProtocolSuiteConfiguration protocolSuiteConfig = new SpdzConfiguration() {
      @Override
      public ProtocolSuite createProtocolSuite(int myPlayerId) {
        return new SpdzProtocolSuite(myPlayerId, this);
      }

      @Override
      public PreprocessingStrategy getPreprocessingStrategy() {
        return PreprocessingStrategy.DUMMY;
      }

      @Override
      public String fuelStationBaseUrl() {
        return null;
      }

      @Override
      public int getMaxBitLength() {
        return 150;
      }
    };

    // Instantiate environment
    SCEConfiguration sceConf = sceConfig;
    ProtocolSuiteConfiguration psConf = protocolSuiteConfig;
    SecureComputationEngine sce = new SecureComputationEngineImpl(sceConf, psConf);

    // Create application we are going run
    AggregationDemo app = new AggregationDemo();

    app.runApplication(sce);

    return;

  }
}