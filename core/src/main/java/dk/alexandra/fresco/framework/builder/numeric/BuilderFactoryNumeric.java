package dk.alexandra.fresco.framework.builder.numeric;

import dk.alexandra.fresco.framework.BuilderFactory;
import dk.alexandra.fresco.framework.builder.ComputationDirectory;
import dk.alexandra.fresco.lib.compare.MiscOIntGenerators;
import dk.alexandra.fresco.lib.field.integer.BasicNumericContext;

/**
 * The core factory to implement when creating a numeric protocol. Every
 * {@link ComputationDirectory} fount in this factory will
 * append the produced protocols to the supplied builder. Implementors must provide
 * a {@link Numeric} - being directory for
 * <ul>
 * <li>simple, numeric operations (+, -, *)</li>
 * <li>Open operations for opening a small subset of values used in the control flow (is a<b)<</li>
 * <li>Factories for producing secret shared values</li>
 * </ul>
 * The other directories have defaults, based on the raw methods, but can be overridden.
 */
public interface BuilderFactoryNumeric extends BuilderFactory<ProtocolBuilderNumeric> {

  int MAGIC_SECURE_NUMBER = 60;

  BasicNumericContext getBasicNumericContext();

  Numeric createNumeric(ProtocolBuilderNumeric builder);

  MiscOIntGenerators getBigIntegerHelper();

  default Comparison createComparison(ProtocolBuilderNumeric builder) {
    return new DefaultComparison(this, builder);
  }

  default AdvancedNumeric createAdvancedNumeric(ProtocolBuilderNumeric builder) {
    return new DefaultAdvancedNumeric(this, builder);
  }
  
  default Collections createCollections(ProtocolBuilderNumeric builder) {
    return new DefaultCollections(this, builder);
  }
  
  default PreprocessedValues createPreprocessedValues(ProtocolBuilderNumeric builder) {
    return new DefaultPreprocessedValues(builder);
  }

  /**
   * Returns a builder which can be helpful while developing a new protocol. Be very careful though,
   * to include this in any production code since the debugging opens values to all parties.
   *
   * @param builder the current builder that will have the protocols inserted
   * @return By default a standard debugger which opens values and prints them.
   */
  default Debug createDebug(ProtocolBuilderNumeric builder) {
    return new DefaultDebug(builder);
  }

  @Override
  default ProtocolBuilderNumeric createSequential() {
    return new ProtocolBuilderNumeric(this, false);
  }

  @Override
  default ProtocolBuilderNumeric createParallel() {
    return new ProtocolBuilderNumeric(this, true);
  }
}
