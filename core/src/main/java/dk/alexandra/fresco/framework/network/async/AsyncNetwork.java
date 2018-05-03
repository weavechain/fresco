package dk.alexandra.fresco.framework.network.async;

import dk.alexandra.fresco.framework.Party;
import dk.alexandra.fresco.framework.configuration.NetworkConfiguration;
import dk.alexandra.fresco.framework.network.CloseableNetwork;
import dk.alexandra.fresco.framework.util.ExceptionConverter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple network implementation.
 * <p>
 * Implements non-blocking sends and blocking receives. Uses two threads per opposing party, one for
 * sending and one for receiving messages.
 * </p>
 */
public class AsyncNetwork implements CloseableNetwork {

  public static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofMinutes(1);
  private static final int PARTY_ID_BYTES = 1;
  private static final Duration RECEIVE_TIMEOUT = Duration.ofMillis(100);
  private static final Logger logger = LoggerFactory.getLogger(AsyncNetwork.class);

  private ServerSocketChannel server;
  private final Map<Integer, SocketChannel> channelMap;
  private final BlockingQueue<byte[]> selfQueue;
  private final NetworkConfiguration conf;
  private boolean alive;
  private ExecutorService communicationService;
  private final Map<Integer, Future<Object>> sendFutures;
  private final Map<Integer, Future<Object>> receiveFutures;
  private final Map<Integer, Sender> senders;
  private final Map<Integer, Receiver> receivers;

  /**
   * Creates a network with the given configuration and a default timeout of
   * {@link #DEFAULT_CONNECTION_TIMEOUT}. Calling the constructor will automatically trigger an
   * attempt to connect to the other parties. If this fails a {@link RuntimeException} is thrown.
   *
   * @param conf the network configuration
   */
  public AsyncNetwork(NetworkConfiguration conf) {
    this(conf, DEFAULT_CONNECTION_TIMEOUT);
  }

  /**
   * Creates a network with the given configuration and a timeout of <code>timeout</code> in
   * milliseconds. Calling the constructor will automatically trigger an attempt to connect to the
   * other parties. If this fails a {@link RuntimeException} is thrown.
   *
   * @param conf The network configuration
   * @param timeout the time to wait until timeout
   */
  public AsyncNetwork(NetworkConfiguration conf, Duration timeout) {
    this.conf = conf;
    int externalParties = conf.noOfParties() - 1;
    this.channelMap = new HashMap<>(externalParties);
    this.sendFutures = new HashMap<>(externalParties);
    this.receiveFutures = new HashMap<>(externalParties);
    this.receivers = new HashMap<>(externalParties);
    this.senders = new HashMap<>(externalParties);
    this.alive = true;
    this.selfQueue = new LinkedBlockingQueue<>();
    if (conf.noOfParties() > 1) {
      connectNetwork(timeout);
      startCommunication();
    }
    logger.info("P{}: successfully connected network", conf.getMyId());
  }

  /**
   * Fully connects the network.
   * <p>
   * Connects a channels to each external party (i.e., parties other than this party).
   * </p>
   *
   * @param conf the configuration defining the network to connect
   * @param timeout duration to wait until timeout
   */
  private void connectNetwork(Duration timeout) {
    ExecutorService es = Executors.newFixedThreadPool(2);
    CompletionService<Map<Integer, SocketChannel>> cs = new ExecutorCompletionService<>(es);
    cs.submit(() -> {
      return connectClient();
    });
    cs.submit(() -> {
      bindServer();
      return connectServer();
    });
    try {
      for (int i = 0; i < 2; i++) {
        Future<Map<Integer, SocketChannel>> f = cs.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (f == null) {
          throw new TimeoutException("Timed out");
        } else {
          this.channelMap.putAll(f.get());
        }
      }
    } catch (InterruptedException e) {
      teardown();
      throw new RuntimeException("Interrupted while connecting network", e);
    } catch (ExecutionException e) {
      teardown();
      throw new RuntimeException("Failed to connect network", e.getCause());
    } catch (TimeoutException e) {
      teardown();
      throw new RuntimeException("Timed out connecting network", e);
    } finally {
      es.shutdownNow();
    }
  }

