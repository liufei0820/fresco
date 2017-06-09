/*
 * Copyright (c) 2015, 2016 FRESCO (http://github.com/aicis/fresco).
 *
 * This file is part of the FRESCO project.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * FRESCO uses SCAPI - http://crypto.biu.ac.il/SCAPI, Crypto++, Miracl, NTL,
 * and Bouncy Castle. Please see these projects for any further licensing issues.
 *******************************************************************************/
package dk.alexandra.fresco.suite.spdz.evaluation.strategy;

import dk.alexandra.fresco.framework.MPCException;
import dk.alexandra.fresco.framework.NativeProtocol;
import dk.alexandra.fresco.framework.ProtocolCollectionList;
import dk.alexandra.fresco.framework.ProtocolFactory;
import dk.alexandra.fresco.framework.Reporter;
import dk.alexandra.fresco.framework.network.SCENetwork;
import dk.alexandra.fresco.framework.sce.evaluator.BatchedStrategy;
import dk.alexandra.fresco.framework.sce.resources.ResourcePool;
import dk.alexandra.fresco.suite.ProtocolSuite;
import dk.alexandra.fresco.suite.spdz.configuration.SpdzConfiguration;
import dk.alexandra.fresco.suite.spdz.datatypes.SpdzCommitment;
import dk.alexandra.fresco.suite.spdz.gates.SpdzCommitProtocol;
import dk.alexandra.fresco.suite.spdz.gates.SpdzMacCheckProtocol;
import dk.alexandra.fresco.suite.spdz.gates.SpdzOpenCommitProtocol;
import dk.alexandra.fresco.suite.spdz.storage.SpdzStorage;
import dk.alexandra.fresco.suite.spdz.storage.SpdzStorageDummyImpl;
import dk.alexandra.fresco.suite.spdz.storage.SpdzStorageImpl;
import dk.alexandra.fresco.suite.spdz.utils.SpdzFactory;
import dk.alexandra.fresco.suite.spdz.utils.Util;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class SpdzProtocolSuite implements ProtocolSuite {

  private static Map<Integer, SpdzProtocolSuite> instances = new HashMap<>();

  private SecureRandom rand;
  private SpdzStorage[] store;
  private int gatesEvaluated = 0;
  private static final int macCheckThreshold = 100000;
  private BigInteger p;
  private MessageDigest[] digs;
  private SpdzConfiguration spdzConf;

  //This is set whenever an output protocol was evaluated. It means that the sync point needs to do a MAC check.
  private boolean outputProtocolInBatch = false;
  private long totalMacTime;
  private long lastMacEnd;
  private long totalNoneMacTime;
  private int totalSizeOfValues;

  public void outputProtocolUsedInBatch() {
    outputProtocolInBatch = true;
  }

  public SpdzProtocolSuite(int id, SpdzConfiguration spdzConf) {
    this.spdzConf = spdzConf;
    synchronized (instances) {
      instances.put(id, this);
    }
  }

  public static SpdzProtocolSuite getInstance(int id) {
    if (instances.get(id) == null) {
      throw new IllegalStateException("Not properly initialized");
    }
    return instances.get(id);
  }

  public SpdzStorage getStore(int i) {
    return store[i];
  }

  public BigInteger getModulus() {
    return p;
  }

  public SpdzConfiguration getConf() {
    return this.spdzConf;
  }

  public MessageDigest getMessageDigest(int threadId) {
    return this.digs[threadId];
  }

  @Override
  public ProtocolFactory init(ResourcePool resourcePool) {
    int noOfThreads = 1;
    this.store = new SpdzStorage[noOfThreads];
    for (int i = 0; i < noOfThreads; i++) {
      switch (spdzConf.getPreprocessingStrategy()) {
        case DUMMY:
          store[i] = new SpdzStorageDummyImpl(resourcePool.getMyId(),
              resourcePool.getNoOfParties());
          break;
        case STATIC:
          store[i] = new SpdzStorageImpl(resourcePool, i);
          break;
        case FUELSTATION:
          store[i] = new SpdzStorageImpl(resourcePool, i, true, spdzConf.fuelStationBaseUrl());
      }
    }
    this.rand = resourcePool.getSecureRandom();

    try {
      this.digs = new MessageDigest[noOfThreads];
      for (int i = 0; i < this.digs.length; i++) {
        this.digs[i] = MessageDigest.getInstance("SHA-256");
      }
    } catch (NoSuchAlgorithmException e) {
      Reporter.warn("SHA-256 not supported as digest on this system. Might not influence "
          + "computation if your chosen SCPS does not depend on a hash function.");
    }

    try {
      this.store[0].getSSK();
    } catch (MPCException e) {
      throw new MPCException("No preprocessed data found for SPDZ - aborting.", e);
    }

    // Initialize various fields global to the computation.
    this.p = store[0].getSupplier().getModulus();
    Util.setModulus(this.p);

    int maxBitLength = spdzConf.getMaxBitLength();
    return new SpdzFactory(getStore(0), resourcePool.getMyId(), maxBitLength);
  }

  @Override
  public void finishedEval(ResourcePool resourcePool, SCENetwork sceNetwork) {
    try {
      MACCheck(null, resourcePool, sceNetwork);
      this.gatesEvaluated = 0;
    } catch (IOException e) {
      throw new MPCException("Could not complete MACCheck.", e);
    }
  }

  @Override
  public void destroy() {
    for (SpdzStorage store : this.store) {
      store.shutdown();
    }
  }

  @Override
  public RoundSynchronization createRoundSynchronization() {
    return new SpdzRoundSynchronization();
  }

  private int logCount = 0;
  private int synchronizeCalls;
  private long sumOfGates = 0;

  private void MACCheck(Map<Integer, BigInteger> commitments, ResourcePool resourcePool,
      SCENetwork sceNetworks) throws IOException {
    for (int i = 1; i < store.length; i++) {
      store[0].getOpenedValues().addAll(store[i].getOpenedValues());
      store[0].getClosedValues().addAll(store[i].getClosedValues());
      store[i].reset();
    }

    long start = System.currentTimeMillis();
    if (lastMacEnd > 0) {
      totalNoneMacTime += start - lastMacEnd;
    }

    SpdzStorage storage = store[0];
    sumOfGates += SpdzProtocolSuite.this.gatesEvaluated;
    totalSizeOfValues += storage.getOpenedValues().size();

    if (logCount++ > 1500) {
      Reporter.info("MacChecking(" + logCount + ")"
          + ", AverageGateSize=" + (logCount > 0 ? sumOfGates / logCount : "?")
          + ", OpenedValuesSize=" + totalSizeOfValues
          + ", SynchronizeCalls=" + synchronizeCalls
          + ", MacTime=" + totalMacTime
          + ", noneMacTime=" + totalNoneMacTime);
      logCount = 0;
      synchronizeCalls = 0;
      sumOfGates = 0;
      totalSizeOfValues = 0;
    }
    SpdzMacCheckProtocol macCheck = new SpdzMacCheckProtocol(
        rand,
        this.digs[sceNetworks.getThreadId()],
        storage,
        commitments);

    int batchSize = 128;

    do {
      ProtocolCollectionList protocolCollectionList =
          new ProtocolCollectionList(batchSize);
      macCheck.getNextProtocols(protocolCollectionList);

      BatchedStrategy.processBatch(protocolCollectionList, sceNetworks, 0, resourcePool);
    } while (macCheck.hasNextProtocols());

    //reset boolean value
    this.outputProtocolInBatch = false;
    this.gatesEvaluated = 0;
    totalMacTime += System.currentTimeMillis() - start;
    lastMacEnd = System.currentTimeMillis();
  }

  private class SpdzRoundSynchronization implements RoundSynchronization {

    private Map<Integer, BigInteger> commitments;
    boolean commitDone = false;
    boolean openDone = false;
    int roundNumber = 0;
    private final SpdzCommitProtocol commitProtocol;
    private final SpdzOpenCommitProtocol openProtocol;

    SpdzRoundSynchronization() {
      BigInteger s = new BigInteger(Util.getModulus().bitLength(), rand).mod(Util.getModulus());
      SpdzCommitment commitment = new SpdzCommitment(digs[0], s, rand);
      Map<Integer, BigInteger> comms = new HashMap<>();
      commitProtocol = new SpdzCommitProtocol(commitment, comms);
      Map<Integer, BigInteger> commitments = new HashMap<>();
      openProtocol = new SpdzOpenCommitProtocol(commitment, comms, commitments);
    }

    @Override
    public void finishedBatch(int gatesEvaluated, ResourcePool resourcePool, SCENetwork sceNetwork)
        throws MPCException {
      SpdzProtocolSuite.this.gatesEvaluated += gatesEvaluated;
      SpdzProtocolSuite.this.synchronizeCalls++;
      if (SpdzProtocolSuite.this.gatesEvaluated > macCheckThreshold || outputProtocolInBatch) {
        try {

          MACCheck(openDone ? this.commitments : null, resourcePool, sceNetwork);
          this.commitments = null;
        } catch (IOException e) {
          throw new MPCException("Could not complete MACCheck.", e);
        }
      }
    }

    @Override
    public boolean roundFinished(int round, ResourcePool resourcePool, SCENetwork sceNetwork)
        throws MPCException {
      if (outputProtocolInBatch) {

        if (!commitDone) {
          NativeProtocol.EvaluationStatus evaluate = commitProtocol
              .evaluate(round, resourcePool, sceNetwork);
          roundNumber = round + 1;
          commitDone = evaluate.equals(NativeProtocol.EvaluationStatus.IS_DONE);
          return false;
        }
        if (!openDone) {
          NativeProtocol.EvaluationStatus evaluate = openProtocol
              .evaluate(round - roundNumber, resourcePool, sceNetwork);
          openDone = evaluate.equals(NativeProtocol.EvaluationStatus.IS_DONE);
        }
        return openDone;
      }
      return true;
    }
  }
}