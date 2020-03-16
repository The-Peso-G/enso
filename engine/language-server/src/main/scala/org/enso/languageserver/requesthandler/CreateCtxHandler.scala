package org.enso.languageserver.requesthandler

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.languageserver.data.Client
import org.enso.languageserver.jsonrpc.Errors.ServiceError
import org.enso.languageserver.jsonrpc._
import org.enso.languageserver.runtime.ExecutionApi.CreateCtx
import org.enso.languageserver.runtime.ExecutionProtocol

import scala.concurrent.duration.FiniteDuration

/**
  * A request handler for `executionContext/create` commands.
  *
  * @param ctxRegistry a router that dispatches text editing requests
  * @param timeout a request timeout
  * @param client an object representing a client connected to the language server
  */
class CreateCtxHandler(
  ctxRegistry: ActorRef,
  timeout: FiniteDuration,
  client: Client
) extends Actor
    with ActorLogging {

  import context.dispatcher

  override def receive: Receive = requestStage

  private def requestStage: Receive = {
    case Request(CreateCtx, id, params: CreateCtx.Params) =>
      ctxRegistry ! ExecutionProtocol.CreateCtx(params.contextId)
      context.system.scheduler.scheduleOnce(timeout, self, RequestTimeout)
      context.become(responseStage(id, sender()))
  }

  private def responseStage(id: Id, replyTo: ActorRef): Receive = {
    case RequestTimeout =>
      log.error(s"Closing file for ${client.id} timed out")
      replyTo ! ResponseError(Some(id), ServiceError)
      context.stop(self)

  }

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)
}

object CreateCtxHandler {

  /**
    * Creates a configuration object used to create a [[CreateCtxHandler]]
    *
    * @param ctxRegistry a router that dispatches text editing requests
    * @param requestTimeout a request timeout
    * @param client an object representing a client connected to the language server
    * @return a configuration object
    */
  def props(
    ctxRegistry: ActorRef,
    requestTimeout: FiniteDuration,
    client: Client
  ): Props = Props(new CreateCtxHandler(ctxRegistry, requestTimeout, client))

}
