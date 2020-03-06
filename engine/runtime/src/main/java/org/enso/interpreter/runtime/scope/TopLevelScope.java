package org.enso.interpreter.runtime.scope;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.io.File;
import java.util.Map;
import java.util.Optional;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.Builtins;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.Module;
import org.enso.interpreter.runtime.data.Vector;
import org.enso.interpreter.runtime.type.Types;
import org.enso.pkg.QualifiedName;
import org.enso.polyglot.MethodNames;

/** Represents the top scope of Enso execution, containing all the importable modules. */
public class TopLevelScope {
  private final Builtins builtins;
  private final Map<String, Module> modules;
  private final Scope scope;
  private final Context context;

  /**
   * Creates a new instance of top scope.
   *
   * @param builtins the automatically-imported builtin module.
   * @param modules the initial modules this scope contains.
   */
  public TopLevelScope(Builtins builtins, Map<String, Module> modules, Context context) {
    this.builtins = builtins;
    this.modules = modules;
    this.context = context;
    this.scope =
        Scope.newBuilder("top_scope", context.getEnvironment().asGuestValue(new PolyglotView(this)))
            .build();
  }

  /**
   * Returns a polyglot representation of this scope.
   *
   * @return a polyglot Scope object.
   */
  public Scope getScope() {
    return scope;
  }

  /**
   * Looks up a module by name.
   *
   * @param name the name of the module to look up.
   * @return empty result if the module does not exist or the requested module.
   */
  public Optional<Module> getModule(String name) {
    return Optional.ofNullable(modules.get(name));
  }

  /**
   * Returns the builtins module.
   *
   * @return the builtins module.
   */
  public Builtins getBuiltins() {
    return builtins;
  }

  public static class PolyglotView {
    private final TopLevelScope scope;

    private PolyglotView(TopLevelScope topLevelScope) {
      this.scope = topLevelScope;
    }

    @CompilerDirectives.TruffleBoundary
    public Module.PolyglotView get_module(String moduleName) throws UnknownIdentifierException {
      if (moduleName.equals(Builtins.MODULE_NAME)) {
        return new Module.PolyglotView(scope.builtins.getModule(), scope.context);
      }
      Module module = scope.modules.get(moduleName);
      if (module == null) {
        throw UnknownIdentifierException.create(moduleName);
      }
      return new Module.PolyglotView(module, scope.context);
    }

    @CompilerDirectives.TruffleBoundary
    public Module.PolyglotView create_module(String moduleName) {
      Module module =
          new Module(QualifiedName.simpleName(moduleName), scope.context.createScope(moduleName));
      return new Module.PolyglotView(module, scope.context);
    }

    @CompilerDirectives.TruffleBoundary
    public Module.PolyglotView register_module(String qualifiedName, String path) {
      QualifiedName qualName = QualifiedName.fromString(qualifiedName).get();
      File location = new File(path);
      Module module = new Module(qualName, scope.context.getTruffleFile(location));
      scope.modules.put(qualName.toString(), module);
      return new Module.PolyglotView(module, scope.context);
    }

    @CompilerDirectives.TruffleBoundary
    public Object unregister_module(String name) {
      scope.modules.remove(name);
      return scope.context.getUnit().newInstance();
    }
  }
}