  /**
   * Makes connections to the opposing parties with higher id's.
   *
   * @throws InterruptedException thrown if interrupted while waiting to do a connection attempt
   */
  private Map<Integer, SocketChannel> connectClient() throws InterruptedException {
    Map<Integer, SocketChannel> channelMap = new HashMap<>(conf.noOfParties() - conf.getMyId());
    for (int i = conf.getMyId() + 1; i <= conf.noOfParties(); i++) {
      Party p = conf.getParty(i);
      SocketAddress addr = new InetSocketAddress(p.getHostname(), p.getPort());
      boolean connectionMade = false;
      int attempts = 0;
      while (!connectionMade) {
        try {
          SocketChannel channel = SocketChannel.open();
          channel.connect(addr);
          channel.configureBlocking(true);
          channelMap.put(i, channel);
          ByteBuffer b = ByteBuffer.allocate(PARTY_ID_BYTES);
          b.put((byte) conf.getMyId());
          b.position(0);
          while (b.hasRemaining()) {
            channel.write(b);
          }
          connectionMade = true;
          channelMap.put(i, channel);
          logger.info("P{}: connected to {}", conf.getMyId(), p);
        } catch (IOException e) {
          Thread.sleep(1 << ++attempts);
        }
      }
    }
    return channelMap;
  }

  /**
   * Binds the server to the port of this party.
   */
  private void bindServer() {
    SocketAddress sock = new InetSocketAddress(conf.getMe().getPort());
    try {
      this.server = ServerSocketChannel.open();
      this.server.bind(sock);
      logger.info("P{}: bound at {}", conf.getMyId(), sock);
    } catch (IOException e) {
      throw new RuntimeException("Failed to bind to " + sock, e);
    }
  }

  /**
   * Listens for connections from the opposing parties with lower id's.
   *
   * @throws IOException thrown if an {@link IOException} occurs while listening.
   */
  private Map<Integer, SocketChannel> connectServer() throws IOException {
    Map<Integer, SocketChannel> channelMap = new HashMap<>(conf.getMyId() - 1);
    for (int i = 1; i < conf.getMyId(); i++) {
      SocketChannel channel = server.accept();
      channel.configureBlocking(true);
      ByteBuffer buf = ByteBuffer.allocate(PARTY_ID_BYTES);
      while (buf.hasRemaining()) {
        channel.read(buf);
      }
      buf.position(0);
      final int id = buf.get();
      channelMap.put(id, channel);
      logger.info("P{}: accepted connection from {}", conf.getMyId(), conf.getParty(id));
      channelMap.put(id, channel);
    }
    server.close();
    return channelMap;
  }

  /**
   * Starts communication threads to handle incoming and outgoing messages.
   */
  private void startCommunication() {
    int externalParties = this.conf.noOfParties() - 1;
    this.communicationService = Executors.newFixedThreadPool(externalParties * 2);
    for (Entry<Integer, SocketChannel> entry : this.channelMap.entrySet()) {
      final int id = entry.getKey();
      SocketChannel channel = entry.getValue();
      Receiver receiver = new Receiver(channel);
      this.receivers.put(id, receiver);
      Future<Object> receiveFuture = this.communicationService.submit(receiver);
      Sender sender = new Sender(channel);
      this.senders.put(id, sender);
      Future<Object> sendFuture = this.communicationService.submit(sender);
      sendFutures.put(id, sendFuture);
      receiveFutures.put(id, receiveFuture);
    }
  }

  /**
   * Implements the receiver receiving a single message and starting a new receiver.
   */
  private static class Receiver implements Callable<Object> {

    private final SocketChannel channel;
    private final BlockingQueue<byte[]> queue;
    private final AtomicBoolean stop;

    public Receiver(SocketChannel channel) {
      this.channel = channel;
      this.queue = new LinkedBlockingQueue<>();
      this.stop = new AtomicBoolean(false);
    }

    /**
     * Signal the receiver to jump out of the receive loop.
     */
    void stop() {
      this.stop.set(true);;
    }

    byte[] pollMessage(Duration timeout) {
      return ExceptionConverter.safe(() -> queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS),
          "Receive interrupted");
    }


