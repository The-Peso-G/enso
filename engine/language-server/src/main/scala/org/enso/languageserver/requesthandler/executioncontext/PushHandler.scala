package org.enso.languageserver.requesthandler.executioncontext

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import org.enso.jsonrpc.Errors.ServiceError
import org.enso.jsonrpc._
import org.enso.languageserver.requesthandler.RequestTimeout
import org.enso.languageserver.runtime.ExecutionApi._
import org.enso.languageserver.runtime.{
  ContextRegistryProtocol,
  RuntimeFailureMapper
}
import org.enso.languageserver.util.UnhandledLogging

import scala.concurrent.duration.FiniteDuration

/**
  * A request handler for `executionContext/push` commands.
  *
  * @param timeout request timeout
  * @param contextRegistry a reference to the context registry.
  */
class PushHandler(
  timeout: FiniteDuration,
  contextRegistry: ActorRef
) extends Actor
    with ActorLogging
    with UnhandledLogging {

  import context.dispatcher, ContextRegistryProtocol._

  override def receive: Receive = requestStage

  private def requestStage: Receive = {
    case Request(
        ExecutionContextPush,
        id,
        params: ExecutionContextPush.Params
        ) =>
      contextRegistry ! PushContextRequest(
        sender(),
        params.contextId,
        params.stackItem
      )
      val cancellable =
        context.system.scheduler.scheduleOnce(timeout, self, RequestTimeout)
      context.become(responseStage(id, sender(), cancellable))
  }

  private def responseStage(
    id: Id,
    replyTo: ActorRef,
    cancellable: Cancellable
  ): Receive = {
    case RequestTimeout =>
      log.error(s"Request $id timed out")
      replyTo ! ResponseError(Some(id), ServiceError)
      context.stop(self)

    case PushContextResponse(_) =>
      replyTo ! ResponseResult(ExecutionContextPush, id, Unused)
      cancellable.cancel()
      context.stop(self)

    case error: ContextRegistryProtocol.Failure =>
      replyTo ! ResponseError(Some(id), RuntimeFailureMapper.mapFailure(error))
      cancellable.cancel()
      context.stop(self)
  }
}

object PushHandler {

  /**
    * Creates configuration object used to create a [[PushHandler]].
    *
    * @param timeout request timeout
    * @param contextRegistry a reference to the context registry.
    */
  def props(timeout: FiniteDuration, contextRegistry: ActorRef): Props =
    Props(new PushHandler(timeout, contextRegistry))

}
