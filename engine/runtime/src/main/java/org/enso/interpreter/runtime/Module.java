package org.enso.interpreter.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import java.io.File;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.callable.dispatch.CallOptimiserNode;
import org.enso.interpreter.runtime.callable.CallerInfo;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.data.Vector;
import org.enso.interpreter.runtime.scope.LocalScope;
import org.enso.interpreter.runtime.scope.ModuleScope;
import org.enso.interpreter.runtime.type.Types;
import org.enso.pkg.QualifiedName;
import org.enso.polyglot.LanguageInfo;
import org.enso.polyglot.MethodNames;

/** Represents a source module with a known location. */
public class Module {
  private ModuleScope scope;
  private TruffleFile sourceFile;
  private Source literalSource;
  private boolean isParsed = false;
  private final QualifiedName name;

  /**
   * Creates a new module.
   *
   * @param name the qualified name of this module.
   * @param sourceFile the module's source file.
   */
  public Module(QualifiedName name, TruffleFile sourceFile) {
    this.sourceFile = sourceFile;
    this.name = name;
  }

  public Module(QualifiedName name, Source literalSource) {
    this.literalSource = literalSource;
    this.name = name;
  }

  /**
   * Creates a new module.
   *
   * @param name the qualified name of this module.
   */
  public Module(QualifiedName name, ModuleScope scope) {
    this.name = name;
    this.scope = scope;
    this.isParsed = true;
  }

  public void setLiteralSource(Source source) {
    this.literalSource = source;
    this.sourceFile = null;
    this.isParsed = false;
  }

  public void setSourceFile(TruffleFile file) {
    this.literalSource = null;
    this.sourceFile = file;
    this.isParsed = false;
  }

  /**
   * Parses the module sources. The results of this operation are cached.
   *
   * @param context context in which the parsing should take place
   * @return the scope defined by this module
   */
  public ModuleScope getScope(Context context) {
    ensureScopeExists(context);
    if (!isParsed) {
      parse(context);
    }
    return scope;
  }

  private void ensureScopeExists(Context context) {
    if (scope == null) {
      scope = context.createScope(name.module());
      isParsed = false;
    }
  }

  public void parse(Context context) {
    ensureScopeExists(context);
    context.resetScope(scope);
    isParsed = true;
    if (sourceFile != null) {
      context.compiler().run(sourceFile, scope);
    } else if (literalSource != null) {
      context.compiler().run(literalSource, scope);
    }
  }

  public static class PolyglotView {
    private final Module module;
    private final Context context;

    public PolyglotView(Module module, Context context) {
      this.module = module;
      this.context = context;
    }

    @CompilerDirectives.TruffleBoundary
    public Function get_method(AtomConstructor cons, String name) {
      return module.getScope(context).getMethods().get(cons).get(name);
    }

    @CompilerDirectives.TruffleBoundary
    public AtomConstructor get_constructor(String name) {
      return module.getScope(context).getConstructors().get(name);
    }

    @CompilerDirectives.TruffleBoundary
    public Module.PolyglotView patch(String sourceString) {
      ModuleScope scope = module.getScope(context);
      Source source =
          Source.newBuilder(LanguageInfo.ID, sourceString, scope.getAssociatedType().getName())
              .build();
      context.compiler().run(source, scope);
      return this;
    }

    @CompilerDirectives.TruffleBoundary
    public Module.PolyglotView reparse() {
      module.parse(context);
      return this;
    }

    @CompilerDirectives.TruffleBoundary
    public Module.PolyglotView set_source(String source) {
      module.setLiteralSource(
          Source.newBuilder(LanguageInfo.ID, source, module.name.module()).build());
      return this;
    }

    @CompilerDirectives.TruffleBoundary
    public Module.PolyglotView set_source_file(String file) {
      module.setSourceFile(context.getTruffleFile(new File(file)));
      return this;
    }

    @CompilerDirectives.TruffleBoundary
    public AtomConstructor get_associated_constructor() {
      return module.getScope(context).getAssociatedType();
    }

    @CompilerDirectives.TruffleBoundary
    private static Object evalExpression(
        ModuleScope scope, Object[] args, Context context, CallOptimiserNode callOptimiserNode)
        throws ArityException, UnsupportedTypeException {
      String expr = Types.extractArguments(args, String.class);
      AtomConstructor debug = context.getBuiltins().debug();
      Function eval =
          context
              .getBuiltins()
              .getScope()
              .lookupMethodDefinition(debug, Builtins.MethodNames.Debug.EVAL);
      CallerInfo callerInfo = new CallerInfo(null, new LocalScope(), scope);
      Object state = context.getBuiltins().unit().newInstance();
      return callOptimiserNode
          .executeDispatch(eval, callerInfo, state, new Object[] {debug, expr})
          .getValue();
    }
  }
}
