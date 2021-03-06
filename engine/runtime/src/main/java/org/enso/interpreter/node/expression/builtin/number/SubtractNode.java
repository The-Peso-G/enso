package org.enso.interpreter.node.expression.builtin.number;

import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.callable.function.FunctionSchema.CallStrategy;

@NodeInfo(shortName = "Number.-", description = "Subtraction on numbers.")
public class SubtractNode extends NumberBinaryOpMethod {
  private SubtractNode(Language language) {
    super(language);
  }

  /**
   * Creates a two-argument function wrapping this node.
   *
   * @param language the current language instance
   * @return a function wrapping this node
   */
  public static Function makeFunction(Language language) {
    return Function.fromBuiltinRootNode(
        new SubtractNode(language),
        CallStrategy.ALWAYS_DIRECT,
        new ArgumentDefinition(0, "this", ArgumentDefinition.ExecutionMode.EXECUTE),
        new ArgumentDefinition(1, "that", ArgumentDefinition.ExecutionMode.EXECUTE));
  }

  /**
   * Subtracts this method's two operands.
   *
   * @param thisArg the left operand (this)
   * @param thatArg the right operand (that)
   * @return the result of subtracting {@code thatArg} from {@code thisArg}
   */
  @Override
  protected long op(long thisArg, long thatArg) {
    return thisArg - thatArg;
  }

  /**
   * Returns a language-specific name for this node.
   *
   * @return the name of this node
   */
  @Override
  public String getName() {
    return "Number.-";
  }
}
