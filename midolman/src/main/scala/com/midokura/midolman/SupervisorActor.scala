/*
 * Copyright (c) 2012 Midokura Pte.Ltd.
 */
package com.midokura.midolman

import akka.actor.{Props, SupervisorStrategy, Actor, ActorLogging}
import com.google.inject.Inject
import logging.ActorLogWithoutPath

/**
 * This actor is responsible for the supervision strategy of all the
 * top-level (Referenceable derived, well-known actors) actors in
 * midolman. All such actors are launched as children of the SupervisorActor
 */
object SupervisorActor extends Referenceable {
    override val Name = supervisorName

    override val Prefix: String = "/user"

    case class StartChild(props: Props, name: String)
}

class SupervisorActor extends Actor with ActorLogWithoutPath {

    @Inject
    override val supervisorStrategy: SupervisorStrategy = null

    override def postStop() {
        log.info("Supervisor actor is shutting down")
    }

    def receive = {
        case SupervisorActor.StartChild(props, name) =>
            sender ! (try {
                    context.actorOf(props, name)
                } catch {
                    case e: Exception => e
                })

        case _ => log.info("RECEIVED UNKNOWN MESSAGE")
    }
}
