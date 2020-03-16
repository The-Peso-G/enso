package org.enso.languageserver.runtime

import org.enso.languageserver.data.CapabilityRegistration
import org.enso.languageserver.jsonrpc.{HasParams, HasResult, Method}
import org.enso.polyglot.RuntimeApi.ContextId

/**
  * The execution JSON RPC API provided by the language server.
  * See [[https://github.com/luna/enso/blob/master/doc/design/engine/engine-services.md]]
  * for message specifications.
  */
object ExecutionApi {

  case object CreateCtx extends Method("executionContext/create") {
    case class Params(contextId: ContextId)
    case class Result(
      canModify: CapabilityRegistration,
      receivesEvents: CapabilityRegistration
    )
    implicit val hasParams = new HasParams[this.type] {
      type Params = CreateCtx.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = CreateCtx.Result
    }
  }

}
