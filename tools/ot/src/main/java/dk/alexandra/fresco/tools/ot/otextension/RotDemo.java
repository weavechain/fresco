package dk.alexandra.fresco.tools.ot.otextension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import dk.alexandra.fresco.framework.Party;
import dk.alexandra.fresco.framework.configuration.NetworkConfiguration;
import dk.alexandra.fresco.framework.configuration.NetworkConfigurationImpl;
import dk.alexandra.fresco.framework.network.KryoNetNetwork;
import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.framework.sce.resources.ResourcePool;
import dk.alexandra.fresco.framework.util.Pair;
import dk.alexandra.fresco.framework.util.StrictBitVector;
import dk.alexandra.fresco.tools.cointossing.FailedCoinTossingException;
import dk.alexandra.fresco.tools.commitment.FailedCommitmentException;
import dk.alexandra.fresco.tools.commitment.MaliciousCommitmentException;

/**
 * Demo class for execute a light instance of random OT extension.
 * 
 * @author jot2re
 *
 * @param <ResourcePoolT>
 *          The FRESCO resource pool used for the execution
 */
public class RotDemo<ResourcePoolT extends ResourcePool> {
  // Computational security parameter
  private int kbitLength = 128;
  // Statistical security parameter
  private int lambdaSecurityParam = 40;
  // Amount of random OTs to construct
  private int amountOfOTs = 88;

  /**
   * Run the receiving party.
   * 
   * @param pid
   *          The PID of the receiving party
   * @throws FailedCoinTossingException
   *           Thrown in case something, non-malicious, goes wrong in the coin
   * @throws FailedCommitmentException
   *           Thrown in case something, non-malicious, goes wrong in the
   *           commitment protocol. tossing protocol.
   * @throws MaliciousCommitmentException
   *           Thrown in case the other party actively tries to cheat.
   * @throws FailedOtExtensionException
   *           Thrown in case something, non-malicious, goes wrong.
   * @throws MaliciousOtExtensionException
   *           Thrown if cheating occurred
   */
  public void runPartyOne(int pid)
      throws MaliciousCommitmentException, FailedCommitmentException,
      FailedCoinTossingException, FailedOtExtensionException, MaliciousOtExtensionException {
    Network network = new KryoNetNetwork(getNetworkConfiguration(pid));
    System.out.println("Connected receiver");
    Random rand = new Random(42);
    Rot rot = new Rot(1, 2, kbitLength, lambdaSecurityParam, rand, network);
    RotReceiver rotRec = rot.getReceiver();
    rotRec.initialize();
    byte[] otChoices = new byte[amountOfOTs / 8];
    rand.nextBytes(otChoices);
    List<StrictBitVector> vvec = rotRec
        .extend(new StrictBitVector(otChoices, amountOfOTs));
    System.out.println("done receiver");
    for (int i = 0; i < amountOfOTs; i++) {
      System.out.print(i + ": ");
      byte[] output = vvec.get(i).toByteArray();
      for (byte current : output) {
        System.out.print(String.format("%02x ", current));
      }
      System.out.println();
    }
  }

  /**
   * Run the sending party.
   * 
   * @param pid
   *          The PID of the sending party
   * @throws FailedCoinTossingException
   *           Thrown in case something, non-malicious, goes wrong in the coin
   * @throws FailedCommitmentException
   *           Thrown in case something, non-malicious, goes wrong in the
   *           commitment protocol. tossing protocol.
   * @throws FailedOtExtensionException
   *           Thrown in case something, non-malicious, goes wrong.
   * @throws MaliciousCommitmentException
   *           Thrown in case the other party actively tries to cheat in the
   *           commitments.
   * @throws MaliciousOtExtensionException
   *           Thrown in case the other party actively tries to cheat.
   */
  public void runPartyTwo(int pid)
      throws FailedOtExtensionException,
      MaliciousCommitmentException, FailedCommitmentException,
      FailedCoinTossingException, MaliciousOtExtensionException {
    Network network = new KryoNetNetwork(getNetworkConfiguration(pid));
    System.out.println("Connected sender");
    Random rand = new Random(420);
    Rot rot = new Rot(2, 1, kbitLength, lambdaSecurityParam, rand, network);
    RotSender rotSnd = rot.getSender();
    rotSnd.initialize();
    Pair<List<StrictBitVector>, List<StrictBitVector>> vpairs = rotSnd
        .extend(amountOfOTs);
    System.out.println("done sender");
    for (int i = 0; i < amountOfOTs; i++) {
      System.out.println(i + ": ");
      byte[] outputZero = vpairs.getFirst().get(i).toByteArray();
      System.out.println("0-choice: ");
      for (byte current : outputZero) {
        System.out.print(String.format("%02x ", current));
      }
      byte[] outputOne = vpairs.getSecond().get(i).toByteArray();
      System.out.println("\n1-choice: ");
      for (byte current : outputOne) {
        System.out.print(String.format("%02x ", current));
      }
      System.out.println();
    }
  }

  /**
   * The main function, taking one argument, the PID of the calling party.
   * 
   * @param args
   *          Argument list, consisting of only the PID
   */
  public static void main(String[] args) {
    int pid = Integer.parseInt(args[0]);
    try {
      if (pid == 1) {
        new RotDemo<>().runPartyOne(pid);
      } else {
        new RotDemo<>().runPartyTwo(pid);
      }
    } catch (Exception e) {
      System.out.println("Failed to connect: " + e);
      e.printStackTrace(System.out);
    }
  }

  private static NetworkConfiguration getNetworkConfiguration(int pid) {
    Map<Integer, Party> parties = new HashMap<>();
    parties.put(1, new Party(1, "localhost", 8001));
    parties.put(2, new Party(2, "localhost", 8002));
    return new NetworkConfigurationImpl(pid, parties);
  }
}
