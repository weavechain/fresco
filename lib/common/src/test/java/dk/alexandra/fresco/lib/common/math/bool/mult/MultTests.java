package dk.alexandra.fresco.lib.common.math.bool.mult;

import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.TestThreadRunner.TestThread;
import dk.alexandra.fresco.framework.TestThreadRunner.TestThreadFactory;
import dk.alexandra.fresco.framework.builder.binary.ProtocolBuilderBinary;
import dk.alexandra.fresco.framework.sce.resources.ResourcePool;
import dk.alexandra.fresco.framework.util.ByteAndBitConverter;
import dk.alexandra.fresco.framework.value.SBool;
import dk.alexandra.fresco.lib.common.math.AdvancedBinary;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.hamcrest.core.Is;
import org.junit.Assert;

public class MultTests {

  public static class TestBinaryMult<ResourcePoolT extends ResourcePool>
      extends TestThreadFactory<ResourcePoolT, ProtocolBuilderBinary> {

    public TestBinaryMult() {}

    @Override
    public TestThread<ResourcePoolT, ProtocolBuilderBinary> next() {
      return new TestThread<ResourcePoolT, ProtocolBuilderBinary>() {

        List<Boolean> rawFirst = Arrays.asList(ByteAndBitConverter.toBoolean("11ff"));
        List<Boolean> rawSecond = Arrays.asList(ByteAndBitConverter.toBoolean("22"));

        final String expected = "0263de";

        @Override
        public void test() throws Exception {

          Application<List<Boolean>, ProtocolBuilderBinary> app = producer -> {

            ProtocolBuilderBinary builder = (ProtocolBuilderBinary) producer;

            return builder.seq(seq -> {
              AdvancedBinary prov = AdvancedBinary.using(seq);
              List<DRes<SBool>> first =
                  rawFirst.stream().map(seq.binary()::known).collect(Collectors.toList());
              List<DRes<SBool>> second =
                  rawSecond.stream().map(seq.binary()::known).collect(Collectors.toList());

              DRes<List<DRes<SBool>>> multiplication = prov.binaryMult(first, second);

              return () -> multiplication.out();
            }).seq((seq, dat) -> {
              List<DRes<Boolean>> out = new ArrayList<DRes<Boolean>>();
              for (DRes<SBool> o : dat) {
                out.add(seq.binary().open(o));
              }
              return () -> out.stream().map(DRes::out).collect(Collectors.toList());
            });
          };
          List<Boolean> outputs = runApplication(app);

          Assert.assertThat(ByteAndBitConverter.toHex(outputs), Is.is(expected));

          Assert.assertThat(outputs.size(), Is.is(rawFirst.size() + rawSecond.size()));

        }
      };
    }
  }
}