    @Override
    public Object call() throws IOException, InterruptedException {
      while (!stop.get()) {
        ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES);
        while (buf.hasRemaining()) {
          channel.read(buf);
        }
        buf.flip();
        int nextMessageSize = buf.getInt();
        buf = ByteBuffer.allocate(nextMessageSize);
        while (buf.remaining() > 0) {
          channel.read(buf);
        }
        queue.add(buf.array());
      }
      return null;
    }

  }

  /**
   * Implements the sender sending messages.
   */
  private static class Sender implements Callable<Object> {

    private final SocketChannel channel;
    private final BlockingQueue<byte[]> queue;
    private final AtomicBoolean flush;
    private final AtomicBoolean stop;

    Sender(SocketChannel channel) {
      this.channel = channel;
      this.queue = new LinkedBlockingQueue<>();
      this.flush = new AtomicBoolean(false);
      this.stop = new AtomicBoolean(false);
    }

    void queueMessage(byte[] msg) {
      queue.add(msg);
    }

    /**
     * Signals the sender that it has been artificially unblocked in order for it to stop.
     */
    void unblock() {
      this.flush.set(true);
      if (queue.isEmpty()) {
        this.stop.set(true);
        queue.add(new byte[] {});
      }
    }

    @Override
    public Object call() throws IOException, InterruptedException {
      while (!queue.isEmpty() || !flush.get()) {
        byte[] data = queue.take();
        if (!stop.get()) {
          ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES + data.length);
          buf.putInt(data.length);
          buf.put(data);
          buf.position(0);
          while (buf.hasRemaining()) {
            channel.write(buf);
          }
        }
      }
      return null;
    }

  }

  @Override
  public void send(int partyId, byte[] data) {
    inRange(partyId);
    if (partyId != conf.getMyId() && sendFutures.get(partyId).isDone()) {
      try {
        sendFutures.get(partyId).get();
      } catch (Exception e) {
        throw new RuntimeException("Sender for P"
            + partyId
            + " threw exception. Unable to send", e);
      }
      throw new RuntimeException("Sender for P" + partyId + " not running. Unable to send");
    }
    if (partyId == conf.getMyId()) {
      this.selfQueue.add(data);
    } else {
      this.senders.get(partyId).queueMessage(data);
    }
  }

  @Override
  public byte[] receive(int partyId) {
    if (partyId == conf.getMyId()) {
      return ExceptionConverter.safe(() -> selfQueue.take(), "Receiving from self iterrupted");
    }
    inRange(partyId);
    byte[] data = null;
    while (data == null) {
      if (partyId != conf.getMyId() && receiveFutures.get(partyId).isDone()) {
        try {
          receiveFutures.get(partyId).get();
        } catch (Exception e) {
          throw new RuntimeException(
              "Receiver for P" + partyId + " threw exception. Unable to receive", e);
        }
        throw new RuntimeException("Receiver for P" + partyId + " not running. Unable to receive");
      }
      data = receivers.get(partyId).pollMessage(RECEIVE_TIMEOUT);
    }
    return data;
  }

  /**
   * Check if a party ID is in the range of known parties.
   *
   * @param partyId an ID for a party
   */
  private void inRange(int partyId) {
    if (!(0 < partyId && partyId < getNoOfParties() + 1)) {
      throw new IllegalArgumentException(
          "Party id " + partyId + " not in range 1 ... " + getNoOfParties());
    }
  }

  private void teardown() {
    if (alive) {
      alive = false;
      if (conf.noOfParties() < 2) {
        logger.info("P{}: Network closed", conf.getMyId());
        return;
      }
      ExceptionConverter.safe(() -> {
        closeThreads();
        closeChannels();
        logger.info("P{}: Network closed", conf.getMyId());
        return null;
      }, "Unable to properly close the network.");
    } else {
      logger.info("P{}: Network already closed", conf.getMyId());
    }
  }

  /**
   * Safely closes the communication channels. Note: this should only be called after the channels
   * are no longer used.
   *
   * @throws IOException if closing the channels triggers an IOException
   */
  private void closeChannels() throws IOException {
    if (this.server != null) {
      this.server.close();
    }
    for (SocketChannel channel : this.channelMap.values()) {
      if (channel != null) {
        channel.close();
      }
    }
  }

  /**
   * Safely closes the threads used for sending/receiving messages. Note: this should be only be
   * called once {@link #alive} is set to false.
   */
  private void closeThreads() {
    receivers.values().forEach(r -> r.stop());
    sendFutures.keySet().stream()
      .filter(i -> !sendFutures.get(i).isDone())
      .forEach(i -> senders.get(i).unblock());
    for (Future<Object> f : sendFutures.values()) {
      try {
        f.get();
      } catch (Exception e) {
        // TODO: Should this cause close to throw an exception?
        logger.warn("P{}: A failed sender detected while closing network", conf.getMyId());
      }
    }
    // Here we just check if any receivers failed
    for (Future<Object> f : receiveFutures.values().stream().filter(f -> f.isDone())
        .collect(Collectors.toList())) {
      try {
        f.get();
      } catch (Exception e) {
        // TODO: Should this cause close to throw an exception?
        logger.warn("P{}: A failed receiver detected while closing network", conf.getMyId());
      }
    }
    if (communicationService != null) {
      communicationService.shutdownNow();
    }
  }

  /**
   * Closes the network down and releases held resources.
   */
  @Override
  public void close() {
    teardown();
  }

  @Override
  public int getNoOfParties() {
    return this.conf.noOfParties();
  }
}
