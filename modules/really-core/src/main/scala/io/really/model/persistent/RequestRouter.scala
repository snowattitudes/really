/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.persistent

import _root_.io.really._
import akka.actor.ActorLogging
import akka.persistence.PersistentView

class RequestRouter(globals: ReallyGlobals, persistId: String) extends PersistentView with ActorLogging {

  import RequestRouter._

  override def persistenceId: String = persistId

  override def viewId: String = "request-router-view"

  private var models: List[R] = List.empty

  def validR(r: R): Boolean =
    models.contains(r.skeleton)

  def receive: Receive = handleEvent orElse handleRequest

  def handleEvent: Receive = {
    case PersistentModelStore.DeletedModels(removedModels) =>
      models = models diff removedModels.map(_.r)

    case PersistentModelStore.AddedModels(newModels) =>
      models ++= newModels.map(_.r)
  }

  def handleRequest: Receive = {
    case req: Request.Create if validR(req.r) =>
      log.debug("Routing Create Request to CollectionActor: {}", req)
      globals.collectionActor forward req.copy(r = req.r / globals.quickSand.nextId())
    case req: Request with RoutableToCollectionActor if validR(req.r) =>
      log.debug("Routing Request to CollectionActor: {}", req)
      globals.collectionActor forward req
    case req: Request with RoutableToCollectionActor =>
      sender ! RNotFound(req.r)
    case req: Request with RoutableToReadHandler if validR(req.r) =>
      log.debug("Routing Request to ReadHandler: {}", req)
      globals.readHandler forward req
    case req: Request with RoutableToReadHandler =>
      sender ! RNotFound(req.r)
    case req: RoutableToSubscriptionManager =>
      log.debug("Routing Request to SubscriptionManager: {}", req)
      globals.subscriptionManager forward req
    case req: Request =>
      sender ! UnsupportedCmd(req.getClass.getName)
  }

}

object RequestRouter {

  trait RequestRouterResponse

  case class RNotFound(r: R) extends RequestRouterResponse

  case class UnsupportedCmd(cmd: String) extends RequestRouterResponse

}
